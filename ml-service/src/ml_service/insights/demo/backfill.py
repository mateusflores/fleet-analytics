"""Métricas por viagem de um mês gerado pelo `HistoryGenerator`, com o mesmo cálculo do caminho ao vivo.

Lê `trips.csv`, `events.csv` e `telemetry.csv.gz` de um diretório de mês e devolve um DataFrame no formato de
`telemetry.trip_metrics` (+ chaves de veículo e motorista), pronto para as regras ou para gravar no banco.
"""

from __future__ import annotations

from pathlib import Path

import pandas as pd

from ml_service.insights.db import TRIP_METRIC_COLUMNS
from ml_service.insights.policy import FleetPolicy
from ml_service.insights.trip_metrics import compute_trip_metrics


def read_trips(month_dir: Path) -> pd.DataFrame:
    trips = pd.read_csv(month_dir / "trips.csv")
    trips["start_time"] = pd.to_datetime(trips["start_epoch"], unit="s", utc=True)
    trips["end_time"] = pd.to_datetime(trips["end_epoch"], unit="s", utc=True)
    return trips


def read_events(month_dir: Path) -> pd.DataFrame:
    ev = pd.read_csv(month_dir / "events.csv")
    ev["time"] = pd.to_datetime(ev["epoch"], unit="s", utc=True)
    return ev


def read_telemetry(month_dir: Path) -> pd.DataFrame:
    tel = pd.read_csv(month_dir / "telemetry.csv.gz")
    tel["time"] = pd.to_datetime(tel["timestamp"], unit="s", utc=True)
    return tel


def backfill_month(month_dir: Path, policy: FleetPolicy, vehicle_type: str, detector,
                   window_s: int = 60, assume_warm: bool = False) -> pd.DataFrame:
    trips, events, tel = read_trips(month_dir), read_events(month_dir), read_telemetry(month_dir)
    ev_by_trip = {k: g for k, g in events.groupby("trip_id")}
    empty_ev = events.iloc[0:0]
    rows = []
    for trip_id, raw in tel.groupby("tripId", sort=False):
        m = compute_trip_metrics(raw.drop(columns=["timestamp", "vehicleId", "tripId"]), policy, vehicle_type,
                                 ev_by_trip.get(trip_id, empty_ev), detector, window_s, assume_warm=assume_warm)
        rows.append({"trip_id": trip_id, **m})
    metrics = pd.DataFrame(rows, columns=["trip_id", *TRIP_METRIC_COLUMNS])
    out = trips.merge(metrics, on="trip_id", how="left")
    return out.rename(columns={"vehicle": "vehicle_key", "driver": "driver_key"})
