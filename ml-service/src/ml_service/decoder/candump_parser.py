"""Parser de logs no formato candump (SocketCAN).

Linha esperada: ``(1594702589.999073) can1 18F11031#0000FFFFFFFFFFFF``
"""

from __future__ import annotations

from collections.abc import Iterator
from dataclasses import dataclass
from pathlib import Path

# IDs menores que isso são quadros CAN padrão (11 bits), não J1939.
_MIN_EXTENDED_ID = 0x800


@dataclass(frozen=True, slots=True)
class Frame:
    ts: float
    can_id: int
    priority: int
    pgn: int
    sa: int
    da: int | None  # endereço de destino (apenas PDU1; None para broadcast/PDU2)
    data: bytes


def pgn_from_id(can_id: int) -> tuple[int, int, int | None]:
    """Extrai (pgn, sa, da) de um identificador de 29 bits J1939."""
    dp = (can_id >> 24) & 0x1
    pf = (can_id >> 16) & 0xFF
    ps = (can_id >> 8) & 0xFF
    sa = can_id & 0xFF
    if pf < 240:  # PDU1: PS é o endereço de destino, não faz parte do PGN
        return (dp << 16) | (pf << 8), sa, ps
    return (dp << 16) | (pf << 8) | ps, sa, None  # PDU2: PS é extensão do grupo


def parse_line(line: str) -> Frame | None:
    """Converte uma linha em Frame; devolve None se for inválida ou não J1939."""
    parts = line.split()
    if len(parts) < 3:
        return None
    try:
        ts = float(parts[0].strip("()"))
        id_hex, sep, data_hex = parts[2].partition("#")
        if not sep:
            return None
        can_id = int(id_hex, 16)
        if can_id < _MIN_EXTENDED_ID:
            return None
        data = bytes.fromhex(data_hex)
    except ValueError:  # linha truncada, hex ímpar, etc.
        return None
    pgn, sa, da = pgn_from_id(can_id)
    return Frame(ts, can_id, (can_id >> 26) & 0x7, pgn, sa, da, data)


def iter_frames(path: str | Path) -> Iterator[Frame]:
    """Lê o arquivo em streaming (os logs têm até ~2 GB)."""
    with open(path, encoding="ascii", errors="replace") as fh:
        for line in fh:
            frame = parse_line(line)
            if frame is not None:
                yield frame
