"""Tabela de SPNs (SAE J1939-71) usados pelo projeto e função de decodificação."""

from __future__ import annotations

from dataclasses import dataclass

from .candump_parser import Frame


@dataclass(frozen=True, slots=True)
class Signal:
    name: str
    pgn: int
    start_byte: int  # 1-based, como na norma
    length: int  # bytes (little-endian)
    scale: float = 1.0
    offset: float = 0.0
    spn: int | None = None

    def decode(self, data: bytes) -> float | None:
        """Valor físico, ou None se ausente / 'não disponível' / erro (>= 0xFB..)."""
        start = self.start_byte - 1
        end = start + self.length
        if len(data) < end:
            return None
        raw = int.from_bytes(data[start:end], "little")
        # Faixa 0xFB.. - 0xFF.. é reservada: erro / não disponível (J1939-71).
        if raw >= 0xFB << (8 * (self.length - 1)):
            return None
        return raw * self.scale + self.offset


SIGNALS: tuple[Signal, ...] = (
    # EEC1 - 61444 (0xF004)
    Signal("engine_torque_pct", 61444, 3, 1, 1, -125, spn=513),
    Signal("rpm", 61444, 4, 2, 0.125, spn=190),
    # EEC2 - 61443 (0xF003)
    Signal("pedal_pct", 61443, 2, 1, 0.4, spn=91),
    Signal("load_pct", 61443, 3, 1, 1, spn=92),
    # ETC2 - 61445 (0xF005)
    Signal("gear", 61445, 4, 1, 1, -125, spn=523),
    # ET1 - 65262 (0xFEEE)
    Signal("coolant_c", 65262, 1, 1, 1, -40, spn=110),
    # EFL/P1 - 65263 (0xFEEF)
    Signal("oil_kpa", 65263, 4, 1, 4, spn=100),
    # CCVS - 65265 (0xFEF1)
    Signal("speed_kmh", 65265, 2, 2, 1 / 256, spn=84),
    # LFE - 65266 (0xFEF2)
    Signal("fuel_rate_lph", 65266, 1, 2, 0.05, spn=183),
    # IC1 - 65270 (0xFEF6)
    Signal("boost_kpa", 65270, 2, 1, 2, spn=102),
    Signal("intake_c", 65270, 3, 1, 1, -40, spn=105),
    # VEP1 - 65271 (0xFEF7)
    Signal("battery_v", 65271, 5, 2, 0.05, spn=168),
    # VD - 65248 (0xFEE0)
    Signal("odometer_km", 65248, 5, 4, 0.125, spn=245),
    # DD - 65276 (0xFEFC)
    Signal("fuel_level_pct", 65276, 2, 1, 0.4, spn=96),
)

_BY_PGN: dict[int, tuple[Signal, ...]] = {}
for _s in SIGNALS:
    _BY_PGN[_s.pgn] = (*_BY_PGN.get(_s.pgn, ()), _s)

WATCHED_PGNS: frozenset[int] = frozenset(_BY_PGN)


def decode_frame(frame: Frame) -> list[tuple[str, float]]:
    """Decodifica os sinais conhecidos de um quadro (omite os indisponíveis)."""
    out: list[tuple[str, float]] = []
    for sig in _BY_PGN.get(frame.pgn, ()):
        value = sig.decode(frame.data)
        if value is not None:
            out.append((sig.name, value))
    return out
