"""Esquema canônico: tabela a 1 Hz com `ts, vehicle_id, vehicle_group, segment, engine_on` + sinais.

Papel de cada sinal:
- driver:     condição de operação (o que o motorista/rota impõem)
- response:   resposta do veículo, ligada à saúde (alvo dos modelos de valor esperado)
- cumulative: grandeza acumulada; só a inclinação na janela é usada
- state:      informativo, não entra nas features
"""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class SignalSpec:
    unit: str
    lo: float
    hi: float
    role: str
    label: str


SIGNALS: dict[str, SignalSpec] = {
    "rpm": SignalSpec("rpm", 0, 8000, "driver", "rotação do motor"),
    "speed_kmh": SignalSpec("km/h", 0, 250, "driver", "velocidade"),
    "load_pct": SignalSpec("%", 0, 125, "driver", "carga do motor"),
    "engine_torque_pct": SignalSpec("%", -125, 125, "driver", "torque do motor"),
    "pedal_pct": SignalSpec("%", 0, 100, "driver", "pedal do acelerador"),
    "gear": SignalSpec("", -5, 20, "driver", "marcha"),
    "coolant_c": SignalSpec("°C", -40, 210, "response", "temperatura do arrefecimento"),
    "oil_kpa": SignalSpec("kPa", 0, 1000, "response", "pressão do óleo"),
    "fuel_rate_lph": SignalSpec("L/h", 0, 3200, "response", "consumo de combustível"),
    "boost_kpa": SignalSpec("kPa", 0, 500, "response", "pressão do turbo"),
    "intake_c": SignalSpec("°C", -40, 210, "response", "temperatura de admissão"),
    "battery_v": SignalSpec("V", 0, 36, "response", "tensão da bateria"),
    "fuel_level_pct": SignalSpec("%", 0, 100, "cumulative", "nível de combustível"),
    "odometer_km": SignalSpec("km", 0, 1e7, "state", "odômetro"),
}

META = ["ts", "vehicle_id", "vehicle_group", "segment", "engine_on"]
REQUIRED = ["rpm"]


def by_role(signals: list[str], *roles: str) -> list[str]:
    return [s for s in signals if SIGNALS[s].role in roles]
