"""Frota sintética (Empresa B): porte offline das equações do `desktop-simulator`.

O simulador Java (TelemetryGenerator) só gera dados em tempo real; aqui as mesmas equações e constantes
são executadas a 1 Hz sobre um perfil de velocidade gerado (urbano / rodovia / paradas), produzindo um
histórico em CSV com os nomes de campo do simulador.

Uso: python -m ml_service.synthetic.simulator_fleet --out data/synthetic/empresa-b.csv
"""

from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np
import pandas as pd

# Constantes de desktop-simulator/.../generators/TelemetryGenerator.java
IDLE_RPM, MAX_RPM, RPM_PER_KMH, RPM_EMA_ALPHA = 700, 4000, 40.0, 0.3
BASE_TEMP_C, MAX_TEMP_DELTA_C, TEMP_EMA_ALPHA, COOLDOWN_RATE_C = 85.0, 35.0, 0.2, 0.02
FUEL_CONSUMPTION_FACTOR, NOMINAL_VOLTAGE = 0.00001, 13.8


def speed_profile(seconds: int, rng: np.random.Generator) -> np.ndarray:
    """Velocidade (km/h) a 1 Hz alternando trechos urbanos, de rodovia e paradas."""
    v, speed, t = np.zeros(seconds), 0.0, 0
    while t < seconds:
        mode = rng.choice(["urbano", "rodovia", "parado"], p=[0.45, 0.4, 0.15])
        if mode == "urbano":
            target, dur = rng.uniform(20, 55), int(rng.uniform(60, 400))
        elif mode == "rodovia":
            target, dur = rng.uniform(65, 82), int(rng.uniform(300, 1500))
        else:
            target, dur = 0.0, int(rng.uniform(30, 240))
        accel = rng.uniform(0.6, 1.6)
        for _ in range(min(dur, seconds - t)):
            speed += np.clip(target - speed, -2 * accel, accel)
            speed = max(0.0, speed + (rng.normal(0, 0.4) if target > 0 else 0.0)) if speed > 0.5 or target > 0 else 0.0
            v[t] = speed
            t += 1
    return v


def simulate_trip(speed: np.ndarray, odometer: float, fuel: float, rng: np.random.Generator) -> pd.DataFrame:
    n = len(speed)
    rpm, temp, fuel_l, odo, volt = np.empty(n), np.empty(n), np.empty(n), np.empty(n), np.empty(n)
    last_rpm, last_temp = float(IDLE_RPM), BASE_TEMP_C
    for i in range(n):
        target = np.clip(IDLE_RPM + round(speed[i] * RPM_PER_KMH) + int(rng.normal(0, 50)), IDLE_RPM, MAX_RPM)
        last_rpm = float(np.clip(round(RPM_EMA_ALPHA * target + (1 - RPM_EMA_ALPHA) * last_rpm), IDLE_RPM, MAX_RPM))
        t_target = BASE_TEMP_C + (last_rpm - IDLE_RPM) / (MAX_RPM - IDLE_RPM) * MAX_TEMP_DELTA_C + rng.normal(0, 1)
        last_temp = TEMP_EMA_ALPHA * t_target + (1 - TEMP_EMA_ALPHA) * last_temp
        if last_rpm <= IDLE_RPM + 50:
            last_temp -= COOLDOWN_RATE_C
        last_temp = max(BASE_TEMP_C - 5, last_temp)
        fuel = float(np.clip(fuel - last_rpm * FUEL_CONSUMPTION_FACTOR, 0, 100))
        if fuel < 8:  # reabastecimento (o simulador não modela; evita tanque vazio)
            fuel = 100.0
        odometer += speed[i] / 3600
        rpm[i], temp[i], fuel_l[i], odo[i] = last_rpm, last_temp, fuel, odometer
        volt[i] = np.clip(NOMINAL_VOLTAGE + rng.normal(0, 0.1), 11.0, 14.4)
    return pd.DataFrame({"speedKmh": speed, "engineRpm": rpm, "engineTempC": temp, "fuelLevelPct": fuel_l,
                         "odometerKm": odo, "batteryVoltage": volt})


def generate(vehicles: int, hours: float, seed: int) -> pd.DataFrame:
    rng = np.random.default_rng(seed)
    parts = []
    for v in range(vehicles):
        ts = pd.Timestamp("2026-10-01T08:00:00Z").timestamp() + v * 7
        odometer, fuel, remaining = rng.uniform(10_000, 100_000), 100.0, int(hours * 3600)
        while remaining > 0:
            n = min(remaining, int(rng.uniform(3600, 3 * 3600)))
            trip = simulate_trip(speed_profile(n, rng), odometer, fuel, rng)
            odometer, fuel = trip["odometerKm"].iloc[-1], trip["fuelLevelPct"].iloc[-1]
            trip.insert(0, "timestamp", (ts + np.arange(n)).astype("int64"))
            trip.insert(1, "vehicleId", f"sim-{v + 1:02d}")
            parts.append(trip)
            ts += n + int(rng.uniform(1800, 4 * 3600))  # intervalo entre viagens
            remaining -= n
    return pd.concat(parts, ignore_index=True).round(3)


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="data/synthetic/empresa-b.csv")
    ap.add_argument("--vehicles", type=int, default=3)
    ap.add_argument("--hours", type=float, default=8.0)
    ap.add_argument("--seed", type=int, default=7)
    a = ap.parse_args()
    df = generate(a.vehicles, a.hours, a.seed)
    Path(a.out).parent.mkdir(parents=True, exist_ok=True)
    df.to_csv(a.out, index=False)
    print(f"{a.out}: {len(df)} linhas, {df['vehicleId'].nunique()} veículos")
