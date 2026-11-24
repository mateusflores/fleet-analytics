"""Gera o histórico de demonstração: agenda → simulador (Java) → métricas por viagem, mês a mês.

Saída em `--out` (padrão `data/demo/`):
- `<AAAA-MM>/` — agenda, viagens, alertas e telemetria do mês (a telemetria 1 Hz só é mantida nos meses que tocam
  os últimos `live_days` dias, que vão a 1 Hz para o banco da demo);
- `trip_metrics.parquet`, `events.parquet` — o ano inteiro, usados pela avaliação e pela carga;
- `ground_truth.json`, `manifest.json`.

Uso:
  python -m ml_service.insights.demo.build --config configs/demo-history.yaml [--end-date AAAA-MM-DD] [--jobs 3]
"""

from __future__ import annotations

import argparse
import json
import logging
import os
import subprocess
import time
from concurrent.futures import ProcessPoolExecutor
from datetime import date
from pathlib import Path

import pandas as pd

from ml_service.insights.demo.backfill import backfill_month, read_events
from ml_service.insights.demo.scenario import DemoConfig, ground_truth, schedule
from ml_service.insights.policy import FleetPolicy
from ml_service.insights.run import ModelResolver

log = logging.getLogger("ml_service.insights.demo")

SIMULATOR_DIR = Path(__file__).resolve().parents[5] / "desktop-simulator"
GENERATOR = "com.unesp.desktopsimulator.tools.HistoryGenerator"
# Mesmo mapeamento do compose: o caminhão médio do simulador, sem assumir motor aquecido.
DEMO_VEHICLE_MODEL = {"default": {"company": "empresa-b", "group": "caminhao-medio-sim"}}


def simulator_classpath(out: Path) -> str:
    """Compila o desktop-simulator e devolve o classpath (classes + dependências)."""
    cp_file = (out / "classpath.txt").resolve()
    subprocess.run(["./mvnw", "-q", "compile", "dependency:build-classpath", f"-Dmdep.outputFile={cp_file}"],
                   cwd=SIMULATOR_DIR, check=True)
    return f"{SIMULATOR_DIR / 'target' / 'classes'}:{cp_file.read_text().strip()}"


def policy() -> FleetPolicy:
    """Política inicial da frota (mesmos valores semeados pela V13)."""
    return FleetPolicy(economy_rpm_max={"RIGID_TRUCK": 2000, "ARTICULATED_VEHICLE": 1600,
                                        "PASSAGER_VEHICLE": 2200, "AGRICULTURAL_VEHICLE": 2000})


def build_month(month: str, rows: pd.DataFrame, out: Path, classpath: str, vehicle_type: str,
                keep_telemetry: bool, models_dir: str = "models") -> dict:
    t0 = time.time()
    month_dir = out / month
    month_dir.mkdir(parents=True, exist_ok=True)
    rows.to_csv(month_dir / "schedule.csv", index=False)
    subprocess.run(["java", "-XX:ActiveProcessorCount=1", "-cp", classpath, GENERATOR, "--schedule", str(month_dir / "schedule.csv"),
                    "--out", str(month_dir)], check=True, stdout=subprocess.DEVNULL)
    t_sim = time.time() - t0
    detector, warm = ModelResolver(Path(models_dir), DEMO_VEHICLE_MODEL).get("any")
    metrics = backfill_month(month_dir, policy(), vehicle_type, detector, assume_warm=warm)
    metrics["health"] = metrics["health"].map(json.dumps)
    metrics["policy"] = metrics["policy"].map(json.dumps)
    metrics.to_parquet(month_dir / "trip_metrics.parquet", index=False)
    read_events(month_dir).to_parquet(month_dir / "events.parquet", index=False)
    if not keep_telemetry:
        (month_dir / "telemetry.csv.gz").unlink()
    return {"month": month, "vehicle_days": len(rows), "trips": len(metrics), "sim_s": round(t_sim, 1),
            "total_s": round(time.time() - t0, 1)}


def build(config: Path, out: Path, end: date | None = None, jobs: int = 3, months: int | None = None) -> dict:
    cfg = DemoConfig.load(config, end)
    out.mkdir(parents=True, exist_ok=True)
    plan = schedule(cfg)
    plan["month"] = plan["date"].str[:7]
    month_keys = sorted(plan["month"].unique())
    if months:
        month_keys = month_keys[-months:]
    classpath = simulator_classpath(out)
    # um processo por mês: cada um com uma thread (sem isso, LightGBM/BLAS de cada processo usam todos os núcleos)
    for var in ("OMP_NUM_THREADS", "OPENBLAS_NUM_THREADS", "MKL_NUM_THREADS", "LOKY_MAX_CPU_COUNT"):
        os.environ.setdefault(var, "1")
    live_month = cfg.live_start.isoformat()[:7]
    vehicle_type = cfg.raw.get("vehicle_type", "RIGID_TRUCK")
    results = []
    with ProcessPoolExecutor(max_workers=jobs) as pool:
        futures = [pool.submit(build_month, m, plan[plan["month"] == m].drop(columns="month"), out, classpath,
                               vehicle_type, m >= live_month) for m in month_keys]
        for f in futures:
            results.append(f.result())
            log.info("mês %s", results[-1])
    metrics = pd.concat([pd.read_parquet(out / m / "trip_metrics.parquet") for m in month_keys], ignore_index=True)
    events = pd.concat([pd.read_parquet(out / m / "events.parquet") for m in month_keys], ignore_index=True)
    metrics.to_parquet(out / "trip_metrics.parquet", index=False)
    events.to_parquet(out / "events.parquet", index=False)
    (out / "ground_truth.json").write_text(json.dumps(ground_truth(cfg), indent=2, ensure_ascii=False))
    manifest = {"config": str(config), "start": cfg.start.isoformat(), "end": cfg.end.isoformat(),
                "live_start": cfg.live_start.isoformat(), "months": month_keys, "trips": len(metrics),
                "events": len(events), "per_month": results}
    (out / "manifest.json").write_text(json.dumps(manifest, indent=2))
    return manifest


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--config", default="configs/demo-history.yaml")
    ap.add_argument("--out", default="data/demo")
    ap.add_argument("--end-date", type=date.fromisoformat, help="último dia do histórico (padrão: hoje)")
    ap.add_argument("--jobs", type=int, default=3, help="meses gerados em paralelo")
    ap.add_argument("--months", type=int, help="só os últimos N meses (calibração)")
    a = ap.parse_args()
    m = build(Path(a.config), Path(a.out), a.end_date, a.jobs, a.months)
    print(f"{m['trips']} viagens, {m['events']} alertas, {m['start']} a {m['end']} → {a.out}")


if __name__ == "__main__":
    main()
