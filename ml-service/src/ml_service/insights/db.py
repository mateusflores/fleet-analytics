"""Acesso ao banco da plataforma (PostgreSQL/TimescaleDB) para métricas de viagem e insights.

Conexão pelas mesmas variáveis da API: `DATABASE_URL` (aceita o formato JDBC `jdbc:postgresql://host:porta/banco`),
`DATABASE_USERNAME` e `DATABASE_PASSWORD`. `INSIGHTS_DATABASE_URL` (conninfo libpq) tem precedência, se definida.
"""

from __future__ import annotations

import json
import os
from datetime import datetime

import pandas as pd
import psycopg
from psycopg.rows import dict_row
from psycopg.types.json import Jsonb

from ml_service.insights.planner import Action
from ml_service.insights.policy import FleetPolicy

TRIP_METRIC_COLUMNS = [
    "engine_on_s", "driving_s", "idle_s", "excessive_idle_s", "distance_km", "fuel_used_l", "fuel_expected_l",
    "idle_fuel_l", "avg_speed_kmh", "max_speed_kmh", "avg_load_pct", "speeding_s", "harsh_accel_count",
    "harsh_brake_count", "rpm_over_s", "events_warning", "events_critical", "events_ml", "health", "policy",
]


def conninfo() -> str:
    if os.environ.get("INSIGHTS_DATABASE_URL"):
        return os.environ["INSIGHTS_DATABASE_URL"]
    url = os.environ.get("DATABASE_URL", "postgresql://localhost:5432/postgres").removeprefix("jdbc:")
    extra = {k: os.environ[v] for k, v in (("user", "DATABASE_USERNAME"), ("password", "DATABASE_PASSWORD"))
             if os.environ.get(v)}
    return psycopg.conninfo.make_conninfo(url, **extra)


def connect() -> psycopg.Connection:
    return psycopg.connect(conninfo(), row_factory=dict_row, autocommit=False)


def _frame(conn: psycopg.Connection, sql: str, params: tuple | dict = ()) -> pd.DataFrame:
    with conn.cursor() as cur:
        cur.execute(sql, params)
        rows = cur.fetchall()
        cols = [d.name for d in cur.description]
    return pd.DataFrame(rows, columns=cols)


# ---------------------------------------------------------------------------------------------------------------
# política

def load_policy(conn: psycopg.Connection) -> FleetPolicy:
    p = _frame(conn, "SELECT * FROM fleet.fleet_policy WHERE id = 1")
    rpm = _frame(conn, "SELECT vehicle_type, economy_rpm_max FROM fleet.vehicle_type_policy")
    if p.empty:
        return FleetPolicy(economy_rpm_max=dict(zip(rpm["vehicle_type"], rpm["economy_rpm_max"])))
    r = p.iloc[0]
    return FleetPolicy(speed_limit_kmh=float(r["speed_limit_kmh"]), harsh_accel_ms2=float(r["harsh_accel_ms2"]),
                       harsh_brake_ms2=float(r["harsh_brake_ms2"]), idle_threshold_s=int(r["idle_threshold_s"]),
                       fuel_price_per_liter=float(r["fuel_price_per_liter"]),
                       economy_rpm_max={k: int(v) for k, v in zip(rpm["vehicle_type"], rpm["economy_rpm_max"])})


# ---------------------------------------------------------------------------------------------------------------
# métricas por viagem

def trips_to_compute(conn: psycopg.Connection, limit: int = 200) -> pd.DataFrame:
    """Viagens encerradas sem métricas (ou com métricas calculadas antes do encerramento)."""
    return _frame(conn, """
        SELECT t.id AS trip_id, t.vehicle_id, t.driver_id, t.start_time, t.end_time, v.vehicle_type
        FROM fleet.trips t
        JOIN fleet.vehicles v ON v.id = t.vehicle_id
        LEFT JOIN telemetry.trip_metrics m ON m.trip_id = t.id
        WHERE t.end_time IS NOT NULL
          AND (m.trip_id IS NULL OR (m.source = 'LIVE' AND m.computed_at < t.end_time))
        ORDER BY t.end_time
        LIMIT %s""", (limit,))


def load_trip_telemetry(conn: psycopg.Connection, trip_id, start: datetime, end: datetime) -> pd.DataFrame:
    tel = _frame(conn, """
        SELECT time, engine_rpm AS rpm, fuel_level_pct, engine_temp_c AS coolant_c, odometer_km,
               battery_voltage AS battery_v, speed_kmh, load_pct, engine_torque_pct, pedal_pct, gear, oil_kpa,
               fuel_rate_lph, boost_kpa, intake_c
        FROM telemetry.vehicle_telemetry
        WHERE trip_id = %s AND time >= %s AND time <= %s
        ORDER BY time""", (trip_id, start, end))
    if tel.empty or tel["speed_kmh"].notna().any():
        return tel
    # dispositivos que só mandam a velocidade na posição: completa pela posição do mesmo segundo
    pos = _frame(conn, """
        SELECT date_trunc('second', time) AS sec, avg(speed_kmh) AS speed_kmh
        FROM telemetry.vehicle_positions
        WHERE trip_id = %s AND time >= %s AND time <= %s
        GROUP BY 1""", (trip_id, start, end))
    if pos.empty:
        return tel
    tel["sec"] = pd.to_datetime(tel["time"], utc=True).dt.floor("s")
    pos["sec"] = pd.to_datetime(pos["sec"], utc=True)
    return tel.drop(columns="speed_kmh").merge(pos, on="sec", how="left").drop(columns="sec")


