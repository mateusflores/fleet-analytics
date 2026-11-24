"""Ramos das regras de insight, do planner e das métricas por viagem que os cenários de test_insights não cobrem."""

import numpy as np
import pandas as pd
import pytest

from ml_service.insights.planner import DISMISS_SNOOZE_DAYS, plan
from ml_service.insights.policy import FleetPolicy
from ml_service.insights.rules import (Candidate, driver_peer_insights, fleet_efficiency_ranking, fleet_idle_cost,
                                       pct_more, vehicle_alert_recurrence, vehicle_fuel_trend, vehicle_health_drift)
from ml_service.insights.trip_metrics import compute_trip_metrics
from test_insights import NOW, POLICY, _history, _trip_frame


@pytest.fixture()
def history():
    return _history()


# ---------------------------------------------------------------------------------------------------------------
# motoristas

def test_peers_require_three_drivers_per_vehicle_type(history):
    trips, _ = history
    few = trips[trips["driver_id"].isin(["d4", "d5"])]
    assert driver_peer_insights(few, NOW, POLICY) == []


def test_peers_require_minimum_exposure(history):
    trips, _ = history
    t = trips.copy()
    # d5 (o que corre) dirigiu pouco no período: < 10 h → não é avaliado
    recent = t["start_time"] >= NOW - pd.Timedelta(days=30)
    keep = ~(recent & (t["driver_id"] == "d5")) | (t.groupby("driver_id").cumcount() % 50 == 0)
    found = {c.subject_id for c in driver_peer_insights(t[keep], NOW, POLICY) if c.category == "DRIVER_SPEEDING"}
    assert "d5" not in found


def test_drivers_are_compared_only_within_the_same_vehicle_type(history):
    trips, _ = history
    t = trips.copy()
    t.loc[t["driver_id"] == "d5", "vehicle_type"] = "ARTICULATED_VEHICLE"   # sozinho no tipo: sem colegas
    assert not [c for c in driver_peer_insights(t, NOW, POLICY) if c.subject_id == "d5"]


def test_peer_severity_and_dedup_key(history):
    trips, _ = history
    c = next(c for c in driver_peer_insights(trips, NOW, POLICY) if c.category == "DRIVER_SPEEDING")
    assert c.severity == "CRITICAL" and c.evidence["ratio"] >= 2            # 4× a mediana
    assert c.dedup_key == "DRIVER_SPEEDING:d5:RIGID_TRUCK"
    assert pct_more(1.4) == "40% mais" and pct_more(2.5) == "2,5× mais"


# ---------------------------------------------------------------------------------------------------------------
# consumo e desgaste

def test_fuel_trend_needs_recent_and_baseline_data(history):
    trips, _ = history
    only_recent = trips[trips["start_time"] >= NOW - pd.Timedelta(days=7)]
    assert vehicle_fuel_trend(only_recent, NOW) == []


def test_fuel_noise_without_fault_is_not_flagged(history):
    trips, _ = history
    healthy = trips[trips["vehicle_id"] != "v1"]
    assert vehicle_fuel_trend(healthy, NOW) == []


def test_lower_consumption_is_not_an_insight(history):
    trips, _ = history
    t = trips.copy()
    recent = (t["vehicle_id"] == "v0") & (t["start_time"] >= NOW - pd.Timedelta(days=7))
    t.loc[recent, "health"] = t.loc[recent, "health"].map(lambda h: {**h, "fuel_real_l": h["fuel_real_l"] * 0.7})
    assert not [c for c in vehicle_fuel_trend(t, NOW) if c.subject_id == "v0"]


def test_health_direction_matters(history):
    """Pressão do óleo SUBINDO não é desgaste; temperatura caindo também não."""
    trips, _ = history
    t = trips.copy()
    recent = (t["vehicle_id"] == "v0") & (t["start_time"] >= NOW - pd.Timedelta(days=7))

    def better(h):
        s = {k: dict(v) for k, v in h["signals"].items()}
        s["oil_kpa"]["actual"] *= 1.15
        s["coolant_c"]["actual"] -= 5
        return {**h, "signals": s}
    t.loc[recent, "health"] = t.loc[recent, "health"].map(better)
    assert not [c for c in vehicle_health_drift(t, NOW) if c.subject_id == "v0"]


