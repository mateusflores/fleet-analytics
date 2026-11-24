"""Ingestão: adaptadores que convertem os dados da empresa para o esquema canônico."""

from __future__ import annotations

from pathlib import Path

import numpy as np
import pandas as pd

from ml_service.config import Config, Source
from ml_service.features.windows import ENGINE_ON_S_CAP, add_engine_on_s
from ml_service.pipeline.build_dataset import aggregate_seconds, resample_segments
from ml_service.schema import META, SIGNALS

CACHE = Path("data/cache")


def _finish(df: pd.DataFrame, vehicle_id: str, group: str) -> pd.DataFrame:
    df = df.copy()
    df["vehicle_id"], df["vehicle_group"] = vehicle_id, group
    signals = [s for s in SIGNALS if s in df.columns]
    return df[[*META, *signals]]


def from_candump_j1939(src: Source) -> pd.DataFrame:
    """Log candump de um veículo. A decodificação é cacheada (o log de 1,9 GB leva ~1 min)."""
    path = Path(src.path)
    st = path.stat()
    cache = CACHE / f"{path.stem}-{st.st_size}-{int(st.st_mtime)}.parquet"
    if cache.exists():
        df = pd.read_parquet(cache)
    else:
        df = resample_segments(aggregate_seconds(path))
        CACHE.mkdir(parents=True, exist_ok=True)
        df.to_parquet(cache, index=False)
    # 0% de combustível é artefato de inicialização do nó 0x31 (D09)
    if "fuel_level_pct" in df:
        df.loc[df["fuel_level_pct"] == 0, "fuel_level_pct"] = np.nan
        df["fuel_level_pct"] = df.groupby("segment")["fuel_level_pct"].transform(
            lambda s: s.bfill(limit=60).ffill(limit=60))
    return _finish(df, src.vehicle_id or path.stem, src.vehicle_group)


def from_csv(src: Source) -> pd.DataFrame:
    """CSV genérico. Opções: `columns` {sinal: coluna}, `ts_column`, `ts_unit` (s|ms|iso),
    `vehicle_id_column` (opcional), `scale` {sinal: [fator, offset]} (opcional)."""
    opt = src.options
    raw = pd.read_csv(src.path)
    ts = raw[opt["ts_column"]]
    unit = opt.get("ts_unit", "s")
    if unit == "iso":
        sec = (pd.to_datetime(ts, utc=True) - pd.Timestamp(0, tz="UTC")) // pd.Timedelta(seconds=1)
    else:
        sec = (ts.astype(float) / (1000 if unit == "ms" else 1)).astype("int64")
    data = pd.DataFrame({"sec": np.asarray(sec, dtype="int64")})
    for signal, column in opt["columns"].items():
        if signal not in SIGNALS:
            raise ValueError(f"sinal desconhecido no mapeamento: {signal}")
        factor, offset = opt.get("scale", {}).get(signal, (1.0, 0.0))
        data[signal] = raw[column].astype(float) * factor + offset
    signals = list(opt["columns"])
    vid_col = opt.get("vehicle_id_column")
    vids = raw[vid_col].astype(str) if vid_col else pd.Series(src.vehicle_id or Path(src.path).stem, index=raw.index)
    parts = []
    for vid, g in data.groupby(vids.to_numpy()):
        g = g.groupby("sec", as_index=False)[signals].mean()
        parts.append(_finish(resample_segments(g, signals), str(vid), src.vehicle_group))
    return pd.concat(parts, ignore_index=True)


ADAPTERS = {"candump_j1939": from_candump_j1939, "csv": from_csv}


def load_company_data(cfg: Config) -> pd.DataFrame:
    frames = []
    for src in cfg.sources:
        if src.adapter not in ADAPTERS:
            raise ValueError(f"adaptador desconhecido: {src.adapter} (disponíveis: {sorted(ADAPTERS)})")
        frames.append(ADAPTERS[src.adapter](src))
    df = pd.concat(frames, ignore_index=True)
    dup = df.duplicated(["vehicle_id", "ts"]).sum()
    if dup:
        raise ValueError(f"{dup} linhas com (vehicle_id, ts) repetidos entre as fontes")
    df = add_engine_on_s(df)
    # `assume_warm: true` na fonte: log que começa com o motor já quente (tempo desde a partida desconhecido)
    for src in cfg.sources:
        if src.options.get("assume_warm") and src.vehicle_id:
            df.loc[df["vehicle_id"] == src.vehicle_id, "engine_on_s"] = ENGINE_ON_S_CAP
    return df
