"""Janelas deslizantes e features estatísticas, geradas a partir dos sinais disponíveis.

Por sinal (driver/response): média, desvio, mínimo, máximo e inclinação na janela.
Sinais acumulados (ex. nível de combustível): só a inclinação, ignorando saltos de reabastecimento.
Derivadas: fração parado, aceleração média absoluta, variação média de RPM e tempo desde a partida do motor.
Janelas com motor desligado ou com valores ausentes são descartadas.
"""

from __future__ import annotations

import numpy as np
import pandas as pd
from numpy.lib.stride_tricks import sliding_window_view

from ml_service.schema import by_role

WINDOW_META = ["vehicle_id", "vehicle_group", "segment", "start_idx", "ts_start"]
CONTEXT_FEATURES = ["idle_frac", "accel_abs_mean", "rpm_diff_abs_mean", "engine_on_s"]
ENGINE_ON_S_CAP = 3600.0
REFUEL_JUMP = 2.0  # salto por segundo (em unidades do sinal) tratado como reabastecimento
STATS = ("mean", "std", "min", "max", "slope")


def engine_on_seconds(engine_on: np.ndarray) -> np.ndarray:
    """Segundos desde que o motor foi ligado (0 na primeira amostra ligada; reinicia a cada desligamento)."""
    idx = np.arange(len(engine_on))
    last_off = np.maximum.accumulate(np.where(~engine_on, idx, -1))
    return (idx - last_off - 1).clip(min=0).astype(float)


def add_engine_on_s(df: pd.DataFrame) -> pd.DataFrame:
    """Adiciona a coluna `engine_on_s` por (veículo, segmento). Requer linhas em ordem de tempo."""
    df = df.sort_values(["vehicle_id", "segment", "ts"]).reset_index(drop=True)
    df["engine_on_s"] = np.concatenate([engine_on_seconds(g.to_numpy(dtype=bool))
                                        for _, g in df.groupby(["vehicle_id", "segment"], sort=False)["engine_on"]])
    return df


def segment_windows(seg: pd.DataFrame, signals: list[str], size: int, stride: int) -> pd.DataFrame:
    """Features das janelas válidas de um trecho contínuo a 1 Hz (índice 0..n-1)."""
    stat, cum = by_role(signals, "driver", "response"), by_role(signals, "cumulative")
    cols = [*stat, *cum]
    n = len(seg)
    if n < size or not cols:
        return pd.DataFrame()
    arr = np.array(seg[cols], dtype=float)  # cópia: é modificado abaixo
    for i, s in enumerate(cols):  # reabastecimento: remove saltos para cima em sinais acumulados
        if s in cum:
            jump = np.diff(arr[:, i], prepend=arr[0, i])
            arr[:, i] -= np.cumsum(np.where(jump > REFUEL_JUMP, jump, 0.0))
    view = sliding_window_view(arr, size, axis=0)[::stride]  # (m, k, size)
    on = sliding_window_view(seg["engine_on"].to_numpy(dtype=bool), size)[::stride].all(axis=1)
    valid = on & ~np.isnan(view).any(axis=(1, 2))
    view = view[valid]
    t = np.arange(size, dtype=float) - (size - 1) / 2
    tt = float((t * t).sum())
    starts = np.arange(0, n - size + 1, stride)[valid]
    run = (seg["engine_on_s"].to_numpy(dtype=float) if "engine_on_s" in seg.columns
           else engine_on_seconds(seg["engine_on"].to_numpy(dtype=bool)))
    out: dict[str, np.ndarray] = {"start_idx": starts, "engine_on_s": np.minimum(run[starts], ENGINE_ON_S_CAP)}
    for i, s in enumerate(cols):
        x = view[:, i, :]
        if s in stat:
            out[f"{s}_mean"], out[f"{s}_std"] = x.mean(axis=1), x.std(axis=1)
            out[f"{s}_min"], out[f"{s}_max"] = x.min(axis=1), x.max(axis=1)
        out[f"{s}_slope"] = (x * t).sum(axis=1) / tt
    if "speed_kmh" in stat:
        v = view[:, cols.index("speed_kmh"), :]
        out["idle_frac"] = (v < 1).mean(axis=1)
        out["accel_abs_mean"] = np.abs(np.diff(v, axis=1)).mean(axis=1)
    if "rpm" in stat:
        out["rpm_diff_abs_mean"] = np.abs(np.diff(view[:, cols.index("rpm"), :], axis=1)).mean(axis=1)
    return pd.DataFrame(out)


def compute_windows(df: pd.DataFrame, signals: list[str], size: int, stride: int) -> pd.DataFrame:
    """Janelas de todos os (veículo, segmento) de uma tabela canônica."""
    parts = []
    for (vid, seg_id), g in df.groupby(["vehicle_id", "segment"], sort=True):
        g = g.sort_values("ts").reset_index(drop=True)
        w = segment_windows(g, signals, size, stride)
        if w.empty:
            continue
        w.insert(0, "vehicle_id", vid)
        w.insert(1, "vehicle_group", g["vehicle_group"].iloc[0])
        w.insert(2, "segment", int(seg_id))
        w["ts_start"] = g["ts"].to_numpy()[w["start_idx"].to_numpy()]
        parts.append(w)
    if not parts:
        return pd.DataFrame(columns=WINDOW_META)
    out = pd.concat(parts, ignore_index=True)
    return out[[*WINDOW_META, *[c for c in out.columns if c not in WINDOW_META]]]


def feature_columns(w: pd.DataFrame) -> list[str]:
    return [c for c in w.columns if c not in WINDOW_META and c != "split"]


def assign_split(w: pd.DataFrame, test_segments: dict[str, set[int]], test_frac: float, gap: int) -> pd.Series:
    """'train' | 'test' | 'gap' por janela, sem vazamento temporal.

    - veículo com `test_segments`: esses segmentos inteiros são teste;
    - caso contrário: o trecho final (`test_frac`) das janelas do veículo, em ordem cronológica, é teste,
      e `gap` janelas na fronteira são descartadas (elas se sobrepõem ao treino).
    """
    split = pd.Series("train", index=w.index)
    for vid, g in w.groupby("vehicle_id", sort=False):
        if vid in test_segments:
            split[g.index[g["segment"].isin(test_segments[vid])]] = "test"
            continue
        idx = g.sort_values(["segment", "start_idx"]).index
        n_test = int(len(idx) * test_frac)
        if n_test == 0:
            continue
        split[idx[len(idx) - n_test:]] = "test"
        split[idx[max(0, len(idx) - n_test - gap): len(idx) - n_test]] = "gap"
    return split
