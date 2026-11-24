"""Validação dos dados de uma empresa antes do treino: qualidade, cobertura e requisitos mínimos."""

from __future__ import annotations

import pandas as pd

from ml_service.config import Config
from ml_service.schema import REQUIRED, SIGNALS, by_role


def usable_signals(df: pd.DataFrame, min_valid: float) -> list[str]:
    """Sinais presentes e válidos em pelo menos `min_valid` do tempo com motor ligado."""
    on = df[df["engine_on"]]
    if on.empty:
        return []
    return [s for s in SIGNALS if s in df.columns and on[s].notna().mean() >= min_valid]


def quality_report(df: pd.DataFrame, cfg: Config) -> dict:
    rep: dict = {"errors": [], "warnings": [], "groups": {}}
    for group, g in df.groupby("vehicle_group"):
        on = g[g["engine_on"]]
        usable = usable_signals(g, cfg.min_valid_frac)
        info = {
            "vehicles": sorted(g["vehicle_id"].unique()),
            "hours_total": round(len(g) / 3600, 2),
            "hours_engine_on": round(len(on) / 3600, 2),
            "segments": int(g.groupby("vehicle_id")["segment"].nunique().sum()),
            "period": [str(g["ts"].min()), str(g["ts"].max())],
            "usable_signals": usable,
            "signals": {},
        }
        for s in SIGNALS:
            if s not in g.columns:
                continue
            x = on[s]
            spec = SIGNALS[s]
            info["signals"][s] = {
                "missing_pct": round(float(x.isna().mean() * 100), 2) if len(x) else 100.0,
                "out_of_range_pct": round(float(((x < spec.lo) | (x > spec.hi)).mean() * 100), 3) if len(x) else 0.0,
                "min": None if x.isna().all() else round(float(x.min()), 2),
                "mean": None if x.isna().all() else round(float(x.mean()), 2),
                "max": None if x.isna().all() else round(float(x.max()), 2),
            }
            if info["signals"][s]["out_of_range_pct"] > 1:
                rep["warnings"].append(f"[{group}] {s}: {info['signals'][s]['out_of_range_pct']}% fora da faixa física")
            if s not in usable and not x.isna().all():
                rep["warnings"].append(f"[{group}] {s} descartado: {info['signals'][s]['missing_pct']}% ausente")
        if "speed_kmh" in usable and len(on):
            v = on["speed_kmh"]
            info["operating_states_pct"] = {
                "parado (<1 km/h)": round(float((v < 1).mean() * 100), 1),
                "transição": round(float(((v >= 1) & (v < 80)).mean() * 100), 1),
                "rodovia (>=80 km/h)": round(float((v >= 80).mean() * 100), 1),
            }
            for state, pct in info["operating_states_pct"].items():
                if 0 < pct < 5:
                    rep["warnings"].append(f"[{group}] estado '{state}' pouco representado ({pct}% do tempo): "
                                           "mais falsos alarmes são esperados nele")
        for s in REQUIRED:
            if s not in usable:
                rep["errors"].append(f"[{group}] sinal obrigatório ausente ou inválido: {s}")
        if not by_role(usable, "response", "cumulative"):
            rep["errors"].append(f"[{group}] nenhum sinal de resposta utilizável (ex.: coolant_c, battery_v)")
        if info["hours_engine_on"] < cfg.min_engine_hours:
            rep["errors"].append(f"[{group}] {info['hours_engine_on']} h com motor ligado; mínimo {cfg.min_engine_hours} h")
        rep["groups"][group] = info
    rep["ok"] = not rep["errors"]
    return rep
