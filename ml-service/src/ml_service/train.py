"""Pipeline de treinamento por empresa: dados brutos -> validação -> features -> modelos -> avaliação -> artefato.

Uso:  python -m ml_service.train --config configs/empresa-a.yaml
Saída: models/<empresa>/<versão>/{model.joblib, metrics.json, quality.json, report.md, config.yaml}
"""

from __future__ import annotations

import argparse
import json
import shutil
import sys
import time
from datetime import datetime
from pathlib import Path

import joblib
import pandas as pd

from ml_service.config import Config, load_config
from ml_service.detector import GroupDetector
from ml_service.evaluate import clean_metrics, fault_metrics, test_frames
from ml_service.features.windows import assign_split, compute_windows
from ml_service.ingest import load_company_data
from ml_service.quality import quality_report, usable_signals
from ml_service.report import render_report
from ml_service.schema import by_role

MODELS = Path("models")


def prepare_windows(df: pd.DataFrame, cfg: Config, signals: list[str] | None = None) -> tuple[pd.DataFrame, list[str]]:
    """Janelas de um grupo com a coluna `split`. `signals` restringe os sinais (padrão: todos os utilizáveis)."""
    usable = by_role(usable_signals(df, cfg.min_valid_frac), "driver", "response", "cumulative")
    signals = usable if signals is None else [s for s in usable if s in signals]
    size, stride = cfg.window_size_s, cfg.window_stride_s
    w = compute_windows(df, signals, size, stride)
    test_segments = {s.vehicle_id: set(s.test_segments) for s in cfg.sources if s.test_segments and s.vehicle_id}
    w["split"] = assign_split(w, test_segments, cfg.test_frac, gap=size // stride)
    return w, signals


def train_group(df: pd.DataFrame, cfg: Config) -> tuple[GroupDetector, dict]:
    w, signals = prepare_windows(df, cfg)
    size, stride = cfg.window_size_s, cfg.window_stride_s
    w_train, w_test = w[w["split"] == "train"], w[w["split"] == "test"]
    if len(w_train) < 200 or len(w_test) < 50:
        raise ValueError(f"janelas insuficientes (treino={len(w_train)}, teste={len(w_test)})")

    det = GroupDetector(signals, far=cfg.target_false_alarm_rate, n_folds=cfg.cv_folds, seed=cfg.seed,
                        warmup_s=cfg.warmup_s).fit(w_train)
    frames = test_frames(df, w_test, size)
    metrics = {
        "signals": signals,
        "n_features": len(det.features),
        "n_train_in_scope": det.n_train,
        "calibration_factor": det.calibration_factor,
        "windows": {"train": len(w_train), "test": len(w_test), "gap": int((w["split"] == "gap").sum())},
        "split_detail": {k: {f"{v}/seg{s}": int(n) for (v, s), n in g.groupby(["vehicle_id", "segment"]).size().items()}
                         for k, g in w[w["split"] != "gap"].groupby("split")},
        "clean_test": clean_metrics(det, w_test, stride),
        "faults": fault_metrics(det, frames, signals, size, stride, cfg.fault_duration_s, cfg.fault_period_s,
                                cfg.fault_severities, cfg.seed),
    }
    return det, metrics


def run(config_path: str, models_dir: Path = MODELS) -> Path:
    t0 = time.time()
    cfg = load_config(config_path)
    df = load_company_data(cfg)
    quality = quality_report(df, cfg)
    version = datetime.now().strftime("%Y%m%d-%H%M%S")
    out = models_dir / cfg.company / version
    out.mkdir(parents=True, exist_ok=True)
    (out / "quality.json").write_text(json.dumps(quality, indent=1, ensure_ascii=False))
    shutil.copy(config_path, out / "config.yaml")
    if not quality["ok"]:
        (out / "report.md").write_text(render_report(cfg, version, quality, {}, 0))
        print("Dados reprovados na validação:\n- " + "\n- ".join(quality["errors"]))
        print(f"Relatório: {out / 'report.md'}")
        sys.exit(1)

    detectors, metrics = {}, {}
    for group, g in df.groupby("vehicle_group"):
        detectors[group], metrics[group] = train_group(g, cfg)
    joblib.dump({"company": cfg.company, "version": version, "window_size_s": cfg.window_size_s,
                 "window_stride_s": cfg.window_stride_s, "detectors": detectors}, out / "model.joblib")
    (out / "metrics.json").write_text(json.dumps(metrics, indent=1, ensure_ascii=False))
    (out / "report.md").write_text(render_report(cfg, version, quality, metrics, time.time() - t0))
    (models_dir / cfg.company / "LATEST").write_text(version)
    print(f"{cfg.company} {version}: treinado em {time.time() - t0:.0f}s -> {out}")
    return out


if __name__ == "__main__":
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--config", required=True)
    run(ap.parse_args().config)
