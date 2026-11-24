"""Motor de inferência em tempo real (independente do broker).

Recebe mensagens de telemetria (dict JSON), mantém por veículo um buffer a 1 Hz com as mesmas regras do
treino (média por segundo, preenchimento limitado, novo segmento após lacuna > 60 s), calcula as features
da janela com o mesmo código do treino e aplica o detector da empresa do veículo.
Devolve eventos no formato do `EventMessage` da API.
"""

from __future__ import annotations

import logging
from collections import deque
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path

import joblib
import numpy as np
import pandas as pd

from ml_service.features.windows import ENGINE_ON_S_CAP, segment_windows
from ml_service.schema import SIGNALS

log = logging.getLogger("ml_service.inference")

# Nomes de campo das mensagens (API/simulador) -> sinais canônicos. Nomes canônicos também são aceitos.
FIELD_MAP = {
    "engineRpm": "rpm", "speedKmh": "speed_kmh", "engineTempC": "coolant_c", "batteryVoltage": "battery_v",
    "fuelLevelPct": "fuel_level_pct", "odometerKm": "odometer_km", "loadPct": "load_pct",
    "engineTorquePct": "engine_torque_pct", "pedalPct": "pedal_pct", "gear": "gear", "oilKpa": "oil_kpa",
    "fuelRateLph": "fuel_rate_lph", "boostKpa": "boost_kpa", "intakeC": "intake_c",
}
FIELD_MAP.update({s: s for s in SIGNALS})
REVERSE_FIELD_MAP = {v: k for k, v in FIELD_MAP.items() if k != v}

GAP_SECONDS, FFILL, SLOW_FFILL = 60, 5, 30
SLOW = {"fuel_level_pct", "odometer_km", "battery_v", "coolant_c"}


def parse_ts(value) -> float:
    """Epoch em segundos a partir de número (s) ou texto ISO-8601."""
    if isinstance(value, (int, float)):
        return float(value)
    return datetime.fromisoformat(str(value).replace("Z", "+00:00")).timestamp()


def iso(ts: float) -> str:
    return datetime.fromtimestamp(ts, tz=timezone.utc).isoformat().replace("+00:00", "Z")


def load_model(models_dir: Path, company: str) -> dict:
    version = (models_dir / company / "LATEST").read_text().strip()
    return joblib.load(models_dir / company / version / "model.joblib")


@dataclass
class VehicleStream:
    company: str
    group: str
    detector: object
    size: int
    stride: int
    assume_warm: bool = False
    rows: deque = field(default_factory=deque)
    cur_sec: int | None = None
    acc: dict = field(default_factory=dict)          # sinal -> [soma, contagem] do segundo corrente
    last: dict = field(default_factory=dict)         # sinal -> (valor, segundo) da última leitura
    trip_id: str | None = None
    engine_on_s: float = -1.0
    since_window: int = 0
    consecutive: int = 0
    last_emit: dict = field(default_factory=dict)
    warned_missing: bool = False

    def reset(self) -> None:
        self.rows.clear()
        self.acc, self.last, self.cur_sec = {}, {}, None
        self.engine_on_s, self.since_window, self.consecutive = -1.0, 0, 0

    def _append_row(self, sec: int, measured: dict) -> None:
        row = {"sec": sec}
        for s in SIGNALS:
            if s in measured:
                row[s] = measured[s]
            elif s in self.last and sec - self.last[s][1] <= (SLOW_FFILL if s in SLOW else FFILL):
                row[s] = self.last[s][0]
            else:
                row[s] = np.nan
        on = bool(row["rpm"] > 0)
        self.engine_on_s = (ENGINE_ON_S_CAP if self.assume_warm else self.engine_on_s + 1) if on else -1.0
        row["engine_on"], row["engine_on_s"] = on, max(self.engine_on_s, 0.0)
        self.rows.append(row)
        while len(self.rows) > self.size:
            self.rows.popleft()
        self.since_window += 1

    def close_second(self, next_sec: int) -> int:
        """Fecha o segundo corrente e preenche os segundos sem mensagem até `next_sec`. Devolve nº de linhas novas."""
        measured = {s: tot / n for s, (tot, n) in self.acc.items()}
        for s, v in measured.items():
            self.last[s] = (v, self.cur_sec)
        self._append_row(self.cur_sec, measured)
        added = 1
        for sec in range(self.cur_sec + 1, next_sec):
            self._append_row(sec, {})
            added += 1
        self.acc, self.cur_sec = {}, next_sec
        return added


