"""Integração do motor de insights com o banco real (PostgreSQL + TimescaleDB, migrações Flyway da API).

Roda só com `ML_TEST_DATABASE_URL` (conninfo de um servidor descartável com permissão para criar bancos), ex.:
  docker run -d --rm --name ml-test-db -e POSTGRES_PASSWORD=t -p 25433:5432 timescale/timescaledb:latest-pg18
  ML_TEST_DATABASE_URL=postgresql://postgres:t@localhost:25433/postgres .venv/bin/pytest tests/test_insights_db.py
Cria um banco novo por sessão, aplica V1…V13 em ordem e o apaga no fim.
"""

from __future__ import annotations

import os
import re
import uuid
from pathlib import Path

import numpy as np
import pandas as pd
import psycopg
import pytest
from psycopg.rows import dict_row

from ml_service.insights import db
from ml_service.insights.planner import Action, plan
from ml_service.insights.rules import Candidate
from ml_service.insights.run import ModelResolver, compute_pending_trips, evaluate_insights, run_once

ADMIN_URL = os.environ.get("ML_TEST_DATABASE_URL")
pytestmark = pytest.mark.skipif(not ADMIN_URL, reason="defina ML_TEST_DATABASE_URL para os testes com banco")

MIGRATIONS = Path(__file__).resolve().parents[2] / "fleet-analytics-api" / "src" / "main" / "resources" / "db" / "migration"
NOW = pd.Timestamp.now(tz="UTC").floor("s")


def _version(p: Path) -> int:
    return int(re.match(r"V(\d+)__", p.name).group(1))


@pytest.fixture(scope="module")
def dburl():
    name = f"ml_it_{uuid.uuid4().hex[:8]}"
    with psycopg.connect(ADMIN_URL, autocommit=True) as admin:
        admin.execute(f"CREATE DATABASE {name}")
    url = psycopg.conninfo.make_conninfo(ADMIN_URL, dbname=name)
    with psycopg.connect(url, autocommit=True) as conn:
        for f in sorted(MIGRATIONS.glob("V*__*.sql"), key=_version):
            conn.execute(f.read_text())
    yield url
    with psycopg.connect(ADMIN_URL, autocommit=True) as admin:
        admin.execute(f"DROP DATABASE {name} WITH (FORCE)")


@pytest.fixture()
def conn(dburl, monkeypatch):
    monkeypatch.setenv("INSIGHTS_DATABASE_URL", dburl)
    with psycopg.connect(dburl, row_factory=dict_row) as c:
        for t in ("telemetry.insights", "telemetry.trip_metrics", "telemetry.vehicle_telemetry",
                  "telemetry.vehicle_positions", "telemetry.events", "fleet.trips", "fleet.vehicles", "fleet.users"):
            c.execute(f"DELETE FROM {t}")
        c.commit()
        yield c


def _vehicle(conn, name="Caminhão IT") -> str:
    vid = str(uuid.uuid4())
    conn.execute("INSERT INTO fleet.vehicles (id, serial_number, license_plate, nickname, vehicle_type, is_active) "
                 "VALUES (%s, %s, %s, %s, 'RIGID_TRUCK', true)", (vid, vid[:8], vid[:7], name))
    return vid


def _driver(conn, first="Ana") -> str:
    did = str(uuid.uuid4())
    conn.execute("INSERT INTO fleet.users (id, username, email, password, is_enabled, first_name, last_name) "
                 "VALUES (%s, %s, %s, 'x', true, %s, 'Teste')", (did, did[:12], did[:12] + "@t", first))
    return did


def _trip_frame(start: pd.Timestamp) -> pd.DataFrame:
    """400 s parado, aceleração, 300 s a 95 km/h, frenagem brusca e parada (mesma viagem de test_insights)."""
    speed = np.concatenate([np.zeros(400), np.linspace(0, 95, 120), np.full(300, 95.0), np.linspace(95, 80, 30),
                            [80 - 7.2 * i for i in range(1, 4)], np.full(60, 58.4), np.linspace(58.4, 0, 60),
                            np.zeros(30)])
    return pd.DataFrame({
        "time": pd.date_range(start, periods=len(speed), freq="1s"),
        "rpm": np.where(speed < 1, 700, 1500 + speed * 6).astype(int), "speed_kmh": speed,
        "fuel_rate_lph": np.where(speed < 1, 3.6, 18.0), "load_pct": np.where(speed < 1, 10.0, 50.0),
        "odometer_km": 1000 + np.cumsum(speed) / 3600, "coolant_c": 90.0, "oil_kpa": 300.0,
        # demais sinais do modelo da Empresa B (13), para o consumo esperado e a saúde serem calculados
        "engine_torque_pct": np.where(speed < 1, 5.0, 45.0), "pedal_pct": np.where(speed < 1, 0.0, 40.0),
        "gear": np.where(speed < 1, 1, 5), "boost_kpa": np.where(speed < 1, 5.0, 80.0), "intake_c": 35.0,
        "battery_v": 14.0, "fuel_level_pct": 80.0,
    })


