"""Serviço de inferência: consome a telemetria do RabbitMQ e publica eventos de ML para a API.

- Entrada: exchange `amq.topic`, chaves `telemetry.telemetry` e `telemetry.position` (as mesmas mensagens que a
  API consome; o serviço usa uma fila própria, exclusiva, e não interfere nas filas da API).
- Saída: `amq.topic` com a chave `telemetry.event`, no formato do `EventMessage` — a API persiste e envia por SSE.

Uso: python -m ml_service.inference.service --config configs/inference.yaml
"""

from __future__ import annotations

import argparse
import json
import logging
import os
import time
from pathlib import Path

import pika
import yaml

from ml_service.inference.engine import StreamEngine

log = logging.getLogger("ml_service.inference")


def load_settings(path: str | Path) -> dict:
    cfg = yaml.safe_load(Path(path).read_text())
    rmq = cfg.setdefault("rabbitmq", {})
    rmq["host"] = os.environ.get("RABBITMQ_HOST", rmq.get("host", "localhost"))
    rmq["port"] = int(os.environ.get("RABBITMQ_PORT", rmq.get("port", 5672)))
    rmq["user"] = os.environ.get("RABBITMQ_USER", rmq.get("user", "admin"))
    rmq["password"] = os.environ.get("RABBITMQ_PASSWORD", rmq.get("password", "admin"))
    cfg.setdefault("exchange", "amq.topic")
    cfg.setdefault("input_routing_keys", ["telemetry.telemetry", "telemetry.position"])
    cfg.setdefault("event_routing_key", "telemetry.event")
    return cfg


def connect(cfg: dict, attempts: int = 10, first_wait_s: float = 2.0) -> pika.BlockingConnection:
    """Conecta ao RabbitMQ, tentando de novo com espera crescente (o broker pode ainda estar subindo)."""
    r = cfg["rabbitmq"]
    params = pika.ConnectionParameters(host=r["host"], port=r["port"],
                                       credentials=pika.PlainCredentials(r["user"], r["password"]), heartbeat=30)
    wait = first_wait_s
    for attempt in range(1, attempts + 1):
        try:
            return pika.BlockingConnection(params)
        except pika.exceptions.AMQPConnectionError:
            if attempt == attempts:
                raise
            log.warning("RabbitMQ indisponível em %s:%s (tentativa %d/%d); nova tentativa em %.0f s",
                        r["host"], r["port"], attempt, attempts, wait)
            time.sleep(wait)
            wait = min(wait * 2, 30)
    raise AssertionError("inalcançável")


def build_engine(cfg: dict) -> StreamEngine:
    return StreamEngine(cfg.get("models_dir", "models"), cfg.get("vehicles", {}),
                        min_consecutive=cfg.get("min_consecutive", 2), cooldown_s=cfg.get("cooldown_s", 300))


def make_handler(engine: StreamEngine, cfg: dict):
    """Callback do consumidor: decodifica, processa e publica os eventos. Nunca propaga exceções de uma mensagem."""
    props = pika.BasicProperties(content_type="application/json", delivery_mode=2)

    def handle(ch, method, properties, body):
        try:
            payload = json.loads(body)
        except ValueError:
            log.warning("mensagem inválida descartada (%d bytes)", len(body))
            return
        if not isinstance(payload, dict):
            log.warning("mensagem sem objeto JSON descartada")
            return
        try:
            events = engine.on_message(payload)
        except Exception:
            # uma mensagem ruim não pode derrubar o consumidor (e perder o estado de todos os veículos)
            log.exception("falha ao processar mensagem do veículo %s; descartada", payload.get("vehicleId"))
            return
        for event in events:
            ch.basic_publish(cfg["exchange"], cfg["event_routing_key"], json.dumps(event, ensure_ascii=False).encode(), props)
            log.info("evento %s [%s] veículo %s: %s", event["eventType"], event["severity"], event["vehicleId"], event["message"])

    return handle


def run(config_path: str) -> None:
    cfg = load_settings(config_path)
    engine = build_engine(cfg)
    channel = connect(cfg).channel()
    queue = channel.queue_declare(queue="", exclusive=True, auto_delete=True).method.queue
    for key in cfg["input_routing_keys"]:
        channel.queue_bind(queue=queue, exchange=cfg["exchange"], routing_key=key)
    handle = make_handler(engine, cfg)

    channel.basic_consume(queue=queue, on_message_callback=handle, auto_ack=True)
    log.info("aguardando telemetria em %s (%s)", cfg["exchange"], ", ".join(cfg["input_routing_keys"]))
    try:
        channel.start_consuming()
    except KeyboardInterrupt:
        log.info("encerrado: %s", engine.stats)


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    logging.getLogger("pika").setLevel(logging.WARNING)
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--config", default="configs/inference.yaml")
    run(ap.parse_args().config)