def test_coolant_rule_uses_absolute_degrees(history):
    trips, _ = history
    t = trips.copy()
    recent = (t["vehicle_id"] == "v0") & (t["start_time"] >= NOW - pd.Timedelta(days=7))

    def hot(h):
        s = {k: dict(v) for k, v in h["signals"].items()}
        s["coolant_c"]["actual"] += 7
        return {**h, "signals": s}
    t.loc[recent, "health"] = t.loc[recent, "health"].map(hot)
    c = next(c for c in vehicle_health_drift(t, NOW) if c.subject_id == "v0")
    assert c.evidence["signal"] == "coolant_c" and c.severity == "CRITICAL"   # +7 °C ≥ 6 °C
    assert "°C acima do esperado" in c.description


# ---------------------------------------------------------------------------------------------------------------
# alertas e frota

def _events(rows):
    return pd.DataFrame(rows, columns=["time", "vehicle_id", "event_type", "severity"])


def test_alert_recurrence_needs_history_and_a_real_increase(history):
    trips, _ = history
    week = [(NOW - pd.Timedelta(days=d % 6, hours=1), "v0", "LOW_BATTERY", "WARNING") for d in range(4)]
    assert vehicle_alert_recurrence(_events(week), trips, NOW)                     # 4 na semana, base ~0
    # base com ~4 por semana: 4 na última não é recorrência
    base = [(NOW - pd.Timedelta(days=8 + i * 1.75), "v0", "LOW_BATTERY", "WARNING") for i in range(32)]
    assert vehicle_alert_recurrence(_events(week + base), trips, NOW) == []
    # veículo com histórico curto (primeira viagem há 3 semanas) fica de fora
    short = trips[(trips["vehicle_id"] != "v0") | (trips["start_time"] >= NOW - pd.Timedelta(days=21))]
    assert vehicle_alert_recurrence(_events(week), short, NOW) == []


def test_alert_recurrence_is_critical_when_any_recent_alert_is(history):
    trips, _ = history
    ev = [(NOW - pd.Timedelta(days=i, hours=2), "v0", "ENGINE_OVERHEAT", "CRITICAL" if i == 0 else "WARNING")
          for i in range(4)]
    c = vehicle_alert_recurrence(_events(ev), trips, NOW)[0]
    assert c.severity == "CRITICAL" and c.dedup_key == "VEHICLE_ALERT_RECURRENCE:v0:ENGINE_OVERHEAT"


def test_fleet_idle_cost_threshold_and_numbers(history):
    trips, _ = history
    assert fleet_idle_cost(trips.iloc[:3], NOW, POLICY) == []                      # < R$ 50
    c = fleet_idle_cost(trips, NOW, POLICY)[0]
    ev = c.evidence
    assert ev["cost"] == pytest.approx(ev["idle_fuel_l"] * POLICY.fuel_price_per_liter, rel=1e-3)
    assert ev["saving_half_excessive"] == pytest.approx(ev["excessive_idle_fuel_l"] / 2 * 6.0, rel=1e-2)


def test_efficiency_ranking_needs_three_vehicles_and_lists_the_worst(history):
    trips, _ = history
    assert fleet_efficiency_ranking(trips[trips["vehicle_id"].isin(["v0", "v1"])], NOW) == []
    t = trips.copy()
    sel = (t["vehicle_id"] == "v4") & (t["start_time"] >= NOW - pd.Timedelta(days=30))
    t.loc[sel, "health"] = t.loc[sel, "health"].map(lambda h: {**h, "fuel_real_l": h["fuel_real_l"] * 1.25})
    c = fleet_efficiency_ranking(t, NOW)[0]
    assert c.evidence["vehicles"][0]["vehicle_id"] == "v4" and c.severity == "WARNING"