def _ended_trip(conn, vid, did, start, frame, with_speed=True) -> str:
    tid = str(uuid.uuid4())
    end = frame["time"].iloc[-1]
    conn.execute("INSERT INTO fleet.trips (id, driver_id, vehicle_id, start_time, end_time) VALUES (%s,%s,%s,%s,%s)",
                 (tid, did, vid, start.to_pydatetime(), end.to_pydatetime()))
    with conn.cursor() as cur, cur.copy("COPY telemetry.vehicle_telemetry (time, vehicle_id, trip_id, engine_rpm, "
                                        "engine_temp_c, odometer_km, speed_kmh, load_pct, oil_kpa, fuel_rate_lph, "
                                        "engine_torque_pct, pedal_pct, gear, boost_kpa, intake_c, battery_voltage, "
                                        "fuel_level_pct) FROM STDIN") as cp:
        for r in frame.itertuples(index=False):
            cp.write_row([r.time.to_pydatetime(), vid, tid, int(r.rpm), r.coolant_c, r.odometer_km,
                          r.speed_kmh if with_speed else None, r.load_pct, r.oil_kpa, r.fuel_rate_lph,
                          r.engine_torque_pct, r.pedal_pct, int(r.gear), r.boost_kpa, r.intake_c, r.battery_v,
                          r.fuel_level_pct])
    if not with_speed:
        with conn.cursor() as cur, cur.copy("COPY telemetry.vehicle_positions (time, vehicle_id, trip_id, latitude, "
                                            "longitude, speed_kmh) FROM STDIN") as cp:
            for r in frame.itertuples(index=False):
                cp.write_row([r.time.to_pydatetime(), vid, tid, -22.9, -47.0, r.speed_kmh])
    conn.commit()
    return tid


# ---------------------------------------------------------------------------------------------------------------
# políticas e métricas por viagem

def test_policy_comes_from_the_seeded_tables(conn):
    p = db.load_policy(conn)
    assert (p.speed_limit_kmh, p.idle_threshold_s, p.fuel_price_per_liter) == (90.0, 300, 6.2)
    assert p.rpm_max_for("RIGID_TRUCK") == 2000 and p.rpm_max_for("ARTICULATED_VEHICLE") == 1600
    conn.execute("UPDATE fleet.fleet_policy SET speed_limit_kmh = 80 WHERE id = 1")
    assert db.load_policy(conn).speed_limit_kmh == 80.0
    conn.rollback()


def test_ended_trip_gets_metrics_once_and_is_recomputed_if_extended(conn):
    vid, did = _vehicle(conn), _driver(conn)
    start = NOW - pd.Timedelta(hours=3)
    tid = _ended_trip(conn, vid, did, start, _trip_frame(start))
    resolver = ModelResolver(Path("models"), {})   # sem modelo: métricas sem consumo esperado/saúde
    assert compute_pending_trips(conn, resolver, batch=50, window_s=60) == 1
    m = conn.execute("SELECT * FROM telemetry.trip_metrics WHERE trip_id = %s", (tid,)).fetchone()
    assert m["source"] == "LIVE" and str(m["driver_id"]) == did
    assert m["speeding_s"] >= 300 and m["harsh_brake_count"] == 1 and m["excessive_idle_s"] == 402
    assert m["fuel_expected_l"] is None and m["policy"]["speed_limit_kmh"] == 90.0
    assert compute_pending_trips(conn, resolver, batch=50, window_s=60) == 0     # idempotente

    # a viagem foi reaberta/encerrada mais tarde: as métricas antigas ficam desatualizadas e são refeitas
    conn.execute("UPDATE fleet.trips SET end_time = now() + interval '1 minute' WHERE id = %s", (tid,))
    conn.commit()
    assert compute_pending_trips(conn, resolver, batch=50, window_s=60) == 1


