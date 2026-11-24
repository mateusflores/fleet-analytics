import numpy as np
import pandas as pd
import pytest

from ml_service.insights.planner import plan
from ml_service.insights.policy import FleetPolicy
from ml_service.insights.rules import Candidate, br, evaluate
from ml_service.insights.trip_metrics import compute_trip_metrics

POLICY = FleetPolicy(speed_limit_kmh=90, harsh_accel_ms2=0.7, harsh_brake_ms2=1.5, idle_threshold_s=300,
                     fuel_price_per_liter=6.0, economy_rpm_max={"RIGID_TRUCK": 2000})
NOW = pd.Timestamp("2026-11-23T12:00:00Z")


# ---------------------------------------------------------------------------------------------------------------
# métricas de viagem

def _trip_frame() -> pd.DataFrame:
    """400 s parado (ociosidade > 300 s), aceleração, 300 s a 95 km/h, frenagem brusca e parada."""
    speed = np.concatenate([np.zeros(400), np.linspace(0, 95, 120), np.full(300, 95.0),
                            np.linspace(95, 80, 30), [80 - 7.2 * i for i in range(1, 4)], np.full(60, 58.4),
                            np.linspace(58.4, 0, 60), np.zeros(30)])
    n = len(speed)
    rpm = np.where(speed < 1, 700, 1500 + speed * 6)  # 95 km/h -> 2070 rpm (acima de 2000)
    fuel = np.where(speed < 1, 3.6, 18.0)
    return pd.DataFrame({
        "time": pd.date_range("2026-11-20T10:00:00Z", periods=n, freq="1s"),
        "engineRpm": rpm, "speedKmh": speed, "fuelRateLph": fuel, "loadPct": np.where(speed < 1, 10, 50),
        "odometerKm": 1000 + np.cumsum(speed) / 3600, "engineTempC": 90.0,
    })


def test_trip_metrics_counts_idle_speeding_harsh_and_fuel():
    raw = _trip_frame()
    events = pd.DataFrame({"event_type": ["ENGINE_OVERHEAT", "ML_OIL_KPA", "LOW_FUEL"],
                           "severity": ["CRITICAL", "WARNING", "WARNING"]})
    m = compute_trip_metrics(raw, POLICY, "RIGID_TRUCK", events)
    assert m["engine_on_s"] == len(raw)
    speed = raw["speedKmh"]
    idle_s = int((speed < 1).sum())
    assert m["idle_s"] == idle_s
    assert m["excessive_idle_s"] == 402          # só o trecho inicial (400 s + 2 pontos da rampa) passa de 300 s
    assert m["speeding_s"] == int((speed > 90).sum()) and m["speeding_s"] >= 300
    assert m["harsh_brake_count"] == 1           # 3 s seguidos a −2 m/s² = um episódio
    assert m["harsh_accel_count"] == 0
    assert m["rpm_over_s"] > 300
    assert m["fuel_used_l"] == pytest.approx(raw["fuelRateLph"].sum() / 3600, rel=1e-3)
    assert m["idle_fuel_l"] == pytest.approx(raw.loc[speed < 1, "fuelRateLph"].sum() / 3600, rel=1e-3)
    assert m["distance_km"] == pytest.approx(raw["speedKmh"].sum() / 3600, rel=0.01)
    assert (m["events_critical"], m["events_warning"], m["events_ml"]) == (1, 1, 1)
    assert m["policy"]["economy_rpm_max"] == 2000


class FakeDetector:
    """Detector mínimo: o 'esperado' é 80% do consumo real e 110% da pressão do óleo real."""
    signals = ["rpm", "speed_kmh", "fuel_rate_lph", "oil_kpa"]
    ev = {"fuel_rate_lph": {}, "oil_kpa": {}}

    def score(self, w):
        out = pd.DataFrame(index=w.index)
        out["scored"] = True
        out["resid_fuel_rate_lph"] = w["fuel_rate_lph_mean"] * 0.2
        out["resid_oil_kpa"] = -w["oil_kpa_mean"] * 0.1
        return out


def test_trip_metrics_expected_fuel_and_health_from_detector():
    raw = _trip_frame().assign(oilKpa=300.0)
    m = compute_trip_metrics(raw, POLICY, "RIGID_TRUCK", detector=FakeDetector())
    h = m["health"]
    assert h["windows"] == len(raw) // 60
    assert h["fuel_expected_l"] == pytest.approx(0.8 * h["fuel_real_l"])
    assert m["fuel_expected_l"] == h["fuel_expected_l"]
    assert h["signals"]["oil_kpa"]["expected"] == pytest.approx(330.0)


