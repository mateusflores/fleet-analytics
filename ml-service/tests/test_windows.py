import numpy as np
import pandas as pd
import pytest

from ml_service.features.windows import assign_split, compute_windows, feature_columns, segment_windows

SIG = ["rpm", "speed_kmh", "coolant_c", "fuel_level_pct"]


def _frame(n, vid="v1", seg=0, **over):
    d = {"rpm": np.full(n, 1500.0), "speed_kmh": np.full(n, 60.0), "coolant_c": np.full(n, 90.0),
         "fuel_level_pct": np.full(n, 50.0)}
    d.update(over)
    return pd.DataFrame({**d, "engine_on": True, "segment": seg, "vehicle_id": vid, "vehicle_group": "g",
                         "ts": pd.date_range("2020-01-01", periods=n, freq="s", tz="UTC")})


def test_slope_mean_and_cumulative_only_slope():
    w = segment_windows(_frame(60, coolant_c=80 + 0.5 * np.arange(60), fuel_level_pct=50 - 0.1 * np.arange(60)), SIG, 60, 10)
    assert len(w) == 1
    assert w.loc[0, "coolant_c_slope"] == pytest.approx(0.5)
    assert w.loc[0, "coolant_c_mean"] == pytest.approx(80 + 0.5 * 59 / 2)
    assert w.loc[0, "fuel_level_pct_slope"] == pytest.approx(-0.1)
    assert "fuel_level_pct_mean" not in w.columns


def test_features_follow_available_signals():
    w = segment_windows(_frame(120), ["rpm", "coolant_c"], 60, 10)
    assert "idle_frac" not in w.columns and "rpm_diff_abs_mean" in w.columns
    assert not any(c.startswith("speed_kmh") for c in w.columns)


def test_windows_skip_engine_off_and_nan():
    df = _frame(180)
    df.loc[10, "engine_on"] = False
    df.loc[150, "rpm"] = np.nan
    s = segment_windows(df, SIG, 60, 10)["start_idx"]
    assert (s > 10).all() and not ((s <= 150) & (150 < s + 60)).any() and len(s) > 0


def test_split_by_segment_and_chronological_gap():
    df = pd.concat([_frame(600, "a", 0), _frame(600, "a", 1), _frame(1200, "b", 0)], ignore_index=True)
    w = compute_windows(df, SIG, 60, 10)
    split = assign_split(w, {"a": {1}}, test_frac=0.25, gap=6)
    a, b = w["vehicle_id"] == "a", w["vehicle_id"] == "b"
    assert (split[a & (w["segment"] == 1)] == "test").all() and (split[a & (w["segment"] == 0)] == "train").all()
    wb = w[b].assign(split=split[b])
    last_train = wb[wb["split"] == "train"]["start_idx"].max()
    first_test = wb[wb["split"] == "test"]["start_idx"].min()
    assert first_test - last_train >= 60          # nenhuma janela de treino se sobrepõe ao teste
    assert (wb["split"] == "gap").sum() == 6
    assert "split" not in feature_columns(wb)


def test_engine_on_seconds_resets_after_engine_off():
    from ml_service.features.windows import engine_on_seconds
    on = np.array([False, True, True, True, False, True, True])
    assert list(engine_on_seconds(on)) == [0, 0, 1, 2, 0, 0, 1]


def test_warmup_windows_are_out_of_scope():
    from ml_service.detector import GroupDetector
    rng = np.random.default_rng(0)
    df = _frame(4000, rpm=1500 + rng.normal(0, 30, 4000), speed_kmh=60 + rng.normal(0, 2, 4000),
                coolant_c=90 + rng.normal(0, 0.5, 4000))
    w = compute_windows(df, SIG, 60, 10)
    det = GroupDetector(SIG, far=0.05, n_folds=3, seed=0, warmup_s=600).fit(w)
    s = det.score(w)
    assert not s.loc[w["engine_on_s"] < 600, "scored"].any() and not s.loc[~s["scored"], "alarm"].any()
    assert det.n_train == int((w["engine_on_s"] >= 600).sum())
    assert s.loc[s["scored"], "alarm"].mean() < 0.15


def test_refuel_jump_does_not_affect_fuel_slope():
    fuel = 50 - 0.02 * np.arange(120)
    fuel[60:] += 40                                  # reabastecimento em degrau
    w = segment_windows(_frame(120, fuel_level_pct=fuel), SIG, 60, 10)
    assert np.allclose(w["fuel_level_pct_slope"], -0.02, atol=2e-3)   # sem o filtro a inclinação seria ~ +0,5
