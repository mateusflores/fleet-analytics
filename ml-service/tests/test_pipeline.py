import numpy as np
import pandas as pd
import pytest

from ml_service.config import load_config
from ml_service.detector import GroupDetector, time_block_folds
from ml_service.features.windows import compute_windows
from ml_service.ingest import load_company_data
from ml_service.quality import quality_report
from ml_service.synthetic.faults import applicable_faults, event_profile, inject
from ml_service.synthetic.simulator_fleet import generate


@pytest.fixture(scope="module")
def company(tmp_path_factory):
    d = tmp_path_factory.mktemp("co")
    generate(vehicles=2, hours=1.5, seed=1).to_csv(d / "fleet.csv", index=False)
    (d / "cfg.yaml").write_text(f"""
company: teste
min_engine_hours: 1
sources:
  - adapter: csv
    path: {d / 'fleet.csv'}
    vehicle_group: sim
    ts_column: timestamp
    vehicle_id_column: vehicleId
    columns: {{rpm: engineRpm, speed_kmh: speedKmh, coolant_c: engineTempC, battery_v: batteryVoltage, fuel_level_pct: fuelLevelPct}}
""")
    cfg = load_config(d / "cfg.yaml")
    return d, cfg, load_company_data(cfg)


def test_csv_adapter_produces_canonical_table(company):
    _, cfg, df = company
    assert set(df["vehicle_id"]) == {"sim-01", "sim-02"}
    assert {"ts", "segment", "engine_on", "rpm", "coolant_c"} <= set(df.columns)
    assert df["engine_on"].all() and df.groupby("vehicle_id")["ts"].apply(lambda s: s.is_monotonic_increasing).all()
    q = quality_report(df, cfg)
    assert q["ok"] and "rpm" in q["groups"]["sim"]["usable_signals"]


def test_quality_rejects_missing_rpm_and_short_data(company):
    _, cfg, df = company
    bad = df.assign(rpm=np.nan, engine_on=True)
    assert any("rpm" in e for e in quality_report(bad, cfg)["errors"])
    assert any("motor ligado" in e for e in quality_report(df.iloc[:600], cfg)["errors"])


def test_event_profile_and_injection_only_touch_events(company):
    _, _, df = company
    frame = df[df["vehicle_id"] == "sim-01"].iloc[:2000].reset_index(drop=True)
    profile, events = event_profile(2000, 300, 900)
    assert events == [(300, 600), (1200, 1500)] and profile.max() == 1 and profile[:300].sum() == 0
    out, p, _ = inject(frame, "overheating", 1.0, 300, 900, np.random.default_rng(0))
    diff = out["coolant_c"] - frame["coolant_c"]
    assert diff[p == 0].abs().max() == 0 and diff.max() == pytest.approx(15.0)
    assert "oil_pressure_drop" not in applicable_faults(["rpm", "coolant_c"])


def test_detector_flags_injected_overheating(company):
    _, _, df = company
    sig = ["rpm", "speed_kmh", "coolant_c", "battery_v", "fuel_level_pct"]
    det = GroupDetector(sig, far=0.01, n_folds=3, seed=0).fit(compute_windows(df[df["vehicle_id"] == "sim-01"], sig, 60, 10))
    frame = df[df["vehicle_id"] == "sim-02"].iloc[:3000].reset_index(drop=True)
    frame["vehicle_id"], frame["segment"] = "x", 0
    hot = frame.assign(coolant_c=frame["coolant_c"] + 20)
    clean, faulty = det.score(compute_windows(frame, sig, 60, 10)), det.score(compute_windows(hot, sig, 60, 10))
    assert faulty["alarm_coolant_c"].mean() > 0.9 and clean["alarm"].mean() < 0.2
    assert "acima do esperado" in det.explain(faulty.iloc[0])[0]
    assert list(time_block_folds(6, 3)) == [0, 0, 1, 1, 2, 2]