def test_trip_metrics_without_rpm_returns_empty_metrics():
    m = compute_trip_metrics(pd.DataFrame({"time": [], "speedKmh": []}), POLICY)
    assert m["engine_on_s"] == 0 and m["fuel_used_l"] is None


# ---------------------------------------------------------------------------------------------------------------
# regras: histórico sintético com cenários plantados

def _history() -> tuple[pd.DataFrame, pd.DataFrame]:
    rng = np.random.default_rng(0)
    rows, events = [], []
    drivers = {f"d{i}": f"Motorista {i}" for i in range(6)}
    vehicles = {f"v{i}": f"Caminhão {i}" for i in range(5)}
    for day in range(100):
        date = NOW - pd.Timedelta(days=100 - day)
        recent = day >= 93
        for k, vid in enumerate(vehicles):
            for trip in range(2):
                did = f"d{(k + trip + day) % 6}"
                drive = rng.uniform(2.5, 3.5) * 3600
                dist = drive / 3600 * rng.uniform(55, 65)
                exp_fuel = dist * 0.27
                real_fuel = exp_fuel * rng.normal(1.0, 0.03) * (1.30 if (vid == "v1" and recent) else 1.0)
                speeding = drive * rng.uniform(0.003, 0.006) * (4 if did == "d5" else 1)
                oil_dev = -0.10 if (vid == "v2" and recent) else 0.0
                idle = rng.uniform(600, 1200)
                rows.append({
                    "trip_id": f"{vid}-{day}-{trip}", "vehicle_id": vid, "driver_id": did,
                    "start_time": date + pd.Timedelta(hours=6 + 5 * trip), "engine_on_s": drive + idle,
                    "driving_s": drive, "idle_s": idle, "excessive_idle_s": idle * 0.3, "distance_km": dist,
                    "fuel_used_l": real_fuel + 1, "fuel_expected_l": exp_fuel, "idle_fuel_l": idle / 3600 * 2.6,
                    "speeding_s": speeding, "harsh_accel_count": rng.poisson(1), "harsh_brake_count": rng.poisson(1),
                    "rpm_over_s": drive * 0.05, "vehicle_type": "RIGID_TRUCK", "vehicle_name": vehicles[vid],
                    "driver_name": drivers[did],
                    "health": {"windows": 150, "fuel_real_l": real_fuel, "fuel_expected_l": exp_fuel,
                               "signals": {"oil_kpa": {"actual": 330 * (1 + oil_dev + rng.normal(0, 0.01)),
                                                       "expected": 330.0},
                                           "coolant_c": {"actual": 90 + rng.normal(0, 0.3), "expected": 90.0}}},
                })
            n_alerts = rng.poisson(0.15) + (3 if (vid == "v3" and recent and day % 2 == 0) else 0)
            for _ in range(n_alerts):
                events.append({"time": date + pd.Timedelta(hours=rng.uniform(6, 18)), "vehicle_id": vid,
                               "event_type": "ENGINE_OVERHEAT", "severity": "WARNING"})
    return pd.DataFrame(rows), pd.DataFrame(events)


@pytest.fixture(scope="module")
def insights():
    trips, events = _history()
    return evaluate(trips, events, NOW, POLICY)


def _keys(cands, category):
    return {c.subject_id for c in cands if c.category == category}


def test_planted_scenarios_are_found(insights):
    assert _keys(insights, "DRIVER_SPEEDING") == {"d5"}
    assert _keys(insights, "VEHICLE_FUEL_TREND") == {"v1"}
    assert _keys(insights, "VEHICLE_HEALTH_DRIFT") == {"v2"}
    assert _keys(insights, "VEHICLE_ALERT_RECURRENCE") == {"v3"}


def test_no_false_alarms_on_normal_subjects(insights):
    for category in ("DRIVER_HARSH_DRIVING", "DRIVER_IDLE", "DRIVER_RPM"):
        assert _keys(insights, category) == set(), category
    drift = [c for c in insights if c.category == "VEHICLE_HEALTH_DRIFT"]
    assert {c.evidence["signal"] for c in drift} == {"oil_kpa"}


def test_texts_carry_the_numbers(insights):
    fuel = next(c for c in insights if c.category == "VEHICLE_FUEL_TREND")
    assert fuel.severity == "CRITICAL"  # +30% ≥ 25%
    assert fuel.description.startswith("Caminhão 1: consumo 3")
    speeding = next(c for c in insights if c.category == "DRIVER_SPEEDING")
    assert "Motorista 5" in speeding.description and "acima de 90 km/h" in speeding.description
    idle = next(c for c in insights if c.category == "FLEET_IDLE_COST")
    assert "R$" in idle.description
    assert br(1234.5, 1) == "1.234,5"


# ---------------------------------------------------------------------------------------------------------------
# reconciliação

