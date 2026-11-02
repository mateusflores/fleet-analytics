"""Converte um log candump J1939 em uma tabela de sinais a 1 Hz (Parquet).

Uso:
    python -m ml_service.pipeline.build_dataset <log.txt> [<saida.parquet>]

Etapas:
1. Lê o log em streaming e agrega por segundo (média dos quadros do segundo).
   Quando o mesmo sinal vem de mais de um SA, o SA 0x00 (motor) tem prioridade.
2. Divide em segmentos onde há lacuna > GAP_SECONDS (não preenche através delas).
3. Reindexa cada segmento em 1 s e faz forward-fill com limite por sinal.
"""

from __future__ import annotations

import sys
import time
from pathlib import Path

import pandas as pd

from ml_service.decoder import SIGNALS, WATCHED_PGNS, decode_frame, iter_frames

SIGNAL_NAMES = [s.name for s in SIGNALS]
GAP_SECONDS = 60
DEFAULT_FFILL_LIMIT = 5
# Sinais que a ECU transmite raramente (ou que variam devagar) toleram mais preenchimento.
SLOW_SIGNALS = {"fuel_level_pct", "odometer_km", "battery_v", "coolant_c"}
SLOW_FFILL_LIMIT = 30


def _flush(sec: int, acc: dict[str, list]) -> dict[str, float]:
    row: dict[str, float] = {"sec": sec}
    for name, (_, total, count) in acc.items():
        row[name] = total / count
    return row


def aggregate_seconds(path: str | Path) -> pd.DataFrame:
    """Passo 1: média por segundo de cada sinal, com prioridade de SA."""
    rows: list[dict[str, float]] = []
    cur: int | None = None
    acc: dict[str, list] = {}  # nome -> [prioridade, soma, contagem]
    for frame in iter_frames(path):
        sec = int(frame.ts)
        if cur is None:
            cur = sec
        elif sec > cur:
            rows.append(_flush(cur, acc))
            acc, cur = {}, sec
        # sec < cur: jitter de timestamp; atribui ao segundo corrente
        if frame.pgn not in WATCHED_PGNS:
            continue
        prio = 0 if frame.sa == 0x00 else 1
        for name, value in decode_frame(frame):
            slot = acc.get(name)
            if slot is None or prio < slot[0]:
                acc[name] = [prio, value, 1]
            elif prio == slot[0]:
                slot[1] += value
                slot[2] += 1
    if cur is not None:
        rows.append(_flush(cur, acc))
    df = pd.DataFrame(rows)
    for name in SIGNAL_NAMES:
        if name not in df:
            df[name] = float("nan")
    return df[["sec", *SIGNAL_NAMES]]


def resample_segments(df: pd.DataFrame) -> pd.DataFrame:
    """Passos 2 e 3: segmentos sem lacunas grandes, grade de 1 s, ffill limitado."""
    df = df.sort_values("sec").drop_duplicates("sec").reset_index(drop=True)
    df["segment"] = (df["sec"].diff() > GAP_SECONDS).cumsum()
    parts = []
    for seg, g in df.groupby("segment"):
        grid = pd.RangeIndex(g["sec"].min(), g["sec"].max() + 1, name="sec")
        g = g.set_index("sec").reindex(grid)
        for name in SIGNAL_NAMES:
            limit = SLOW_FFILL_LIMIT if name in SLOW_SIGNALS else DEFAULT_FFILL_LIMIT
            g[name] = g[name].ffill(limit=limit)
        g["segment"] = seg
        parts.append(g.reset_index())
    out = pd.concat(parts, ignore_index=True)
    out["ts"] = pd.to_datetime(out["sec"], unit="s", utc=True)
    out["engine_on"] = (out["rpm"] > 0).fillna(False)
    return out[["ts", "segment", "engine_on", *SIGNAL_NAMES]]


def build(path: str | Path, out_path: str | Path) -> pd.DataFrame:
    t0 = time.time()
    df = resample_segments(aggregate_seconds(path))
    Path(out_path).parent.mkdir(parents=True, exist_ok=True)
    df.to_parquet(out_path, index=False)
    print(f"{path} -> {out_path}: {len(df)} linhas, {df['segment'].nunique()} segmentos, "
          f"{df['engine_on'].mean():.1%} com motor ligado, {time.time() - t0:.0f}s")
    print("  % NaN por sinal:", {c: f"{df[c].isna().mean():.0%}" for c in SIGNAL_NAMES})
    return df


def main(argv: list[str]) -> None:
    src = Path(argv[0])
    dst = Path(argv[1]) if len(argv) > 1 else Path("data/processed") / f"{src.stem}.parquet"
    build(src, dst)


if __name__ == "__main__":
    main(sys.argv[1:])