def load_trip_events(conn: psycopg.Connection, trip_id) -> pd.DataFrame:
    return _frame(conn, "SELECT event_type, severity FROM telemetry.events WHERE trip_id = %s", (trip_id,))


def upsert_trip_metrics(conn: psycopg.Connection, trip: dict, metrics: dict, source: str = "LIVE") -> None:
    cols = ["trip_id", "vehicle_id", "driver_id", "start_time", "end_time", *TRIP_METRIC_COLUMNS, "source",
            "computed_at"]
    values = {**{k: trip[k] for k in ("trip_id", "vehicle_id", "driver_id", "start_time", "end_time")}, **metrics,
              "source": source, "computed_at": datetime.now().astimezone()}
    values["health"] = Jsonb(_jsonable(values["health"]))
    values["policy"] = Jsonb(_jsonable(values["policy"]))
    placeholders = ", ".join(f"%({c})s" for c in cols)
    updates = ", ".join(f"{c} = EXCLUDED.{c}" for c in cols if c != "trip_id")
    with conn.cursor() as cur:
        cur.execute(f"INSERT INTO telemetry.trip_metrics ({', '.join(cols)}) VALUES ({placeholders}) "
                    f"ON CONFLICT (trip_id) DO UPDATE SET {updates}", values)


def _jsonable(obj):
    return json.loads(json.dumps(obj, default=float))


# ---------------------------------------------------------------------------------------------------------------
# insumos das regras

def load_trips_for_rules(conn: psycopg.Connection, since: datetime) -> pd.DataFrame:
    df = _frame(conn, """
        SELECT m.*, v.vehicle_type,
               COALESCE(NULLIF(v.nickname, ''), v.license_plate, 'Veículo') AS vehicle_name,
               NULLIF(TRIM(CONCAT_WS(' ', u.first_name, u.last_name)), '') AS driver_name
        FROM telemetry.trip_metrics m
        JOIN fleet.vehicles v ON v.id = m.vehicle_id
        LEFT JOIN fleet.users u ON u.id = m.driver_id
        WHERE m.start_time >= %s""", (since,))
    if not df.empty:
        df["start_time"] = pd.to_datetime(df["start_time"], utc=True)
        for c in ("vehicle_id", "driver_id", "trip_id"):
            df[c] = df[c].map(lambda x: str(x) if x is not None else None)
        df["driver_name"] = df["driver_name"].fillna("Motorista")
        num = [c for c in TRIP_METRIC_COLUMNS if c not in ("health", "policy")]
        df[num] = df[num].apply(pd.to_numeric, errors="coerce")
    return df


def load_events(conn: psycopg.Connection, since: datetime) -> pd.DataFrame:
    df = _frame(conn, "SELECT time, vehicle_id, event_type, severity FROM telemetry.events WHERE time >= %s",
                (since,))
    if not df.empty:
        df["time"] = pd.to_datetime(df["time"], utc=True)
        df["vehicle_id"] = df["vehicle_id"].astype(str)
    return df


def load_open_insights(conn: psycopg.Connection) -> pd.DataFrame:
    df = _frame(conn, "SELECT id, dedup_key, status, severity, updated_at FROM telemetry.insights "
                      "WHERE status IN ('ACTIVE', 'ACKNOWLEDGED')")
    if not df.empty:
        df["updated_at"] = pd.to_datetime(df["updated_at"], utc=True)
    return df


def load_dismissed(conn: psycopg.Connection, since: datetime) -> pd.DataFrame:
    df = _frame(conn, "SELECT dedup_key, severity, updated_at FROM telemetry.insights "
                      "WHERE status = 'DISMISSED' AND updated_at >= %s", (since,))
    if not df.empty:
        df["updated_at"] = pd.to_datetime(df["updated_at"], utc=True)
    return df


def apply_actions(conn: psycopg.Connection, actions: list[Action]) -> dict:
    counts = {"insert": 0, "update": 0, "resolve": 0}
    with conn.cursor() as cur:
        for a in actions:
            counts[a.kind] += 1
            if a.kind == "resolve":
                cur.execute("UPDATE telemetry.insights SET status = 'RESOLVED', updated_at = now() WHERE id = %s",
                            (a.insight_id,))
                continue
            c = a.candidate
            fields = {"scope": c.scope, "subject_id": c.subject_id, "category": c.category, "severity": c.severity,
                      "title": c.title, "description": c.description, "period_start": c.period_start,
                      "period_end": c.period_end, "evidence": Jsonb(_jsonable(c.evidence)), "dedup_key": c.dedup_key}
            if a.kind == "insert":
                cols = ", ".join(fields)
                cur.execute(f"INSERT INTO telemetry.insights ({cols}) VALUES "
                            f"({', '.join(f'%({k})s' for k in fields)})", fields)
            else:
                sets = ", ".join(f"{k} = %({k})s" for k in fields if k != "dedup_key")
                status = ", status = 'ACTIVE', acknowledged_by = NULL, acknowledged_at = NULL" if a.reopen else ""
                cur.execute(f"UPDATE telemetry.insights SET {sets}, updated_at = now(){status} WHERE id = %(id)s",
                            {**fields, "id": a.insight_id})
    return counts
