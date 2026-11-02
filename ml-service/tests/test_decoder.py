import pytest

from ml_service.decoder import decode_frame, parse_line, pgn_from_id


def test_pgn_pdu2_broadcast():
    # 18FEF031 -> PGN 0xFEF0 (65264), SA 0x31
    assert pgn_from_id(0x18FEF031) == (0xFEF0, 0x31, None)


def test_pgn_eec1_from_engine():
    pgn, sa, da = pgn_from_id(0x0CF00400)
    assert (pgn, sa, da) == (61444, 0x00, None)


def test_pgn_pdu1_ignores_destination():
    # 18EBFF00 = TP.DT (0xEB00) enviado por SA 0 para destino 0xFF
    assert pgn_from_id(0x18EBFF00) == (0xEB00, 0x00, 0xFF)


def test_parse_line_handles_crlf_and_fields():
    f = parse_line("(1594703731.591373) can1 0CF00400#0E7D7D0000000F7D\r\n")
    assert f is not None
    assert f.ts == pytest.approx(1594703731.591373)
    assert f.priority == 3 and f.pgn == 61444 and f.sa == 0
    assert f.data == bytes.fromhex("0E7D7D0000000F7D")


@pytest.mark.parametrize(
    "line",
    ["", "garbage", "(1.0) can1 0000033F#9B", "(1.0) can1 18FEF031#ABC", "(1.0) can1 18FEF031"],
)
def test_parse_line_rejects_invalid(line):
    assert parse_line(line) is None


def test_engine_off_frame_is_zero():
    # amostra real do log de partida: motor desligado
    f = parse_line("(1594703731.591373) can1 0CF00400#0E7D7D0000000F7D")
    vals = dict(decode_frame(f))
    assert vals["rpm"] == 0
    assert vals["engine_torque_pct"] == 0


def test_eec2_real_sample():
    f = parse_line("(1594702669.675093) can1 0CF00300#D10000FFFF0F6A7D")
    vals = dict(decode_frame(f))
    assert vals["pedal_pct"] == 0 and vals["load_pct"] == 0


def test_rpm_scaling():
    # raw 8000 (0x1F40) * 0.125 = 1000 rpm; torque byte 0xFF = não disponível
    f = parse_line("(1.0) can1 0CF00400#FFFFFF401FFFFFFF")
    vals = dict(decode_frame(f))
    assert vals["rpm"] == pytest.approx(1000)
    assert "engine_torque_pct" not in vals


def test_coolant_and_speed():
    f = parse_line("(1.0) can1 18FEEE00#7DFFFFFFFFFFFFFF")  # 0x7D - 40 = 85 °C
    assert dict(decode_frame(f))["coolant_c"] == 85
    f = parse_line("(1.0) can1 18FEF100#FF00640000FFFFFF")  # 0x6400/256 = 100 km/h
    assert dict(decode_frame(f))["speed_kmh"] == pytest.approx(100)


def test_unknown_pgn_returns_empty():
    assert decode_frame(parse_line("(1.0) can1 18F0FF00#0000000000000000")) == []