# ---------------------------------------------------------------------------------------------------------------
# planner

def _cand(key, severity="WARNING"):
    return Candidate(key, "VEHICLE", "v", "X", severity, "t", "d", NOW, NOW)


def test_planner_ignores_duplicate_candidates_and_expired_dismissals():
    dismissed = pd.DataFrame([{"dedup_key": "a", "severity": "CRITICAL",
                               "updated_at": NOW - pd.Timedelta(days=DISMISS_SNOOZE_DAYS + 1)}])
    actions = plan([_cand("a"), _cand("a", "CRITICAL")], pd.DataFrame(), dismissed, NOW)
    assert [(a.kind, a.candidate.severity) for a in actions] == [("insert", "WARNING")]


def test_planner_does_not_reopen_acknowledged_without_escalation():
    open_ = pd.DataFrame([{"id": "1", "dedup_key": "a", "status": "ACKNOWLEDGED", "severity": "CRITICAL"}])
    (a,) = plan([_cand("a", "WARNING")], open_, pd.DataFrame(), NOW)
    assert a.kind == "update" and not a.reopen


# ---------------------------------------------------------------------------------------------------------------
# métricas por viagem

def test_gap_splits_segments_so_idle_runs_do_not_bridge_it():
    """Dois trechos parados de 200 s separados por 10 min sem dados não viram uma ociosidade de 400 s."""
    a = _trip_frame().iloc[:200].copy()
    b = a.copy()
    b["time"] = b["time"] + pd.Timedelta(seconds=200 + 600)
    m = compute_trip_metrics(pd.concat([a, b]), POLICY, "RIGID_TRUCK")
    assert m["idle_s"] == 400 and m["excessive_idle_s"] == 0
    assert m["engine_on_s"] == 400                                               # a lacuna não conta


def test_odometer_reset_falls_back_to_speed_integral():
    raw = _trip_frame()
    raw.loc[raw.index[500]:, "odometerKm"] = 0.0                                  # módulo reiniciou no meio
    m = compute_trip_metrics(raw, POLICY, "RIGID_TRUCK")
    assert m["distance_km"] == pytest.approx(raw["speedKmh"].sum() / 3600, rel=0.02)


def test_harsh_acceleration_episodes_and_policy_thresholds():
    raw = _trip_frame()
    speed = raw["speedKmh"].to_numpy().copy()
    speed[400:403] = [0, 3.6, 7.2]                                                # +1 m/s² por 2 s seguidos
    speed[403:520] = np.linspace(7.2, 95, 117)
    raw["speedKmh"] = speed
    assert compute_trip_metrics(raw, POLICY, "RIGID_TRUCK")["harsh_accel_count"] == 1
    lenient = FleetPolicy(harsh_accel_ms2=1.5, harsh_brake_ms2=3.0, economy_rpm_max={"RIGID_TRUCK": 2200})
    m = compute_trip_metrics(raw, lenient, "RIGID_TRUCK")
    assert (m["harsh_accel_count"], m["harsh_brake_count"], m["rpm_over_s"]) == (0, 0, 0)
    assert m["policy"]["economy_rpm_max"] == 2200


def test_policy_defaults_for_unknown_vehicle_types():
    assert POLICY.rpm_max_for(None) == 2000 and POLICY.rpm_max_for("TRACTOR_X") == 2000
    snap = POLICY.snapshot("RIGID_TRUCK")
    assert snap["economy_rpm_max"] == 2000 and snap["speed_limit_kmh"] == 90


def test_coarse_odometer_resolution_is_still_used():
    """Odômetro J1939 com resolução de 0,125 km (degraus) continua valendo como distância."""
    raw = _trip_frame()
    raw["odometerKm"] = (raw["odometerKm"] / 0.125).round() * 0.125
    m = compute_trip_metrics(raw, POLICY, "RIGID_TRUCK")
    assert m["distance_km"] == pytest.approx(raw["odometerKm"].iloc[-1] - raw["odometerKm"].iloc[0], abs=1e-9)
