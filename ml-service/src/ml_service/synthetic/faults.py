"""Injeção de falhas sintéticas em dados reais a 1 Hz (para avaliar os detectores sem rótulos).

Cada evento dura `duration` s: a falha cresce em rampa linear na primeira metade e se mantém na segunda
(degradação gradual). `severity` escala a magnitude. Só são aplicáveis as falhas cujo sinal existe.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np
import pandas as pd

from ml_service.schema import SIGNALS


@dataclass(frozen=True)
class Fault:
    signal: str
    kind: str  # add | mul | noise | drain
    magnitude: float
    description: str


FAULTS: dict[str, Fault] = {
    "overheating": Fault("coolant_c", "add", 15.0, "arrefecimento sobe até +15 °C"),
    "battery_drain": Fault("battery_v", "add", -2.0, "tensão cai até -2,0 V"),
    "oil_pressure_drop": Fault("oil_kpa", "mul", -0.35, "pressão do óleo cai até -35%"),
    "fuel_overconsumption": Fault("fuel_rate_lph", "mul", 0.35, "consumo sobe até +35%"),
    "boost_leak": Fault("boost_kpa", "mul", -0.35, "pressão do turbo cai até -35%"),
    "erratic_rpm": Fault("rpm", "noise", 200.0, "ruído gaussiano no RPM (σ até 200 rpm)"),
    "fuel_leak": Fault("fuel_level_pct", "drain", 0.04, "nível de combustível perde 0,04 %/s"),
}


def applicable_faults(signals: list[str]) -> list[str]:
    return [name for name, f in FAULTS.items() if f.signal in signals]


def event_profile(n: int, duration: int, period: int) -> tuple[np.ndarray, list[tuple[int, int]]]:
    """Perfil 0..1 da intensidade da falha e lista de eventos (início, fim) em um trecho de n segundos."""
    profile, events = np.zeros(n), []
    onset = max(period // 2 - duration // 2, 0)
    ramp = np.minimum(1.0, (np.arange(duration) + 1) / (duration / 2))
    while onset + duration <= n:
        profile[onset:onset + duration] = ramp
        events.append((onset, onset + duration))
        onset += period
    return profile, events


def _apply(frame: pd.DataFrame, f: Fault, severity: float, profile: np.ndarray, events: list[tuple[int, int]],
           rng: np.random.Generator) -> pd.DataFrame:
    out = frame.copy()
    x = out[f.signal].to_numpy(dtype=float).copy()
    if f.kind == "add":
        x += severity * f.magnitude * profile
    elif f.kind == "mul":
        x *= 1 + severity * f.magnitude * profile
    elif f.kind == "noise":
        x += rng.normal(0, 1, len(x)) * severity * f.magnitude * profile
    elif f.kind == "drain":
        for onset, end in events:  # perda acumulada dentro de cada evento (não se propaga aos seguintes)
            x[onset:end] -= severity * f.magnitude * (np.arange(end - onset) + 1)
    spec = SIGNALS[f.signal]
    out[f.signal] = np.clip(x, spec.lo, spec.hi)
    return out


def inject(frame: pd.DataFrame, fault_name: str, severity: float, duration: int, period: int,
           rng: np.random.Generator) -> tuple[pd.DataFrame, np.ndarray, list[tuple[int, int]]]:
    """Eventos periódicos ao longo do trecho (avaliação)."""
    profile, events = event_profile(len(frame), duration, period)
    return _apply(frame, FAULTS[fault_name], severity, profile, events, rng), profile, events


def inject_single(frame: pd.DataFrame, fault_name: str, severity: float, onset: int, duration: int,
                  rng: np.random.Generator) -> pd.DataFrame:
    """Um único evento começando em `onset` (replay/demonstração)."""
    n = len(frame)
    end = min(onset + duration, n)
    profile = np.zeros(n)
    profile[onset:end] = np.minimum(1.0, (np.arange(end - onset) + 1) / (duration / 2))
    return _apply(frame, FAULTS[fault_name], severity, profile, [(onset, end)], rng)
