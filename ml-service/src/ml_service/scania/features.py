"""Features por leitura (vehicle_id, time_step) do Scania Component X.

As colunas originais são contadores acumulados e histogramas acumulados (anonimizados).
Por isso as features principais são TAXAS e DISTRIBUIÇÕES:
- vida inteira: valor/tempo e proporção de cada faixa do histograma;
- recente: variação em relação a 1 e a 5 leituras atrás (taxa e distribuição do uso recente).
Reinícios de contador (queda do valor, ex. atualização de ECU) viram NaN.
"""

from __future__ import annotations

import numpy as np
import pandas as pd

HIST_BINS = {"167": 10, "272": 10, "291": 11, "158": 10, "459": 20, "397": 36}
LAGS = (1, 5)
SPEC_COLS = [f"Spec_{i}" for i in range(8)]


def split_columns(columns) -> tuple[list[str], dict[str, list[str]]]:
    counters, hists = [], {v: [] for v in HIST_BINS}
    for c in columns:
        if c in ("vehicle_id", "time_step"):
            continue
        var = c.split("_")[0]
        (hists[var] if var in HIST_BINS else counters).append(c)
    for var in hists:
        hists[var].sort(key=lambda c: int(c.split("_")[1]))
    return counters, hists


def _lag(arr: np.ndarray, vid: np.ndarray, k: int) -> np.ndarray:
    out = np.full_like(arr, np.nan)
    out[k:] = arr[:-k]
    same = np.zeros(len(vid), dtype=bool)
    same[k:] = vid[k:] == vid[:-k]
    out[~same] = np.nan
    return out


def _safe_div(a, b):
    with np.errstate(divide="ignore", invalid="ignore"):
        return np.where(b > 0, a / b, np.nan)


def build_features(ro: pd.DataFrame) -> pd.DataFrame:
    """`ro` deve estar ordenado por (vehicle_id, time_step). Linhas preservadas na mesma ordem."""
    counters, hists = split_columns(ro.columns)
    vid = ro["vehicle_id"].to_numpy()
    t = ro["time_step"].to_numpy(dtype=np.float64)
    lag_t = {k: _lag(t, vid, k) for k in LAGS}
    f: dict[str, np.ndarray] = {"time_step": t, "n_readouts": ro.groupby("vehicle_id").cumcount().to_numpy()}

    for c in counters:
        x = ro[c].to_numpy(dtype=np.float64)
        f[f"{c}_raw"] = x
        f[f"{c}_rate"] = _safe_div(x, t)
        for k in LAGS:
            d = x - _lag(x, vid, k)
            d[d < 0] = np.nan  # reinício do contador
            f[f"{c}_rate_l{k}"] = _safe_div(d, t - lag_t[k])

    for var, cols in hists.items():
        h = ro[cols].to_numpy(dtype=np.float64)
        total = h.sum(axis=1)
        f[f"h{var}_total"] = total
        f[f"h{var}_total_rate"] = _safe_div(total, t)
        life = h / np.where(total > 0, total, np.nan)[:, None]
        for b in range(h.shape[1]):
            f[f"h{var}_p{b}"] = life[:, b]
        for k in LAGS:
            d = h - np.stack([_lag(h[:, b], vid, k) for b in range(h.shape[1])], axis=1)
            d[(d < 0).any(axis=1)] = np.nan  # reinício
            dt_tot = d.sum(axis=1)
            f[f"h{var}_total_rate_l{k}"] = _safe_div(dt_tot, t - lag_t[k])
            if k == 5:  # distribuição do uso recente
                rec = d / np.where(dt_tot > 0, dt_tot, np.nan)[:, None]
                for b in range(h.shape[1]):
                    f[f"h{var}_rp{b}"] = rec[:, b]
    return pd.DataFrame({k: v.astype(np.float32) for k, v in f.items()})


def spec_frame(spec: pd.DataFrame, categories: dict[str, list[str]]) -> pd.DataFrame:
    out = spec[["vehicle_id"]].copy()
    for c in SPEC_COLS:
        out[c] = pd.Categorical(spec[c], categories=categories[c])
    return out


def spec_categories(train_spec: pd.DataFrame) -> dict[str, list[str]]:
    return {c: sorted(train_spec[c].dropna().unique()) for c in SPEC_COLS}
