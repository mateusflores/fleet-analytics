"""Métricas de uma viagem a partir da telemetria a 1 Hz.

Função pura: recebe as leituras da viagem (nomes de campo da API ou canônicos), a política da frota e,
opcionalmente, o detector do grupo do veículo (modelo de valor esperado). Devolve um dicionário com as colunas de
`telemetry.trip_metrics`.

Convenções (documentadas em docs/plano-insights-historicos.md):
- a série é levada a 1 Hz com as mesmas regras do treino (média por segundo, preenchimento limitado, novo trecho
  após lacuna > 60 s); cada segundo com motor ligado conta 1 s;
- "dirigindo" = velocidade ≥ 1 km/h; "parado" = motor ligado e velocidade < 1 km/h;
- ociosidade excessiva = trechos parados contínuos com duração > `idle_threshold_s` (conta o trecho inteiro);
- aceleração/frenagem brusca = episódios (bordas de subida) em que a variação de velocidade em 1 s passa do limiar;
- consumo esperado e saúde: janelas de 60 s sem sobreposição, fora do aquecimento, comparando o valor real com o
  previsto pelo modelo para as mesmas condições de operação.
"""

from __future__ import annotations

import numpy as np
import pandas as pd

from ml_service.features.windows import ENGINE_ON_S_CAP, add_engine_on_s, segment_windows
from ml_service.inference.engine import FIELD_MAP
from ml_service.insights.policy import FleetPolicy
from ml_service.pipeline.build_dataset import resample_segments
from ml_service.schema import SIGNALS

MOVING_KMH = 1.0
HEALTH_SIGNALS = ("oil_kpa", "coolant_c", "battery_v", "boost_kpa", "intake_c")