def _cand(key, severity="WARNING"):
    return Candidate(key, "VEHICLE", "v", "X", severity, "t", "d", NOW, NOW)


def test_planner_insert_update_resolve_reopen_and_snooze():
    open_ = pd.DataFrame([{"id": "1", "dedup_key": "a", "status": "ACTIVE", "severity": "WARNING"},
                          {"id": "2", "dedup_key": "b", "status": "ACKNOWLEDGED", "severity": "WARNING"},
                          {"id": "3", "dedup_key": "gone", "status": "ACTIVE", "severity": "INFO"}])
    dismissed = pd.DataFrame([{"dedup_key": "d", "severity": "WARNING", "updated_at": NOW - pd.Timedelta(days=3)},
                              {"dedup_key": "e", "severity": "WARNING", "updated_at": NOW - pd.Timedelta(days=3)}])
    actions = plan([_cand("a"), _cand("b", "CRITICAL"), _cand("c"), _cand("d"), _cand("e", "CRITICAL")],
                   open_, dismissed, NOW)
    by = {(a.kind, a.candidate.dedup_key if a.candidate else a.insight_id): a for a in actions}
    assert ("update", "a") in by and not by[("update", "a")].reopen
    assert by[("update", "b")].reopen                      # agravou: reconhecido volta a ativo
    assert ("insert", "c") in by
    assert ("insert", "d") not in by                       # descartado há 3 dias, mesma gravidade
    assert ("insert", "e") in by                           # descartado, mas agravou
    assert ("resolve", "3") in by
    assert len(actions) == 5


def test_planner_resolves_only_after_hysteresis():
    open_ = pd.DataFrame([{"id": "1", "dedup_key": "recent", "status": "ACTIVE", "severity": "WARNING",
                           "updated_at": NOW - pd.Timedelta(hours=6)},
                          {"id": "2", "dedup_key": "stale", "status": "ACTIVE", "severity": "WARNING",
                           "updated_at": NOW - pd.Timedelta(hours=49)}])
    actions = plan([], open_, pd.DataFrame(), NOW)
    assert [(a.kind, a.insight_id) for a in actions] == [("resolve", "2")]


def test_peer_rule_requires_relevant_absolute_difference():
    """+40% sobre a mediana, mas só ~1 ponto percentual de ociosidade: não vira insight; 3× (≈ 6 p.p.) vira."""
    trips, events = _history()
    for factor, expected in ((1.4, set()), (3.0, {"d2"})):
        t = trips.copy()
        t.loc[t["driver_id"] == "d2", "excessive_idle_s"] *= factor
        found = {c.subject_id for c in evaluate(t, events, NOW, POLICY) if c.category == "DRIVER_IDLE"}
        assert found == expected, factor


def test_stable_fault_stays_detected_by_persistent_deviation():
    """Óleo 10% abaixo do esperado há 50 dias: a linha de base do próprio veículo já está degradada (sem tendência),
    mas o desvio em relação ao modelo persiste — o insight continua."""
    from ml_service.insights.rules import vehicle_health_drift
    trips, _ = _history()
    old = trips["start_time"] >= NOW - pd.Timedelta(days=50)
    sel = (trips["vehicle_id"] == "v4") & old

    def degrade(h):
        h = {**h, "signals": {**h["signals"], "oil_kpa": {**h["signals"]["oil_kpa"]}}}
        h["signals"]["oil_kpa"]["actual"] *= 0.90
        return h
    trips.loc[sel, "health"] = trips.loc[sel, "health"].map(degrade)
    found = [c for c in vehicle_health_drift(trips, NOW) if c.subject_id == "v4"]
    assert [c.evidence["kind"] for c in found] == ["persistent"]
    assert "desvio persistente" in found[0].description


def test_persistent_deviation_works_with_four_recent_days():
    """Semana com feriado/fim de semana: só 4 dias recentes ainda bastam para o critério persistente."""
    from ml_service.insights.rules import vehicle_health_drift
    trips, _ = _history()
    recent_days = trips["start_time"] >= NOW - pd.Timedelta(days=7)
    keep = ~recent_days | (trips["start_time"] >= NOW - pd.Timedelta(days=4))
    trips = trips[keep].copy()
    sel = (trips["vehicle_id"] == "v4") & (trips["start_time"] >= NOW - pd.Timedelta(days=50))

    def degrade(h):
        h = {**h, "signals": {**h["signals"], "oil_kpa": {**h["signals"]["oil_kpa"]}}}
        h["signals"]["oil_kpa"]["actual"] *= 0.90
        return h
    trips.loc[sel, "health"] = trips.loc[sel, "health"].map(degrade)
    assert [c.subject_id for c in vehicle_health_drift(trips, NOW) if c.subject_id == "v4"] == ["v4"]
