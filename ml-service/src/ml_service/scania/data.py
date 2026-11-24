"""Leitura do Scania Component X (IDA 2024) e rotulagem por tempo até a falha.

Fonte: https://doi.org/10.1038/s41597-025-04802-6 (dataset: 10.5878/jvb5-d390).
Os CSVs originais são convertidos uma vez para Parquet (data/scania/) para leitura rápida.
"""

from __future__ import annotations

from pathlib import Path

import numpy as np
import pandas as pd
import pyarrow.csv as pacsv
import pyarrow.parquet as pq

RAW = Path("../data/Scania Component Dataset/data")
OUT = Path("data/scania")

# Classe pelo tempo restante até a falha (documentação do desafio):
# >48 -> 0 | 48-24 -> 1 | 24-12 -> 2 | 12-6 -> 3 | 6-0 -> 4
TTE_EDGES = [48, 24, 12, 6]


def convert_csvs(raw: Path = RAW, out: Path = OUT) -> None:
    out.mkdir(parents=True, exist_ok=True)
    for name in ("train_operational_readouts", "validation_operational_readouts",
                 "test_operational_readouts", "train_specifications", "validation_specifications",
                 "test_specifications", "train_tte", "validation_labels", "test_labels"):
        dst = out / f"{name}.parquet"
        if dst.exists():
            continue
        table = pacsv.read_csv(raw / f"{name}.csv")
        pq.write_table(table, dst, compression="zstd")
        print(f"{name}: {table.num_rows} linhas, {table.num_columns} colunas")


def label_from_tte(time_to_failure: np.ndarray) -> np.ndarray:
    """Classe 0..4 a partir do tempo restante até a falha (NaN/inf = sem falha -> 0)."""
    t = np.asarray(time_to_failure, dtype=float)
    y = np.zeros(len(t), dtype=np.int8)
    valid = np.isfinite(t) & (t >= 0)
    y[valid & (t <= 48)] = 1
    y[valid & (t <= 24)] = 2
    y[valid & (t <= 12)] = 3
    y[valid & (t <= 6)] = 4
    return y


def load_train_readouts(out: Path = OUT) -> pd.DataFrame:
    """Leituras de treino com a coluna `y` (classe) e `tte` (tempo até a falha, NaN se sem reparo)."""
    ro = pd.read_parquet(out / "train_operational_readouts.parquet")
    tte = pd.read_parquet(out / "train_tte.parquet")
    ro = ro.merge(tte, on="vehicle_id", how="left")
    ro["tte"] = np.where(ro["in_study_repair"] == 1, ro["length_of_study_time_step"] - ro["time_step"], np.nan)
    ro["y"] = label_from_tte(ro["tte"].to_numpy())
    return ro.drop(columns=["length_of_study_time_step"])