def test_backfilled_metrics_are_never_recomputed(conn):
    vid, did = _vehicle(conn), _driver(conn)
    start = NOW - pd.Timedelta(days=200)
    tid = str(uuid.uuid4())
    conn.execute("INSERT INTO fleet.trips (id, driver_id, vehicle_id, start_time, end_time) VALUES (%s,%s,%s,%s,%s)",
                 (tid, did, vid, start.to_pydatetime(), (start + pd.Timedelta(hours=2)).to_pydatetime()))
    conn.execute("INSERT INTO telemetry.trip_metrics (trip_id, vehicle_id, driver_id, start_time, source, computed_at)"
                 " VALUES (%s, %s, %s, %s, 'BACKFILL', %s)",
                 (tid, vid, did, start.to_pydatetime(), (start - pd.Timedelta(days=1)).to_pydatetime()))
    conn.commit()
    assert db.trips_to_compute(conn).empty


def test_speed_falls_back_to_positions_when_telemetry_has_none(conn):
    vid, did = _vehicle(conn), _driver(conn)
    start = NOW - pd.Timedelta(hours=5)
    frame = _trip_frame(start)
    tid = _ended_trip(conn, vid, did, start, frame, with_speed=False)
    tel = db.load_trip_telemetry(conn, tid, start.to_pydatetime(), frame["time"].iloc[-1].to_pydatetime())
    assert tel["speed_kmh"].notna().all()
    assert tel["speed_kmh"].max() == pytest.approx(95.0)


def test_trip_metrics_upsert_roundtrips_json(conn):
    vid, did = _vehicle(conn), _driver(conn)
    start = NOW - pd.Timedelta(hours=8)
    tid = _ended_trip(conn, vid, did, start, _trip_frame(start))
    trip = {"trip_id": tid, "vehicle_id": vid, "driver_id": did, "start_time": start.to_pydatetime(), "end_time": None}
    metrics = {c: None for c in db.TRIP_METRIC_COLUMNS} | {
        "engine_on_s": 10, "driving_s": 5, "idle_s": 5, "excessive_idle_s": 0, "distance_km": 0.1,
        "harsh_accel_count": 0, "harsh_brake_count": 0, "speeding_s": 0, "rpm_over_s": 0, "events_warning": 0,
        "events_critical": 0, "events_ml": 0, "health": {"windows": np.int64(2), "signals": {"oil_kpa": {
            "actual": np.float64(280.0), "expected": 300.0}}}, "policy": {"speed_limit_kmh": 90.0}}
    db.upsert_trip_metrics(conn, trip, metrics)
    db.upsert_trip_metrics(conn, trip, {**metrics, "engine_on_s": 20})
    conn.commit()
    rows = conn.execute("SELECT engine_on_s, health FROM telemetry.trip_metrics WHERE trip_id = %s", (tid,)).fetchall()
    assert len(rows) == 1 and rows[0]["engine_on_s"] == 20
    assert rows[0]["health"]["signals"]["oil_kpa"]["actual"] == 280.0


# ---------------------------------------------------------------------------------------------------------------
# ciclo de vida dos insights no banco

def _cand(vid, severity="WARNING", key=None):
    return Candidate(key or f"VEHICLE_FUEL_TREND:{vid}", "VEHICLE", vid, "VEHICLE_FUEL_TREND", severity,
                     "Consumo", f"texto {severity}", NOW - pd.Timedelta(days=7), NOW, {"ratio": np.float64(1.3)})


def _cycle(conn, candidates, now=NOW):
    open_ = db.load_open_insights(conn)
    dismissed = db.load_dismissed(conn, (now - pd.Timedelta(days=14)).to_pydatetime())
    counts = db.apply_actions(conn, plan(candidates, open_, dismissed, now))
    conn.commit()
    return counts


def _rows(conn):
    return conn.execute("SELECT * FROM telemetry.insights ORDER BY created_at").fetchall()


