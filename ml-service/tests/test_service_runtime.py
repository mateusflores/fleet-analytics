"""Comportamentos de execução como serviço (compose): reconexão ao RabbitMQ e sobrescritas por ambiente."""

import pika
import pytest

from ml_service.inference import service
from ml_service.insights import run


def test_connect_retries_until_broker_is_up(monkeypatch):
    calls = []

    def fake_connection(params):
        calls.append(params)
        if len(calls) < 3:
            raise pika.exceptions.AMQPConnectionError("down")
        return "conn"

    monkeypatch.setattr(service.pika, "BlockingConnection", fake_connection)
    monkeypatch.setattr(service.time, "sleep", lambda s: None)
    cfg = service.load_settings("configs/inference.compose.yaml")
    assert service.connect(cfg, attempts=5) == "conn"
    assert len(calls) == 3


def test_connect_gives_up_after_attempts(monkeypatch):
    def down(params):
        raise pika.exceptions.AMQPConnectionError("down")

    monkeypatch.setattr(service.pika, "BlockingConnection", down)
    monkeypatch.setattr(service.time, "sleep", lambda s: None)
    with pytest.raises(pika.exceptions.AMQPConnectionError):
        service.connect(service.load_settings("configs/inference.compose.yaml"), attempts=2)


def test_insights_config_env_overrides(monkeypatch):
    monkeypatch.setenv("INSIGHTS_INTERVAL_S", "300")
    monkeypatch.setenv("INSIGHTS_INFERENCE_CONFIG", "configs/inference.yaml")
    cfg = run.load_config("configs/insights.compose.yaml")
    assert cfg["interval_s"] == 300 and cfg["inference_config"] == "configs/inference.yaml"
    assert cfg["retry_s"] == 60


def test_compose_mapping_has_default_vehicle():
    import yaml
    vehicles = yaml.safe_load(open("configs/inference.compose.yaml"))["vehicles"]
    assert vehicles["default"]["group"] == "caminhao-medio-sim"
    resolver = run.ModelResolver(__import__("pathlib").Path("models"), vehicles)
    detector, warm = resolver.get("4a0c1f9e-0000-0000-0000-000000000123")   # UUID qualquer
    assert detector is not None and warm is False
