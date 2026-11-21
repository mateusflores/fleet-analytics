"""X1 — Transferência entre frotas: o modelo treinado em uma frota serve para outra?

Treina o detector da frota de origem usando só os sinais comuns às duas frotas e compara a taxa de alarmes
em (a) dados normais de teste da própria frota e (b) dados normais da outra frota.

Uso: python -m ml_service.experiments.transfer --source configs/empresa-a.yaml --targets configs/t660.yaml configs/empresa-b.yaml
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from ml_service.config import load_config
from ml_service.detector import GroupDetector
from ml_service.features.windows import compute_windows
from ml_service.ingest import load_company_data
from ml_service.quality import usable_signals
from ml_service.schema import by_role
from ml_service.train import prepare_windows


def _signals(df, cfg) -> list[str]:
    return by_role(usable_signals(df, cfg.min_valid_frac), "driver", "response", "cumulative")


def transfer(source_cfg_path: str, target_cfg_path: str) -> dict:
    scfg, tcfg = load_config(source_cfg_path), load_config(target_cfg_path)
    sdf, tdf = load_company_data(scfg), load_company_data(tcfg)
    common = [s for s in _signals(sdf, scfg) if s in _signals(tdf, tcfg)]
    w, signals = prepare_windows(sdf, scfg, common)
    det = GroupDetector(signals, far=scfg.target_false_alarm_rate, n_folds=scfg.cv_folds, seed=scfg.seed,
                        warmup_s=scfg.warmup_s).fit(w[w["split"] == "train"])
    own = det.score(w[w["split"] == "test"])
    other = det.score(compute_windows(tdf, signals, scfg.window_size_s, scfg.window_stride_s))
    own, other = own[own["scored"]], other[other["scored"]]
    per_signal = lambda s: {t: round(float(s[f"alarm_{t}"].mean()), 4) for t in det.ev}  # noqa: E731
    return {
        "source": scfg.company, "target": tcfg.company, "common_signals": signals,
        "own_test": {"windows": len(own), "alarm_rate": float(own["alarm"].mean()), "by_detector": per_signal(own)},
        "other_fleet": {"windows": len(other), "alarm_rate": float(other["alarm"].mean()),
                        "isolation_forest": float(other["if_alarm"].mean()), "by_detector": per_signal(other)},
    }


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--source", required=True)
    ap.add_argument("--targets", nargs="+", required=True)
    ap.add_argument("--out", default="reports/experiments/x1_transfer.json")
    a = ap.parse_args()
    out = Path(a.out)
    results = json.loads(out.read_text()) if out.exists() else {}
    for target in a.targets:
        r = transfer(a.source, target)
        results[f"{r['source']}->{r['target']}"] = r
        print(f"{r['source']} -> {r['target']}: sinais comuns {len(r['common_signals'])} | alarmes na própria frota "
              f"{r['own_test']['alarm_rate']:.1%} ({r['own_test']['windows']} janelas) | na outra frota "
              f"{r['other_fleet']['alarm_rate']:.1%} ({r['other_fleet']['windows']} janelas)")
        print("   por detector na outra frota:", r["other_fleet"]["by_detector"], "| IF:", round(r["other_fleet"]["isolation_forest"], 3))
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(results, indent=1, ensure_ascii=False))