def test_insert_update_and_unique_open_insight_per_key(conn):
    vid = _vehicle(conn)
    assert _cycle(conn, [_cand(vid)])["insert"] == 1
    assert _cycle(conn, [_cand(vid, "CRITICAL")])["update"] == 1
    rows = _rows(conn)
    assert len(rows) == 1 and rows[0]["severity"] == "CRITICAL" and rows[0]["evidence"]["ratio"] == 1.3
    with pytest.raises(psycopg.errors.UniqueViolation):   # o índice parcial garante um aberto por chave
        db.apply_actions(conn, [Action("insert", _cand(vid))])
    conn.rollback()


def test_resolution_waits_for_the_hysteresis_window(conn):
    vid = _vehicle(conn)
    _cycle(conn, [_cand(vid)])
    assert _cycle(conn, [])["resolve"] == 0                         # sumiu há pouco: continua aberto
    conn.execute("UPDATE telemetry.insights SET updated_at = now() - interval '3 days'")
    conn.commit()
    assert _cycle(conn, [])["resolve"] == 1
    assert _rows(conn)[0]["status"] == "RESOLVED"
    assert _cycle(conn, [_cand(vid)])["insert"] == 1                 # condição voltou: novo insight
    assert [r["status"] for r in _rows(conn)] == ["RESOLVED", "ACTIVE"]


def test_dismissed_insight_is_snoozed_unless_it_gets_worse(conn):
    vid = _vehicle(conn)
    _cycle(conn, [_cand(vid)])
    conn.execute("UPDATE telemetry.insights SET status = 'DISMISSED', updated_at = now()")
    conn.commit()
    assert _cycle(conn, [_cand(vid)])["insert"] == 0                 # mesma gravidade: silenciado
    assert _cycle(conn, [_cand(vid, "CRITICAL")])["insert"] == 1     # agravou: volta a aparecer
    conn.execute("UPDATE telemetry.insights SET status = 'DISMISSED', updated_at = now() - interval '20 days'")
    conn.commit()
    assert _cycle(conn, [_cand(vid)])["insert"] == 1                 # passou o prazo do descarte


def test_acknowledged_insight_reopens_only_when_it_escalates(conn):
    vid, did = _vehicle(conn), _driver(conn)
    _cycle(conn, [_cand(vid)])
    conn.execute("UPDATE telemetry.insights SET status = 'ACKNOWLEDGED', acknowledged_by = %s, "
                 "acknowledged_at = now()", (did,))
    conn.commit()
    _cycle(conn, [_cand(vid)])
    assert _rows(conn)[0]["status"] == "ACKNOWLEDGED"
    _cycle(conn, [_cand(vid, "CRITICAL")])
    r = _rows(conn)[0]
    assert r["status"] == "ACTIVE" and r["acknowledged_by"] is None


def test_rules_read_names_and_types_from_the_platform_tables(conn):
    vid, did = _vehicle(conn, "Bandeirante 01"), _driver(conn, "Carla")
    start = NOW - pd.Timedelta(hours=2)
    _ended_trip(conn, vid, did, start, _trip_frame(start))
    compute_pending_trips(conn, ModelResolver(Path("models"), {}), 10, 60)
    trips = db.load_trips_for_rules(conn, (NOW - pd.Timedelta(days=1)).to_pydatetime())
    row = trips.iloc[0]
    assert (row["vehicle_name"], row["driver_name"], row["vehicle_type"]) == ("Bandeirante 01", "Carla Teste",
                                                                             "RIGID_TRUCK")
    assert isinstance(row["health"], dict) and row["vehicle_id"] == vid


def test_run_once_end_to_end_with_the_trained_model(conn):
    """Executor completo com o modelo da Empresa B: métricas, consumo esperado e regras sobre o banco."""
    vid, did = _vehicle(conn), _driver(conn)
    start = NOW - pd.Timedelta(hours=4)
    tid = _ended_trip(conn, vid, did, start, _trip_frame(start))
    resolver = ModelResolver(Path("models"), {"default": {"company": "empresa-b", "group": "caminhao-medio-sim",
                                                          "assume_warm": True}})
    result = run_once({"trip_batch": 10, "window_s": 60, "lookback_days": 100}, resolver)
    assert result["trips_computed"] == 1
    m = conn.execute("SELECT fuel_expected_l, health FROM telemetry.trip_metrics WHERE trip_id = %s", (tid,)).fetchone()
    assert m["health"]["windows"] > 0 and m["fuel_expected_l"] > 0
    again = evaluate_insights(conn, 100)
    assert again["insert"] == 0                                     # nada novo na segunda avaliação
