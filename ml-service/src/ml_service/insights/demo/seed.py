"""Prepara o banco da demonstração na primeira inicialização do compose da demo (`demo/compose.yaml`).

1. Espera a API responder (as migrações do Flyway criam o esquema na partida dela).
2. Se o banco já foi preparado (tabela `public.demo_seed`), não faz nada.
3. Gera o histórico de 12 meses em `DEMO_DATA_DIR` (volume), terminando **ontem**, se ele ainda não existe ou
   terminou antes de anteontem: os insights olham para "os últimos 7/30 dias", então um histórico velho não
   mostraria nada. Não termina hoje porque as viagens do resto do dia teriam `end_time` no futuro, e o motor as
   recalcularia a cada execução (métricas calculadas antes do encerramento).
4. Carrega o histórico (`load.load`), calcula as métricas das 2 últimas semanas e avalia os insights.
5. Grava a marca em `public.demo_seed`. Se uma execução anterior parou no meio, a próxima recarrega do zero.

Banco: as mesmas variáveis do motor de insights (DATABASE_URL no formato JDBC, DATABASE_USERNAME, DATABASE_PASSWORD).
API: DEMO_API_URL (padrão http://api:8080/api/v1), usuário admin com ADMIN_PASSWORD.

Uso: python -m ml_service.insights.demo.seed   (depois, no compose: python -m ml_service.insights.run)
"""

from __future__ import annotations

import json
import logging
import os
import shutil
import time
import urllib.error
from datetime import date, timedelta
from pathlib import Path

import pandas as pd
import psycopg

from ml_service.insights import db
from ml_service.insights.demo.build import DEMO_VEHICLE_MODEL, build
from ml_service.insights.demo.load import Api, load
from ml_service.insights.demo.scenario import DemoConfig
from ml_service.insights.run import ModelResolver, compute_pending_trips, evaluate_insights

log = logging.getLogger("ml_service.insights.demo")

CONFIG = Path("configs/demo-history.yaml")


def wait_for_api(url: str, password: str, timeout_s: float = 600) -> Api:
    deadline = time.monotonic() + timeout_s
    while True:
        try:
            return Api(url, "admin", password)
        except (urllib.error.URLError, ConnectionError, TimeoutError) as e:
            # HTTPError 401 é URLError também, mas não adianta esperar: senha errada
            if isinstance(e, urllib.error.HTTPError) and e.code in (400, 401, 403):
                raise SystemExit(f"login do admin recusado pela API ({e.code}): confira ADMIN_PASSWORD")
            if time.monotonic() > deadline:
                raise SystemExit(f"API indisponível em {url}: {e}")
            log.info("aguardando a API (%s)", e)
            time.sleep(5)


def already_seeded(conn: psycopg.Connection) -> dict | None:
    conn.execute("CREATE TABLE IF NOT EXISTS public.demo_seed (loaded_at timestamptz NOT NULL DEFAULT now(), "
                 "history_end date NOT NULL, counts jsonb)")
    conn.commit()
    return conn.execute("SELECT loaded_at, history_end FROM public.demo_seed ORDER BY loaded_at DESC LIMIT 1").fetchone()


def ensure_history(out: Path, today: date, jobs: int) -> dict:
    end = today - timedelta(days=1)
    manifest_file = out / "manifest.json"
    if manifest_file.exists():
        manifest = json.loads(manifest_file.read_text())
        if date.fromisoformat(manifest["end"]) >= end - timedelta(days=1):
            log.info("histórico já gerado (%s a %s), reaproveitando", manifest["start"], manifest["end"])
            return manifest
        log.info("histórico gerado termina em %s: gerando de novo até %s", manifest["end"], end)
    for child in out.iterdir() if out.exists() else []:
        shutil.rmtree(child) if child.is_dir() else child.unlink()
    log.info("gerando o histórico de demonstração (12 meses, %d processos; leva alguns minutos)", jobs)
    t0 = time.time()
    manifest = build(CONFIG, out, end, jobs)
    log.info("histórico gerado em %.0f s: %d viagens, %d alertas", time.time() - t0, manifest["trips"],
             manifest["events"])
    return manifest


def default_jobs() -> int:
    return max(1, min(8, (os.cpu_count() or 2) - 1))


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
    out = Path(os.environ.get("DEMO_DATA_DIR", "/data/demo"))
    api_url = os.environ.get("DEMO_API_URL", "http://api:8080/api/v1")
    jobs = int(os.environ.get("DEMO_BUILD_JOBS") or default_jobs())

    api = wait_for_api(api_url, os.environ["ADMIN_PASSWORD"])
    with psycopg.connect(db.conninfo(), row_factory=psycopg.rows.dict_row) as conn:
        if seeded := already_seeded(conn):
            log.info("banco da demo já preparado em %s (histórico até %s)", seeded["loaded_at"],
                     seeded["history_end"])
            return
        manifest = ensure_history(out, date.today(), jobs)
        cfg = DemoConfig.load(CONFIG, pd.Timestamp(manifest["end"]).date())
        partial = conn.execute("SELECT 1 FROM fleet.trips LIMIT 1").fetchone() is not None
        if partial:
            log.info("carga anterior incompleta: recarregando")
        counts = load(out, cfg, api, conn, do_reset=partial)
        log.info("carregado: %s", counts)
        resolver = ModelResolver(Path("models"), DEMO_VEHICLE_MODEL)
        computed = 0
        while n := compute_pending_trips(conn, resolver, batch=200, window_s=60):
            computed += n
            log.info("métricas das viagens recentes: %d", computed)
            if computed > counts["trips"]:   # proteção: nunca deveria recalcular a mesma viagem
                log.warning("mais métricas calculadas que viagens carregadas: interrompendo")
                break
        insights = evaluate_insights(conn, lookback_days=100)
        log.info("insights: %s", insights)
        conn.execute("INSERT INTO public.demo_seed (history_end, counts) VALUES (%s, %s)",
                     (manifest["end"], json.dumps({**counts, "live_trips": computed, "insights": insights},
                                                  default=str)))
        conn.commit()
    log.info("demo pronta")


if __name__ == "__main__":
    main()
