"""Executor do motor de insights: calcula as métricas das viagens encerradas e reavalia os insights.

Uso:
    python -m ml_service.insights.run --config configs/insights.yaml --once
    python -m ml_service.insights.run --config configs/insights.yaml            # contínuo (interval_s)
"""

from __future__ import annotations

import argparse
import logging
import time
from pathlib import Path

import pandas as pd
import yaml

from ml_service.inference.engine import load_model
from ml_service.insights import db
from ml_service.insights.planner import DISMISS_SNOOZE_DAYS, plan
from ml_service.insights.rules import evaluate
from ml_service.insights.trip_metrics import compute_trip_metrics

log = logging.getLogger("ml_service.insights")


class ModelResolver:
    """Detector (modelo de valor esperado) do veículo, pelo mapeamento do serviço de inferência."""

    def __init__(self, models_dir: Path, vehicles: dict):
        self.models_dir, self.vehicles, self.cache = models_dir, vehicles, {}

    def get(self, vehicle_id: str):
        info = self.vehicles.get(str(vehicle_id)) or self.vehicles.get("default")
        if not info:
            return None, False
        company = info["company"]
        if company not in self.cache:
            try:
                self.cache[company] = load_model(self.models_dir, company)
            except FileNotFoundError:
                log.warning("modelo da empresa %s não encontrado; consumo esperado indisponível", company)
                self.cache[company] = None
        model = self.cache[company]
        if model is None:
            return None, False
        group = info.get("group") or next(iter(model["detectors"]))
        return model["detectors"].get(group), bool(info.get("assume_warm"))


def compute_pending_trips(conn, resolver: ModelResolver, batch: int, window_s: int) -> int:
    policy = db.load_policy(conn)
    pending = db.trips_to_compute(conn, batch)
    for _, trip in pending.iterrows():
        raw = db.load_trip_telemetry(conn, trip["trip_id"], trip["start_time"], trip["end_time"])
        detector, warm = resolver.get(trip["vehicle_id"])
        metrics = compute_trip_metrics(raw, policy, trip["vehicle_type"], db.load_trip_events(conn, trip["trip_id"]),
                                       detector, window_s, assume_warm=warm)
        db.upsert_trip_metrics(conn, trip.to_dict(), metrics)
        conn.commit()
    return len(pending)


def evaluate_insights(conn, lookback_days: int, now: pd.Timestamp | None = None) -> dict:
    now = now or pd.Timestamp.now(tz="UTC")
    since = (now - pd.Timedelta(days=lookback_days)).to_pydatetime()
    trips = db.load_trips_for_rules(conn, since)
    events = db.load_events(conn, since)
    candidates = evaluate(trips, events, now, db.load_policy(conn))
    actions = plan(candidates, db.load_open_insights(conn),
                   db.load_dismissed(conn, (now - pd.Timedelta(days=DISMISS_SNOOZE_DAYS)).to_pydatetime()), now)
    counts = db.apply_actions(conn, actions)
    conn.commit()
    return {"candidates": len(candidates), **counts}


def run_once(cfg: dict, resolver: ModelResolver) -> dict:
    with db.connect() as conn:
        trips = compute_pending_trips(conn, resolver, cfg.get("trip_batch", 200), cfg.get("window_s", 60))
        result = evaluate_insights(conn, cfg.get("lookback_days", 100))
    return {"trips_computed": trips, **result}


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--config", default="configs/insights.yaml")
    ap.add_argument("--once", action="store_true")
    args = ap.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
    cfg = yaml.safe_load(Path(args.config).read_text())
    inference = yaml.safe_load(Path(cfg["inference_config"]).read_text())
    resolver = ModelResolver(Path(cfg.get("models_dir", "models")), inference.get("vehicles", {}))
    while True:
        try:
            log.info("execução: %s", run_once(cfg, resolver))
        except Exception:
            log.exception("falha na execução do motor de insights")
            if args.once:
                raise
        if args.once:
            break
        time.sleep(cfg.get("interval_s", 3600))


if __name__ == "__main__":
    main()
