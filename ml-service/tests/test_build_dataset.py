import pandas as pd
import pytest

from ml_service.pipeline.build_dataset import aggregate_seconds, resample_segments


def _write(tmp_path, lines):
    p = tmp_path / "log.txt"
    p.write_text("\r\n".join(lines) + "\r\n")
    return p


def test_aggregate_averages_within_second_and_prefers_sa0(tmp_path):
    p = _write(tmp_path, [
        "(100.1) can1 0CF00400#FFFFFF401FFFFFFF",   # 1000 rpm
        "(100.6) can1 0CF00400#FFFFFF803EFFFFFF",   # 2000 rpm -> média 1500
        "(100.7) can1 18FEF731#FFFFFFFF6400FFFF",   # bateria via SA 0x31: 5 V
        "(100.8) can1 18FEF700#FFFFFFFF9001FFFF",   # bateria via SA 0x00: 0x190*0.05 = 20 V
        "(101.2) can1 0CF00400#FFFFFF401FFFFFFF",
    ])
    df = aggregate_seconds(p)
    assert list(df["sec"]) == [100, 101]
    assert df.loc[0, "rpm"] == pytest.approx(1500)
    assert df.loc[0, "battery_v"] == pytest.approx(0x190 * 0.05)  # SA 0 vence o SA 0x31


def test_resample_fills_limited_and_splits_on_gap():
    df = pd.DataFrame({"sec": [0, 3, 500, 501], "rpm": [800.0, 900.0, 1000.0, 0.0]})
    from ml_service.decoder import SIGNALS
    for s in SIGNALS:
        if s.name not in df:
            df[s.name] = float("nan")
    out = resample_segments(df)
    assert out["segment"].nunique() == 2          # lacuna de 497 s separa os segmentos
    seg0 = out[out["segment"] == 0]
    assert list(seg0["rpm"]) == [800.0, 800.0, 800.0, 900.0]   # 1 Hz com ffill
    assert list(out["engine_on"]) == [True, True, True, True, True, False]
