import numpy as np
import pandas as pd
import pytest

from ml_service.features.windows import CORE, WINDOW, clean, segment_windows, window_features


def _frame(n, **over):
    d = {c: np.full(n, 1.0) for c in [*CORE, "gear"]}
    d.update(over)
    d.update(engine_on=np.ones(n, bool), segment=0, ts=pd.date_range("2020-01-01", periods=n, freq="s"))
    d["fuel_level_pct"] = np.full(n, 50.0)
    return pd.DataFrame(d)


def test_slope_of_linear_signal():
    cols = [*CORE, "gear"]
    arr = np.ones((WINDOW, len(cols)))
    arr[:, cols.index("coolant_c")] = 80 + 0.5 * np.arange(WINDOW)
    f = window_features(arr, cols)
    assert f["coolant_c_slope"] == pytest.approx(0.5)
    assert f["coolant_c_mean"] == pytest.approx(80 + 0.5 * (WINDOW - 1) / 2)


def test_windows_skip_engine_off_and_nan():
    df = _frame(WINDOW * 3)
    df.loc[10, "engine_on"] = False        # invalida as janelas que contêm a linha 10
    df.loc[150, "rpm"] = np.nan            # idem para a linha 150
    w = segment_windows(df, "t", 0)
    assert (w["start_idx"] > 10).all() and not ((w["start_idx"] <= 150) & (150 < w["start_idx"] + WINDOW)).any()


def test_clean_turns_startup_zero_into_nan_then_fills():
    df = _frame(100)
    df.loc[:4, "fuel_level_pct"] = 0.0
    assert (clean(df)["fuel_level_pct"] == 50.0).all()