class StreamEngine:
    def __init__(self, models_dir: str | Path, vehicles: dict[str, dict], min_consecutive: int = 2,
                 cooldown_s: int = 300):
        self.models_dir, self.vehicles = Path(models_dir), vehicles
        self.min_consecutive, self.cooldown_s = min_consecutive, cooldown_s
        self.models: dict[str, dict] = {}
        self.streams: dict[str, VehicleStream] = {}
        self.stats = {"messages": 0, "ignored": 0, "windows": 0, "alarms": 0, "events": 0}

    def _stream(self, vid: str) -> VehicleStream | None:
        if vid in self.streams:
            return self.streams[vid]
        info = self.vehicles.get(vid) or self.vehicles.get("default")
        if info is None:
            return None
        company = info["company"]
        if company not in self.models:
            try:
                self.models[company] = load_model(self.models_dir, company)
                log.info("modelo carregado: %s versão %s", company, self.models[company]["version"])
            except (FileNotFoundError, OSError) as e:
                log.error("modelo da empresa %s indisponível (%s): veículos dela ficam sem detecção", company, e)
                self.models[company] = None
        model = self.models[company]
        if model is None:
            return None
        group = info.get("group") or next(iter(model["detectors"]))
        if group not in model["detectors"]:
            log.error("grupo %s não existe no modelo da empresa %s: veículo %s sem detecção", group, company, vid)
            return None
        self.streams[vid] = VehicleStream(company, group, model["detectors"][group], model["window_size_s"],
                                          model["window_stride_s"], assume_warm=bool(info.get("assume_warm")))
        return self.streams[vid]

    def on_message(self, payload: dict) -> list[dict]:
        """Processa uma mensagem de telemetria ou de posição. Devolve os eventos gerados (0 ou mais)."""
        self.stats["messages"] += 1
        vid = str(payload.get("vehicleId"))
        st = self._stream(vid)
        if st is None or "timestamp" not in payload:
            self.stats["ignored"] += 1
            return []
        try:
            ts = parse_ts(payload["timestamp"])
        except (TypeError, ValueError):
            self.stats["ignored"] += 1
            return []
        sec = int(ts)
        st.trip_id = payload.get("tripId") or st.trip_id
        events: list[dict] = []
        if st.cur_sec is None:
            st.cur_sec = sec
        elif sec - st.cur_sec > GAP_SECONDS:
            st.reset()
            st.cur_sec = sec
        elif sec > st.cur_sec:
            st.close_second(sec)
            if len(st.rows) >= st.size and st.since_window >= st.stride:
                st.since_window = 0
                events = self._score(vid, st, float(st.rows[-1]["sec"]))
        for key, value in payload.items():
            signal = FIELD_MAP.get(key)
            if signal is None or value is None or isinstance(value, bool):
                continue
            try:
                number = float(value)
            except (TypeError, ValueError):
                continue  # valor não numérico: descarta só o campo
            if number != number or number in (float("inf"), float("-inf")):
                continue
            slot = st.acc.setdefault(signal, [0.0, 0])
            slot[0] += number
            slot[1] += 1
        return events

    def _score(self, vid: str, st: VehicleStream, ts: float) -> list[dict]:
        frame = pd.DataFrame(list(st.rows))
        det = st.detector
        w = segment_windows(frame, det.signals, st.size, st.size)
        if w.empty:
            missing = [s for s in det.signals if frame[s].isna().all()]
            if missing and not st.warned_missing:
                log.warning("veículo %s: sinais do modelo ausentes na telemetria: %s (sem detecção)", vid, missing)
                st.warned_missing = True
            st.consecutive = 0
            return []
        self.stats["windows"] += 1
        row = det.score(w).iloc[0]
        if not row["alarm"]:
            st.consecutive = 0
            return []
        self.stats["alarms"] += 1
        st.consecutive += 1
        if st.consecutive < self.min_consecutive:
            return []
        events = []
        for key, message, level in det.explain_items(row):
            event_type = f"ML_{key.upper()}"
            severity = "CRITICAL" if level >= 2 else "WARNING"
            last_ts, last_severity = st.last_emit.get(event_type, (-1e18, None))
            escalated = severity == "CRITICAL" and last_severity == "WARNING"
            if ts - last_ts < self.cooldown_s and not escalated:  # repete só após o cooldown ou se agravar
                continue
            st.last_emit[event_type] = (ts, severity)
            events.append({"vehicleId": vid, "tripId": st.trip_id, "timestamp": iso(ts), "eventType": event_type,
                           "severity": severity, "message": message})
        self.stats["events"] += len(events)
        return events
