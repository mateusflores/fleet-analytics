"""Treina o LightGBM multiclasse (classes 0-4) e avalia pela métrica de custo do IDA 2024.

Uso:  python -m ml_service.scania.train
"""

from __future__ import annotations

import json
import time
from pathlib import Path

import joblib
import lightgbm as lgb
import numpy as np
import pandas as pd
from sklearn.metrics import confusion_matrix

from .cost import min_cost_decision, total_cost
from .data import OUT, load_train_readouts
from .features import SPEC_COLS, build_features, spec_categories, spec_frame

NEG_KEEP = 0.15  # fração de leituras classe 0 mantidas no treino (peso 1/NEG_KEEP)
SEED = 42
MODELS = Path("models")


def _with_specs(feat: pd.DataFrame, vid: np.ndarray, spec: pd.DataFrame) -> pd.DataFrame:
    sp = spec.set_index("vehicle_id").loc[vid, SPEC_COLS].reset_index(drop=True)
    return pd.concat([feat, sp], axis=1)


def last_readout_set(split: str, cats) -> tuple[pd.DataFrame, np.ndarray, np.ndarray]:
    ro = pd.read_parquet(OUT / f"{split}_operational_readouts.parquet").sort_values(["vehicle_id", "time_step"])
    ro = ro.reset_index(drop=True)
    feat = build_features(ro)
    spec = spec_frame(pd.read_parquet(OUT / f"{split}_specifications.parquet"), cats)
    feat = _with_specs(feat, ro["vehicle_id"].to_numpy(), spec)
    last = ro.groupby("vehicle_id").tail(1).index
    labels = pd.read_parquet(OUT / f"{split}_labels.parquet").set_index("vehicle_id")["class_label"]
    vids = ro.loc[last, "vehicle_id"].to_numpy()
    return feat.loc[last].reset_index(drop=True), labels.loc[vids].to_numpy(), vids


def report(name: str, y: np.ndarray, proba: np.ndarray) -> dict:
    pred_arg, pred_cost = proba.argmax(axis=1), min_cost_decision(proba)
    base = total_cost(y, np.zeros_like(y))
    res = {"n": len(y), "custo_sempre_0": base, "custo_argmax": total_cost(y, pred_arg),
           "custo_min_esperado": total_cost(y, pred_cost)}
    alarm = pred_cost > 0
    res["recall_alarme"] = float(alarm[y > 0].mean())
    res["falsos_alarmes"] = int(alarm[y == 0].sum())
    print(f"\n== {name} ({res['n']} veículos, {int((y > 0).sum())} em risco)")
    print(json.dumps(res, indent=1))
    print("matriz de confusão (linhas=real, colunas=previsto, decisão de mínimo custo):")
    print(confusion_matrix(y, pred_cost, labels=range(5)))
    return res


def main() -> None:
    t0 = time.time()
    train_spec = pd.read_parquet(OUT / "train_specifications.parquet")
    cats = spec_categories(train_spec)

    ro = load_train_readouts().sort_values(["vehicle_id", "time_step"]).reset_index(drop=True)
    y_all, groups = ro["y"].to_numpy(), ro["vehicle_id"].to_numpy()
    feat = build_features(ro.drop(columns=["tte", "y", "in_study_repair"]))
    feat = _with_specs(feat, groups, spec_frame(train_spec, cats))
    del ro
    print(f"features de treino: {feat.shape} ({time.time() - t0:.0f}s)")

    # holdout por veículo (nunca separa leituras do mesmo caminhão)
    rng = np.random.default_rng(SEED)
    vids = np.unique(groups)
    hold = set(rng.choice(vids, size=int(0.15 * len(vids)), replace=False))
    is_hold = np.fromiter((v in hold for v in groups), bool, count=len(groups))
    keep_neg = rng.random(len(y_all)) < NEG_KEEP
    use = (y_all > 0) | keep_neg
    w = np.where(y_all == 0, 1.0 / NEG_KEEP, 1.0)
    tr, ho = use & ~is_hold, use & is_hold
    dtr = lgb.Dataset(feat[tr], y_all[tr], weight=w[tr], categorical_feature=SPEC_COLS)
    dho = lgb.Dataset(feat[ho], y_all[ho], weight=w[ho], reference=dtr)

    params = dict(objective="multiclass", num_class=5, learning_rate=0.05, num_leaves=31, min_data_in_leaf=40,
                  feature_fraction=0.5, bagging_fraction=0.8, bagging_freq=1, lambda_l2=5.0,
                  verbose=-1, seed=SEED, num_threads=8)
    booster = lgb.train(params, dtr, num_boost_round=1500, valid_sets=[dho],
                        callbacks=[lgb.early_stopping(50), lgb.log_evaluation(100)])
    print(f"melhor iteração: {booster.best_iteration} ({time.time() - t0:.0f}s)")

    results = {}
    for split in ("validation", "test"):
        X, y, _ = last_readout_set(split, cats)
        results[split] = report(split, y, booster.predict(X, num_iteration=booster.best_iteration))

    MODELS.mkdir(exist_ok=True)
    joblib.dump({"booster": booster, "spec_categories": cats, "features": list(feat.columns),
                 "params": params, "best_iteration": booster.best_iteration}, MODELS / "scania_lgbm.joblib")
    (MODELS / "scania_lgbm_metrics.json").write_text(json.dumps(results, indent=1))
    print(f"concluído em {time.time() - t0:.0f}s")


if __name__ == "__main__":
    main()
