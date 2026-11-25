"""Cenários do histórico de demonstração → agenda diária (veículo × dia) e verdade conhecida.

A agenda é o que o `HistoryGenerator` (desktop-simulator) executa: quem dirige cada veículo no dia, estilo,
hábito de ociosidade, temperatura ambiente e intensidade de cada falha. Tudo é função pura de (configuração, data):
a mesma configuração gera sempre a mesma agenda, e cada mês pode ser gerado separadamente.
"""

from __future__ import annotations

import math
import zlib
from dataclasses import dataclass
from datetime import date, timedelta
from pathlib import Path

import numpy as np
import pandas as pd
import yaml

SCHEDULE_COLUMNS = ["date", "vehicle", "driver", "style", "idle_habit_s", "ambient_c", "faults", "seed", "odometer_km"]
KM_PER_OPERATING_DAY = 330.0   # média aproximada do simulador numa jornada (só para o odômetro ser coerente)


def _rng(*keys) -> np.random.Generator:
    return np.random.default_rng(zlib.crc32("|".join(map(str, keys)).encode()))


@dataclass(frozen=True)
class DemoConfig:
    raw: dict
    end: date

    @classmethod
    def load(cls, path: str | Path, end: date | None = None) -> "DemoConfig":
        return cls(yaml.safe_load(Path(path).read_text()), end or date.today() - timedelta(days=1))

    @property
    def seed(self) -> int:
        return int(self.raw["seed"])

    @property
    def start(self) -> date:
        return self.end - timedelta(days=int(self.raw["days"]) - 1)

    @property
    def live_start(self) -> date:
        return self.end - timedelta(days=int(self.raw.get("live_days", 14)) - 1)

    @property
    def vehicles(self) -> list[dict]:
        return self.raw["vehicles"]

    @property
    def drivers(self) -> list[dict]:
        return self.raw["drivers"]

    @property
    def scenarios(self) -> list[dict]:
        return self.raw.get("scenarios", [])

    def day(self, days_ago: int | None) -> date | None:
        return None if days_ago is None else self.end - timedelta(days=int(days_ago))


# ---------------------------------------------------------------------------------------------------------------
# estado de motoristas e veículos num dia

def driver_state(cfg: DemoConfig, driver: dict, day: date) -> tuple[str, float]:
    """(estilo, hábito de ociosidade em s) do motorista no dia, aplicando os cenários de motorista."""
    style, idle = driver["style"], float(driver["idle_habit_s"])
    for sc in cfg.scenarios:
        ds = sc.get("driver_style")
        if ds and ds["driver"] == driver["key"]:
            start = cfg.day(ds.get("start_days_ago"))
            if start is None or day >= start:
                style = ds["style"]
    return style, idle


def fault_intensity(cfg: DemoConfig, fault: dict, day: date) -> float:
    """Intensidade 0–1 da falha no dia: rampa linear de 0 a `peak` em `ramp_days`, zero após o conserto."""
    start = cfg.day(fault["start_days_ago"])
    end = cfg.day(fault.get("end_days_ago"))
    if day < start or (end is not None and day >= end):
        return 0.0
    ramp = max(int(fault.get("ramp_days", 0)), 0)
    progress = 1.0 if ramp == 0 else min(1.0, ((day - start).days + 1) / ramp)
    return round(float(fault["peak"]) * progress, 4)


def ambient_c(cfg: DemoConfig, day: date) -> float:
    c = cfg.raw["climate"]
    doy = day.timetuple().tm_yday
    mean = c["mean_c"] + c["amplitude_c"] * math.cos(2 * math.pi * (doy - c["peak_day_of_year"]) / 365.0)
    return round(mean + _rng(cfg.seed, "ambient", day).normal(0, c["daily_sd_c"]), 1)


def _operates(cfg: DemoConfig, prob: float, *keys) -> bool:
    return bool(_rng(cfg.seed, *keys).random() < prob)


def day_assignments(cfg: DemoConfig, day: date) -> list[tuple[dict, dict]]:
    """(veículo, motorista) que operam no dia. Motorista sorteado entre os disponíveis: cada um dirige vários
    veículos ao longo do ano e cada veículo tem vários motoristas (separa o efeito do motorista do veículo)."""
    op = cfg.raw["operation"]
    wd = day.weekday()
    prob = op["sunday"] if wd == 6 else op["saturday"] if wd == 5 else op["weekday"]
    vehicles = [v for v in cfg.vehicles if _operates(cfg, prob, "vehicle", v["key"], day)]
    drivers = [d for d in cfg.drivers if not _operates(cfg, op["driver_day_off"], "off", d["key"], day)]
    order = _rng(cfg.seed, "assign", day).permutation(len(drivers))
    return [(v, drivers[i]) for v, i in zip(vehicles, order)]


def schedule(cfg: DemoConfig, start: date | None = None, end: date | None = None) -> pd.DataFrame:
    """Agenda de `start` a `end` (inclusive), no formato do `HistoryGenerator`."""
    start, end = start or cfg.start, end or cfg.end
    faults = [sc["fault"] for sc in cfg.scenarios if "fault" in sc]
    base_odometer = {v["key"]: 80_000 + 40_000 * i for i, v in enumerate(cfg.vehicles)}
    rows = []
    for n in range((end - start).days + 1):
        day = start + timedelta(days=n)
        ambient = ambient_c(cfg, day)
        for vehicle, driver in day_assignments(cfg, day):
            style, idle = driver_state(cfg, driver, day)
            active = [(f["type"], fault_intensity(cfg, f, day)) for f in faults if f["vehicle"] == vehicle["key"]]
            spec = ";".join(f"{t}={x}" for t, x in active if x > 0)
            seed = zlib.crc32(f"{cfg.seed}|{vehicle['key']}|{day}".encode())
            odometer = base_odometer[vehicle["key"]] + (day - cfg.start).days * KM_PER_OPERATING_DAY * 0.8
            rows.append([day.isoformat(), vehicle["key"], driver["key"], style, idle, ambient, spec, seed,
                         round(odometer, 1)])
    return pd.DataFrame(rows, columns=SCHEDULE_COLUMNS)


# ---------------------------------------------------------------------------------------------------------------
# verdade conhecida

def ground_truth(cfg: DemoConfig) -> list[dict]:
    """Um item por insight esperado: cenário, categoria, assunto (chave do veículo/motorista), sinal e período em
    que a condição existe (`start`; `end` = conserto, ou None)."""
    out = []
    for sc in cfg.scenarios:
        if "fault" in sc:
            f = sc["fault"]
            subject, start, end = f["vehicle"], cfg.day(f["start_days_ago"]), cfg.day(f.get("end_days_ago"))
        else:
            spec = sc.get("driver_style") or sc.get("driver_idle")
            subject, start, end = spec["driver"], cfg.day(spec.get("start_days_ago")) or cfg.start, None
        for exp in sc["expect"]:
            out.append({"scenario": sc["id"], "title": sc["title"], "category": exp["category"], "subject": subject,
                        "signal": exp.get("signal"), "start": start.isoformat(),
                        "end": end.isoformat() if end else None, "resolves": bool(exp.get("resolves")),
                        "optional": bool(exp.get("optional"))})
    return out
