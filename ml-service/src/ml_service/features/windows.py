"""Janelas deslizantes e features estatísticas + split por segmento.

Split (decidido após a EDA, por segmento, sem vazamento temporal):
- treino/validação: CSU090BJ (todo) + cross-country segmentos 0 e 2
  (validação = últimos 15% de cada segmento de treino, separados por 1 janela de folga)
- teste: cross-country segmento 7 + segmentos curtos (1, 3, 4, 5, 6)

Uso:  python -m ml_service.features.windows
"""

from __future__ import annotations

from pathlib import Path

import numpy as np
import pandas as pd

WINDOW = 60  # segundos
STRIDE = 10
VAL_FRACTION = 0.15

CORE = ["rpm", "engine_torque_pct", "pedal_pct", "load_pct", "speed_kmh", "coolant_c",
        "oil_kpa", "fuel_rate_lph", "boost_kpa", "intake_c", "battery_v"]
SLOPE_SIGNALS = ["rpm", "load_pct", "speed_kmh", "coolant_c", "oil_kpa", "fuel_rate_lph",
                 "boost_kpa", "intake_c", "battery_v"]

TRAIN_SOURCES = {"bj": None, "cc": {0, 2}}  # None = todos os segmentos
CC_TEST_SEGMENTS = {1, 3, 4, 5, 6, 7}

DATA = Path("data")


def clean(df: pd.DataFrame) -> pd.DataFrame:
    """0% de combustível é artefato de inicialização do SA 0x31: trata como ausente."""
    df = df.copy()
    zero = df["fuel_level_pct"] == 0
    df.loc[zero, "fuel_level_pct"] = np.nan
    df["fuel_level_pct"] = (df.groupby("segment")["fuel_level_pct"]
                            .transform(lambda s: s.bfill(limit=60).ffill(limit=60)))
    return df


def window_features(arr: np.ndarray, cols: list[str]) -> dict[str, float]:
    """Features de uma janela (n x len(cols)) sem NaN."""
    idx = {c: i for i, c in enumerate(cols)}
    t = np.arange(arr.shape[0], dtype=float)
    t -= t.mean()
    out: dict[str, float] = {}
    for c in CORE:
        x = arr[:, idx[c]]
        out[f"{c}_mean"], out[f"{c}_std"] = x.mean(), x.std()
        out[f"{c}_min"], out[f"{c}_max"] = x.min(), x.max()
        if c in SLOPE_SIGNALS:
            out[f"{c}_slope"] = float((t * (x - x.mean())).sum() / (t * t).sum())
    speed, fuel = arr[:, idx["speed_kmh"]], arr[:, idx["fuel_rate_lph"]]
    out["idle_frac"] = float((speed < 1).mean())
    out["accel_abs_mean"] = float(np.abs(np.diff(speed)).mean())
    out["rpm_diff_abs_mean"] = float(np.abs(np.diff(arr[:, idx["rpm"]])).mean())
    out["fuel_economy_kmpl"] = float(speed.mean() / fuel.mean()) if fuel.mean() > 0.5 else np.nan
    out["gear_mean"] = float(arr[:, idx["gear"]].mean())
    return out


def segment_windows(seg: pd.DataFrame, source: str, seg_id: int) -> pd.DataFrame:
    cols = [*CORE, "gear"]
    arr = seg[cols].to_numpy(dtype=float)
    on = seg["engine_on"].to_numpy()
    ts = seg["ts"].to_numpy()
    rows = []
    for start in range(0, len(seg) - WINDOW + 1, STRIDE):
        w = arr[start:start + WINDOW]
        if not on[start:start + WINDOW].all() or np.isnan(w).any():
            continue
        row = {"source": source, "segment": seg_id, "ts_start": ts[start], "start_idx": start}
        row.update(window_features(w, cols))
        rows.append(row)
    return pd.DataFrame(rows)


def build_splits(processed: Path = DATA / "processed", out: Path = DATA / "features") -> dict[str, pd.DataFrame]:
    inputs = {
        "bj": clean(pd.read_parquet(processed / "CSU090BJ.parquet")),
        "cc": clean(pd.read_parquet(processed / "2014_KW_T270_cross_country_can_log.parquet")),
    }
    parts: dict[str, list[pd.DataFrame]] = {"train": [], "val": [], "test": []}
    for source, df in inputs.items():
        for seg_id, seg in df.groupby("segment"):
            w = segment_windows(seg.reset_index(drop=True), source, int(seg_id))
            if w.empty:
                continue
            if source == "cc" and seg_id in CC_TEST_SEGMENTS:
                parts["test"].append(w)
            elif TRAIN_SOURCES[source] is None or seg_id in TRAIN_SOURCES[source]:
                n_val = int(len(w) * VAL_FRACTION)
                gap = WINDOW // STRIDE  # descarta janelas que se sobrepõem ao treino
                parts["train"].append(w.iloc[: len(w) - n_val - gap])
                parts["val"].append(w.iloc[len(w) - n_val:])
    out.mkdir(parents=True, exist_ok=True)
    result = {k: pd.concat(v, ignore_index=True) for k, v in parts.items()}
    for k, d in result.items():
        d.to_parquet(out / f"{k}.parquet", index=False)
        print(f"{k:5s}: {len(d):6d} janelas  ({d.groupby(['source','segment']).size().to_dict()})")
    return result


if __name__ == "__main__":
    build_splits()
