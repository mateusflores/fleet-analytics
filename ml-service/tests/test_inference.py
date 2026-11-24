import joblib
import numpy as np
import pytest

from ml_service.detector import GroupDetector
from ml_service.features.windows import add_engine_on_s, compute_windows
from ml_service.inference.engine import StreamEngine, iso, parse_ts
from ml_service.pipeline.build_dataset import resample_segments
from ml_service.synthetic.simulator_fleet import generate

SIG = ["rpm", "speed_kmh", "coolant_c", "battery_v", "fuel_level_pct"]
VID = "11111111-1111-1111-1111-111111111111"


@pytest.fixture(scope="module")
def setup(tmp_path_factory):
    raw = generate(vehicles=1, hours=2.5, seed=3)
    df = raw.rename(columns={"timestamp": "sec", "engineRpm": "rpm", "speedKmh": "speed_kmh", "engineTempC": "coolant_c",
                             "batteryVoltage": "battery_v", "fuelLevelPct": "fuel_level_pct"})
    df = resample_segments(df[["sec", *SIG]], SIG).assign(vehicle_id="v", vehicle_group="g")
    df = add_engine_on_s(df)
    det = GroupDetector(SIG, far=0.01, n_folds=3, seed=0, warmup_s=0).fit(compute_windows(df.iloc[:6000], SIG, 60, 10))
    d = tmp_path_factory.mktemp("models")
    (d / "co" / "v1").mkdir(parents=True)
    joblib.dump({"company": "co", "version": "v1", "window_size_s": 60, "window_stride_s": 10, "detectors": {"g": det}},
                d / "co" / "v1" / "model.joblib")
    (d / "co" / "LATEST").write_text("v1")
    return d, raw.iloc[6500:7400].reset_index(drop=True)


def _payloads(raw, hot_from=None):
    out = []
    for i, r in enumerate(raw.to_dict("records")):
        temp = r["engineTempC"] + (40 if hot_from is not None and i >= hot_from else 0)
        out.append({"vehicleId": VID, "timestamp": iso(1_800_000_000 + i), "engineRpm": int(r["engineRpm"]),
                    "speedKmh": r["speedKmh"], "engineTempC": temp, "batteryVoltage": r["batteryVoltage"],
                    "fuelLevelPct": r["fuelLevelPct"]})
    return out


def _run(models, payloads, **kw):
    eng = StreamEngine(models, {VID: {"company": "co", "group": "g"}}, **kw)
    return eng, [(i, e) for i, p in enumerate(payloads) for e in eng.on_message(p)]


def test_timestamp_formats():
    assert parse_ts("2027-01-15T08:00:00Z") == parse_ts(iso(parse_ts("2027-01-15T08:00:00Z"))) == 1800000000.0
    assert parse_ts(1800000000.5) == 1800000000.5


def test_normal_stream_is_quiet_and_fault_raises_events_with_cooldown(setup):
    models, raw = setup
    eng, events = _run(models, _payloads(raw))
    assert eng.stats["windows"] > 50 and len(events) <= 1
    eng, events = _run(models, _payloads(raw, hot_from=300))
    hot = [(i, e) for i, e in events if e["eventType"] == "ML_COOLANT_C"]
    i, e = hot[0]
    assert 300 < i <= 400 and "acima do esperado" in e["message"]
    assert e["vehicleId"] == VID and e["timestamp"].endswith("Z")
    sev = [x["severity"] for _, x in hot]
    assert "CRITICAL" in sev[:2]                           # agrava para CRITICAL sem esperar o cooldown
    crit = [j for j, x in hot if x["severity"] == "CRITICAL"]
    assert all(b - a >= 300 for a, b in zip(crit, crit[1:]))  # depois disso, só repete após o cooldown
    assert len(hot) <= 4


def test_unknown_vehicle_ignored_and_gap_resets_buffer(setup):
    models, raw = setup
    eng, _ = _run(models, [{**p, "vehicleId": "outro"} for p in _payloads(raw)[:100]])
    assert eng.stats["ignored"] == 100 and eng.stats["windows"] == 0
    p = _payloads(raw)[:150]
    for x in p[75:]:
        x["timestamp"] = iso(parse_ts(x["timestamp"]) + 600)   # lacuna de 10 min
    eng, _ = _run(models, p[:50] + p[100:])               # 50 s, lacuna, 50 s: nunca há 60 s contínuos
    assert eng.stats["windows"] == 0


def test_missing_model_signal_gives_no_detection(setup):
    models, raw = setup
    eng, events = _run(models, [{k: v for k, v in p.items() if k != "speedKmh"} for p in _payloads(raw, hot_from=100)])
    assert eng.stats["windows"] == 0 and not events


# ---------------------------------------------------------------------------------------------------------------
# robustez: mensagens ruins não derrubam o serviço

def test_malformed_values_and_timestamps_are_ignored_not_fatal(setup):
    models, raw = setup
    eng = StreamEngine(models, {VID: {"company": "co", "group": "g"}})
    good = _payloads(raw)[:5]
    assert eng.on_message({"vehicleId": VID, "timestamp": "not-a-date", "engineRpm": 900}) == []
    assert eng.on_message({"vehicleId": VID, "timestamp": None}) == []
    for p in good:
        eng.on_message({**p, "engineRpm": "abc", "speedKmh": float("nan"), "engineTempC": True})
    st = eng.streams[VID]
    assert "rpm" not in st.acc and "speed_kmh" not in st.acc and "coolant_c" not in st.acc
    assert "battery_v" in st.acc                               # os campos válidos continuam
    assert eng.stats["ignored"] == 2


def test_missing_company_model_or_group_disables_only_that_vehicle(setup, caplog):
    models, raw = setup
    eng = StreamEngine(models, {"a": {"company": "nope"}, "b": {"company": "co", "group": "ghost"},
                                VID: {"company": "co", "group": "g"}})
    p = _payloads(raw)[0]
    assert eng.on_message({**p, "vehicleId": "a"}) == []
    assert eng.on_message({**p, "vehicleId": "b"}) == []
    eng.on_message(p)
    assert VID in eng.streams and "a" not in eng.streams and "b" not in eng.streams
    assert eng.stats["ignored"] == 2


def test_consumer_callback_survives_engine_errors_and_publishes_events():
    from ml_service.inference.service import make_handler

    class Boom:
        def on_message(self, payload):
            if payload.get("vehicleId") == "bad":
                raise RuntimeError("bug")
            return [{"vehicleId": "ok", "eventType": "ML_OIL_KPA", "severity": "WARNING", "message": "m"}]

    class Channel:
        published = []

        def basic_publish(self, exchange, key, body, props):
            self.published.append((exchange, key, body))

    ch = Channel()
    handle = make_handler(Boom(), {"exchange": "amq.topic", "event_routing_key": "telemetry.event"})
    handle(ch, None, None, b"{not json")
    handle(ch, None, None, b"[1, 2]")
    handle(ch, None, None, b'{"vehicleId": "bad"}')
    handle(ch, None, None, b'{"vehicleId": "ok"}')
    assert [(e, k) for e, k, _ in ch.published] == [("amq.topic", "telemetry.event")]
