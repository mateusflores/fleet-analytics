"""Replay de dados históricos como telemetria em tempo real (simula o ESP32 com dados reais).

Lê um trecho dos dados de uma empresa, opcionalmente injeta uma falha e publica uma mensagem por segundo no
formato do `TelemetryMessage` (com campos extras para os demais sinais; a API ignora os que não conhece).
Os timestamps são rebaseados para o instante atual. `--speed` acelera o envio (os timestamps continuam
avançando 1 s por linha). `--dry-run` não usa o RabbitMQ: alimenta o motor de inferência diretamente.

Exemplo:
  python -m ml_service.tools.replay --config configs/empresa-a.yaml --vehicle t270-01 \\
      --vehicle-uuid <uuid do veículo na API> --segment 7 --start 3000 --duration 600 \\
      --fault overheating --fault-onset 120 --speed 10
"""

from __future__ import annotations

import argparse
import json
import math
import time

import numpy as np

from ml_service.config import load_config
from ml_service.inference.engine import REVERSE_FIELD_MAP, StreamEngine, iso
from ml_service.ingest import load_company_data
from ml_service.schema import SIGNALS
from ml_service.synthetic.faults import FAULTS, inject_single


def build_payloads(a) -> tuple[list[dict], str]:
    cfg = load_config(a.config)
    df = load_company_data(cfg)
    df = df[df["vehicle_id"] == a.vehicle]
    if a.segment is not None:
        df = df[df["segment"] == a.segment]
    frame = df.sort_values("ts").iloc[a.start:a.start + a.duration].reset_index(drop=True)
    if frame.empty:
        raise SystemExit("trecho vazio: confira --vehicle, --segment, --start e --duration")
    if a.fault:
        frame = inject_single(frame, a.fault, a.severity, a.fault_onset, a.fault_duration, np.random.default_rng(a.seed))
    t0 = math.floor(time.time())
    payloads = []
    for i, row in enumerate(frame.to_dict("records")):
        p = {"vehicleId": a.vehicle_uuid, "timestamp": iso(t0 + i)}
        for s in SIGNALS:
            v = row.get(s)
            if v is not None and v == v:
                p[REVERSE_FIELD_MAP.get(s, s)] = int(round(v)) if s == "rpm" else round(float(v), 3)
        payloads.append(p)
    return payloads, frame["vehicle_group"].iloc[0]


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--config", required=True, help="configuração da empresa (fonte dos dados)")
    ap.add_argument("--vehicle", required=True, help="vehicle_id nos dados")
    ap.add_argument("--vehicle-uuid", required=True, help="UUID do veículo na plataforma")
    ap.add_argument("--segment", type=int)
    ap.add_argument("--start", type=int, default=0, help="segundo inicial dentro do trecho")
    ap.add_argument("--duration", type=int, default=600)
    ap.add_argument("--fault", choices=sorted(FAULTS))
    ap.add_argument("--severity", type=float, default=1.0)
    ap.add_argument("--fault-onset", type=int, default=120)
    ap.add_argument("--fault-duration", type=int, default=300)
    ap.add_argument("--speed", type=float, default=1.0)
    ap.add_argument("--seed", type=int, default=0)
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--inference-config", default="configs/inference.yaml")
    a = ap.parse_args()
    payloads, group = build_payloads(a)

    if a.dry_run:
        company = load_config(a.config).company
        engine = StreamEngine("models", {a.vehicle_uuid: {"company": company, "group": group, "assume_warm": True}})
        for i, p in enumerate(payloads):
            for e in engine.on_message(p):
                print(f"t={i:4d}s  {e['eventType']} [{e['severity']}] {e['message']}")
        print("resumo:", engine.stats)
        return

    import pika
    from ml_service.inference.service import connect, load_settings
    cfg = load_settings(a.inference_config)
    channel = connect(cfg).channel()
    props = pika.BasicProperties(content_type="application/json", delivery_mode=1)
    for i, p in enumerate(payloads):
        channel.basic_publish(cfg["exchange"], "telemetry.telemetry", json.dumps(p).encode(), props)
        if i % 60 == 0:
            print(f"{i}/{len(payloads)} mensagens enviadas")
        time.sleep(1.0 / a.speed)
    print(f"{len(payloads)} mensagens enviadas")


if __name__ == "__main__":
    main()