def to_canonical_seconds(raw: pd.DataFrame, assume_warm: bool = False) -> pd.DataFrame:
    """Leituras (coluna `time` + sinais) → grade de 1 s com `segment`, `engine_on` e `engine_on_s`.
    `assume_warm`: trata o motor como já aquecido (mesma opção do serviço de inferência)."""
    df = raw.rename(columns={k: v for k, v in FIELD_MAP.items() if k in raw.columns and k != v})
    signals = [s for s in SIGNALS if s in df.columns]
    if "rpm" not in signals or df.empty:
        return pd.DataFrame()
    t = pd.to_datetime(df["time"], utc=True)
    # independe da resolução da coluna (o pandas 3 usa µs ou ns conforme a origem)
    df = df.assign(sec=((t - pd.Timestamp(0, tz="UTC")) // pd.Timedelta(seconds=1)).astype("int64"))
    df[signals] = df[signals].apply(pd.to_numeric, errors="coerce")
    per_sec = df.groupby("sec", as_index=False)[signals].mean()
    out = resample_segments(per_sec, signals)
    out["vehicle_id"] = "trip"
    out = add_engine_on_s(out).drop(columns="vehicle_id")
    if assume_warm:
        out["engine_on_s"] = np.where(out["engine_on"], ENGINE_ON_S_CAP, 0.0)
    return out


def _runs(mask: np.ndarray, segment: np.ndarray) -> list[int]:
    """Comprimentos dos trechos contínuos de `mask` (sem atravessar troca de segmento)."""
    lengths, cur = [], 0
    for i, m in enumerate(mask):
        if m and (cur == 0 or segment[i] == segment[i - 1]):
            cur += 1
        elif m:
            lengths.append(cur)
            cur = 1
        else:
            if cur:
                lengths.append(cur)
            cur = 0
    if cur:
        lengths.append(cur)
    return lengths


def _episodes(flag: np.ndarray, segment: np.ndarray) -> int:
    """Número de episódios (bordas de subida) de `flag`."""
    if len(flag) == 0:
        return 0
    prev = np.concatenate([[False], flag[:-1]])
    new_seg = np.concatenate([[True], segment[1:] != segment[:-1]])
    return int((flag & (~prev | new_seg)).sum())


def _expected(sec: pd.DataFrame, detector, window_s: int) -> dict:
    """Consumo e sinais de saúde: real × esperado pelo modelo, em janelas de `window_s` fora do aquecimento."""
    if detector is None:
        return {}
    parts = []
    for _, seg in sec.groupby("segment", sort=True):
        w = segment_windows(seg.reset_index(drop=True), detector.signals, window_s, window_s)
        if not w.empty:
            parts.append(w)
    if not parts:
        return {"windows": 0}
    w = pd.concat(parts, ignore_index=True)
    scores = detector.score(w)
    scored = scores["scored"].to_numpy(bool)
    if not scored.any():
        return {"windows": 0}
    out: dict = {"windows": int(scored.sum())}
    hours = window_s / 3600
    if "fuel_rate_lph" in detector.ev:
        real = w.loc[scored, "fuel_rate_lph_mean"].to_numpy(float)
        resid = scores.loc[scored, "resid_fuel_rate_lph"].to_numpy(float)
        out["fuel_real_l"] = float(real.sum() * hours)
        out["fuel_expected_l"] = float((real - resid).sum() * hours)
    signals = {}
    for s in HEALTH_SIGNALS:
        if s in detector.ev and f"{s}_mean" in w.columns:
            real = w.loc[scored, f"{s}_mean"].to_numpy(float)
            resid = scores.loc[scored, f"resid_{s}"].to_numpy(float)
            signals[s] = {"actual": float(real.mean()), "expected": float((real - resid).mean())}
    out["signals"] = signals
    return out


def compute_trip_metrics(raw: pd.DataFrame, policy: FleetPolicy, vehicle_type: str | None = None,
                         events: pd.DataFrame | None = None, detector=None, window_s: int = 60,
                         assume_warm: bool = False) -> dict:
    """Colunas de `telemetry.trip_metrics` para uma viagem (sem as chaves de identificação)."""
    sec = to_canonical_seconds(raw, assume_warm)
    metrics = {
        "engine_on_s": 0, "driving_s": 0, "idle_s": 0, "excessive_idle_s": 0, "distance_km": 0.0,
        "fuel_used_l": None, "fuel_expected_l": None, "idle_fuel_l": None, "avg_speed_kmh": None,
        "max_speed_kmh": None, "avg_load_pct": None, "speeding_s": 0, "harsh_accel_count": 0,
        "harsh_brake_count": 0, "rpm_over_s": 0, "events_warning": 0, "events_critical": 0, "events_ml": 0,
        "health": {}, "policy": policy.snapshot(vehicle_type),
    }
    if events is not None and not events.empty:
        ml = events["event_type"].astype(str).str.startswith("ML_")
        metrics["events_ml"] = int(ml.sum())
        metrics["events_warning"] = int(((events["severity"] == "WARNING") & ~ml).sum())
        metrics["events_critical"] = int(((events["severity"] == "CRITICAL") & ~ml).sum())
    if sec.empty:
        return metrics

    on = sec["engine_on"].to_numpy(bool)
    segment = sec["segment"].to_numpy()
    speed = sec["speed_kmh"].to_numpy(float) if "speed_kmh" in sec else np.full(len(sec), np.nan)
    has_speed = ~np.isnan(speed)
    moving = on & has_speed & (speed >= MOVING_KMH)
    idle = on & has_speed & (speed < MOVING_KMH)

    metrics["engine_on_s"] = int(on.sum())
    metrics["driving_s"] = int(moving.sum())
    metrics["idle_s"] = int(idle.sum())
    metrics["excessive_idle_s"] = int(sum(n for n in _runs(idle, segment) if n > policy.idle_threshold_s))

    if moving.any():
        metrics["avg_speed_kmh"] = float(speed[moving].mean())
        metrics["max_speed_kmh"] = float(speed[moving].max())
    metrics["speeding_s"] = int((on & has_speed & (speed > policy.speed_limit_kmh)).sum())

    # distância: odômetro por trecho quando disponível e coerente; senão, integral da velocidade
    dist_speed = float(np.nansum(np.where(on, speed, 0.0)) / 3600)
    dist_odo = None
    if "odometer_km" in sec:
        span = sec.groupby("segment")["odometer_km"].agg(lambda s: s.max() - s.min())
        if span.notna().all():
            dist_odo = float(span.sum())
    metrics["distance_km"] = dist_odo if dist_odo is not None and dist_odo >= 0.5 * dist_speed else dist_speed

    # direção brusca: variação de velocidade em 1 s dentro do mesmo trecho
    accel = np.diff(speed, prepend=np.nan) / 3.6
    accel[np.concatenate([[True], segment[1:] != segment[:-1]])] = np.nan
    accel = np.nan_to_num(accel, nan=0.0)
    metrics["harsh_accel_count"] = _episodes(on & (accel >= policy.harsh_accel_ms2), segment)
    metrics["harsh_brake_count"] = _episodes(on & (accel <= -policy.harsh_brake_ms2), segment)

    rpm = sec["rpm"].to_numpy(float)
    metrics["rpm_over_s"] = int((on & (rpm > policy.rpm_max_for(vehicle_type))).sum())
    if "load_pct" in sec and on.any():
        load = sec.loc[on, "load_pct"]
        metrics["avg_load_pct"] = float(load.mean()) if load.notna().any() else None

    if "fuel_rate_lph" in sec and sec.loc[on, "fuel_rate_lph"].notna().any():
        fr = np.nan_to_num(sec["fuel_rate_lph"].to_numpy(float), nan=0.0)
        metrics["fuel_used_l"] = float(fr[on].sum() / 3600)
        metrics["idle_fuel_l"] = float(fr[idle].sum() / 3600)

    health = _expected(sec, detector, window_s)
    if "fuel_expected_l" in health:
        metrics["fuel_expected_l"] = health["fuel_expected_l"]
    metrics["health"] = health
    return metrics
