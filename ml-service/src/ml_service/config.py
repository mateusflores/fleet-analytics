"""Configuração de treinamento por empresa (YAML). Ver configs/*.yaml."""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path

import yaml


@dataclass
class Source:
    adapter: str
    path: str
    vehicle_group: str = "default"
    vehicle_id: str | None = None
    test_segments: list[int] | None = None
    options: dict = field(default_factory=dict)


@dataclass
class Config:
    company: str
    sources: list[Source]
    window_size_s: int = 60
    window_stride_s: int = 10
    test_frac: float = 0.25
    target_false_alarm_rate: float = 0.01
    min_valid_frac: float = 0.8
    min_engine_hours: float = 2.0
    cv_folds: int = 5
    warmup_s: int = 1200
    fault_duration_s: int = 300
    fault_period_s: int = 900
    fault_severities: list[float] = field(default_factory=lambda: [0.25, 0.5, 1.0])
    seed: int = 42
    raw: dict = field(default_factory=dict)


_SOURCE_KEYS = {"adapter", "path", "vehicle_group", "vehicle_id", "test_segments"}


def load_config(path: str | Path) -> Config:
    raw = yaml.safe_load(Path(path).read_text())
    sources = [Source(**{k: v for k, v in s.items() if k in _SOURCE_KEYS},
                      options={k: v for k, v in s.items() if k not in _SOURCE_KEYS})
               for s in raw["sources"]]
    win, faults = raw.get("window", {}), raw.get("faults", {})
    cfg = Config(company=raw["company"], sources=sources, raw=raw)
    cfg.window_size_s = win.get("size_s", cfg.window_size_s)
    cfg.window_stride_s = win.get("stride_s", cfg.window_stride_s)
    cfg.fault_duration_s = faults.get("duration_s", cfg.fault_duration_s)
    cfg.fault_period_s = faults.get("period_s", cfg.fault_period_s)
    cfg.fault_severities = faults.get("severities", cfg.fault_severities)
    for key in ("test_frac", "target_false_alarm_rate", "min_valid_frac", "min_engine_hours", "cv_folds", "warmup_s", "seed"):
        if key in raw:
            setattr(cfg, key, raw[key])
    return cfg
