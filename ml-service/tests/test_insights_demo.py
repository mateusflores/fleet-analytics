from datetime import date, timedelta

import pandas as pd
import pytest

from ml_service.insights.demo.evaluate import Episode, replay, score
from ml_service.insights.demo.scenario import DemoConfig, driver_state, fault_intensity, ground_truth, schedule
from test_insights import NOW, POLICY, _history

END = date(2026, 11, 23)


@pytest.fixture(scope="module")
def cfg():
    return DemoConfig.load("configs/demo-history.yaml", END)


# ---------------------------------------------------------------------------------------------------------------
# agenda

def test_schedule_is_deterministic_and_can_be_split_by_month(cfg):
    full = schedule(cfg)
    again = schedule(cfg)
    pd.testing.assert_frame_equal(full, again)
    month = schedule(cfg, date(2026, 3, 1), date(2026, 3, 31))
    pd.testing.assert_frame_equal(full[full["date"].str.startswith("2026-03")].reset_index(drop=True), month)


def test_drivers_rotate_across_vehicles_and_never_double_book(cfg):
    s = schedule(cfg)
    assert not s.duplicated(["date", "driver"]).any()
    assert not s.duplicated(["date", "vehicle"]).any()
    per_driver = s.groupby("driver")["vehicle"].nunique()
    assert per_driver.min() >= 6          # cada motorista dirige vários caminhões
    assert (pd.to_datetime(s["date"]).dt.day_of_week < 6).all()   # domingo sem operação


def test_fault_ramp_and_repair(cfg):
    f = next(sc["fault"] for sc in cfg.scenarios if sc["id"] == "S5")
    start = cfg.day(f["start_days_ago"])
    assert fault_intensity(cfg, f, start - timedelta(days=1)) == 0
    assert fault_intensity(cfg, f, start) == pytest.approx(f["peak"] / f["ramp_days"], abs=1e-4)
    assert fault_intensity(cfg, f, start + timedelta(days=f["ramp_days"] + 5)) == f["peak"]
    assert fault_intensity(cfg, f, cfg.day(f["end_days_ago"])) == 0


def test_driver_style_change_from_date(cfg):
    d05 = next(d for d in cfg.drivers if d["key"] == "d05")
    change = cfg.day(100)
    assert driver_state(cfg, d05, change - timedelta(days=1))[0] == "NORMAL"
    assert driver_state(cfg, d05, change)[0] == "AGGRESSIVE"


def test_ground_truth_lists_every_expectation(cfg):
    gt = ground_truth(cfg)
    assert {g["scenario"] for g in gt} == {sc["id"] for sc in cfg.scenarios}
    s5 = next(g for g in gt if g["scenario"] == "S5")
    assert s5["resolves"] and s5["end"] is not None and s5["signal"] == "battery_v"
    assert next(g for g in gt if g["scenario"] == "S1")["start"] == cfg.start.isoformat()


# ---------------------------------------------------------------------------------------------------------------
# avaliação

def _ep(cat, subject, opened, closed=None, signal=None, scope="VEHICLE"):
    return Episode(f"{cat}:{subject}:{signal}", cat, scope, subject, signal, pd.Timestamp(opened, tz="UTC"),
                   pd.Timestamp(closed, tz="UTC") if closed else None, ["WARNING"], ["texto"])


TRUTH = [
    {"scenario": "A", "title": "", "category": "VEHICLE_FUEL_TREND", "subject": "v1", "signal": None,
     "start": "2026-06-01", "end": None, "resolves": False, "optional": False},
    {"scenario": "B", "title": "", "category": "VEHICLE_HEALTH_DRIFT", "subject": "v2", "signal": "battery_v",
     "start": "2026-05-01", "end": "2026-06-01", "resolves": True, "optional": False},
    {"scenario": "C", "title": "", "category": "DRIVER_IDLE", "subject": "d9", "signal": None,
     "start": "2026-01-01", "end": None, "resolves": False, "optional": False},
]


def test_score_detection_delay_resolution_false_and_related():
    eps = [
        _ep("VEHICLE_FUEL_TREND", "v1", "2026-06-09"),                                    # A: 8 dias
        _ep("VEHICLE_HEALTH_DRIFT", "v2", "2026-05-15", "2026-06-11", signal="battery_v"),  # B: resolve em 10 dias
        _ep("VEHICLE_HEALTH_DRIFT", "v2", "2026-05-20", "2026-05-25", signal="oil_kpa"),  # relacionado (v2 ativo)
        _ep("DRIVER_SPEEDING", "d1", "2026-07-01", "2026-07-03", scope="DRIVER"),         # falso
        _ep("FLEET_IDLE_COST", None, "2026-04-01", scope="FLEET"),                        # frota, à parte
    ]
    r = score(eps, TRUTH, pd.Timestamp("2026-04-01", tz="UTC"), pd.Timestamp("2026-07-31", tz="UTC"))
    by = {g["scenario"]: g for g in r["per_expectation"]}
    assert by["A"]["detected"] and by["A"]["delay_days"] == 8
    assert by["B"]["resolved_after_days"] == 10
    assert not by["C"]["detected"] and by["C"]["started_before_eval"]
    assert (r["expected"], r["detected"]) == (3, 2)
    assert r["false_episodes"] == 1 and r["false"][0]["subject"] == "d1"
    assert r["related_episodes"] == 1
    assert r["fleet"]["episodes"] == 1


def test_score_counts_reopenings():
    eps = [_ep("VEHICLE_FUEL_TREND", "v1", "2026-06-09", "2026-06-12"), _ep("VEHICLE_FUEL_TREND", "v1", "2026-06-14")]
    r = score(eps, TRUTH[:1], pd.Timestamp("2026-04-01", tz="UTC"), pd.Timestamp("2026-07-31", tz="UTC"))
    assert r["reopenings"] == 1 and r["per_expectation"][0]["episodes"] == 2


def test_replay_opens_and_keeps_the_planted_insights():
    trips, events = _history()
    days = list(pd.date_range(NOW - pd.Timedelta(days=3), NOW, freq="D"))
    eps = replay(trips, events, days, POLICY)
    opened = {(e.category, e.subject) for e in eps}
    assert ("VEHICLE_FUEL_TREND", "v1") in opened and ("DRIVER_SPEEDING", "d5") in opened
    # um episódio por chave enquanto a condição persiste (atualizado, não recriado)
    keys = [e.dedup_key for e in eps]
    assert len(keys) == len(set(keys))


# ---------------------------------------------------------------------------------------------------------------
# primeira inicialização do compose da demo

def test_seed_reuses_recent_history_and_regenerates_stale(tmp_path, monkeypatch):
    import json

    from ml_service.insights.demo import seed
    calls = []

    def fake_build(config, out, end, jobs):
        calls.append(end)
        m = {"start": "x", "end": end.isoformat(), "trips": 1, "events": 0}
        (out / "manifest.json").write_text(json.dumps(m))
        return m
    monkeypatch.setattr(seed, "build", fake_build)
    today = date(2026, 11, 25)

    seed.ensure_history(tmp_path, today, 2)                                  # volume vazio: gera até ontem
    assert calls == [today - timedelta(days=1)]                              # (hoje deixaria viagens no futuro)
    seed.ensure_history(tmp_path, today + timedelta(days=1), 2)              # termina anteontem: reaproveita
    assert len(calls) == 1
    (tmp_path / "2025-11").mkdir()
    seed.ensure_history(tmp_path, today + timedelta(days=5), 2)              # velho: limpa e gera de novo
    assert calls[-1] == today + timedelta(days=4) and not (tmp_path / "2025-11").exists()
