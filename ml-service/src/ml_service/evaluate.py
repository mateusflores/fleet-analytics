"""Avaliação dos detectores: falsos alarmes em dados normais e detecção de falhas sintéticas."""

from __future__ import annotations

import numpy as np
import pandas as pd
from sklearn.metrics import roc_auc_score

from ml_service.detector import GroupDetector
from ml_service.features.windows import segment_windows
from ml_service.synthetic.faults import FAULTS, applicable_faults, inject


def test_frames(df: pd.DataFrame, w_test: pd.DataFrame, size: int) -> list[pd.DataFrame]:
    """Trechos a 1 Hz cobertos pelas janelas de teste (um por veículo/segmento)."""
    frames = []
    for (vid, seg), g in w_test.groupby(["vehicle_id", "segment"]):
        lo, hi = int(g["start_idx"].min()), int(g["start_idx"].max()) + size
        rows = df[(df["vehicle_id"] == vid) & (df["segment"] == seg)].sort_values("ts")
        frames.append(rows.iloc[lo:hi].reset_index(drop=True))
    return frames


def clean_metrics(det: GroupDetector, w_test: pd.DataFrame, stride: int) -> dict:
    s = det.score(w_test)
    out_of_scope = float(1 - s["scored"].mean())
    s = s[s["scored"]]
    far = float(s["alarm"].mean())
    return {
        "n_windows": len(w_test),
        "n_scored": len(s),
        "out_of_scope_frac": out_of_scope,
        "false_alarm_rate": far,
        "false_alarms_per_hour": far * 3600 / stride,
        "far_isolation_forest": float(s["if_alarm"].mean()),
        "far_expected_value": float(s["ev_alarm"].mean()),
        "far_by_signal": {t: float(s[f"alarm_{t}"].mean()) for t in det.ev},
        "expected_value_quality": det.expected_value_quality(w_test),
    }


def fault_metrics(det: GroupDetector, frames: list[pd.DataFrame], signals: list[str], size: int, stride: int,
                  duration: int, period: int, severities: list[float], seed: int) -> list[dict]:
    rows = []
    for name in applicable_faults(signals):
        for sev in severities:
            rng = np.random.default_rng(seed)
            scored, delays, n_events = [], [], 0
            for frame in frames:
                faulted, profile, events = inject(frame, name, sev, duration, period, rng)
                w = segment_windows(faulted, signals, size, stride)
                if w.empty:
                    continue
                s = det.score(w)
                starts = w["start_idx"].to_numpy()
                s["frac"] = [float((profile[a:a + size] > 0).mean()) for a in starts]
                s["start"] = starts
                scored.append(s)
                for onset, end in events:
                    over = s[(s["start"] + size > onset) & (s["start"] < end) & s["scored"]]
                    if over.empty:  # evento inteiro no aquecimento: fora do escopo
                        continue
                    hit = over[over["alarm"]]
                    n_events += 1
                    if len(hit):
                        delays.append(int(hit["start"].min()) + size - onset)
            if not scored:
                continue
            s = pd.concat(scored, ignore_index=True)
            s = s[s["scored"]]
            pos, neg = s[s["frac"] >= 0.5], s[s["frac"] == 0]
            if pos.empty or neg.empty:
                continue
            tp, fp = int(pos["alarm"].sum()), int(neg["alarm"].sum())
            prec = tp / (tp + fp) if tp + fp else float("nan")
            rec = tp / len(pos)
            y = np.r_[np.ones(len(pos)), np.zeros(len(neg))]
            auc = lambda col: float(roc_auc_score(y, np.r_[pos[col], neg[col]]))  # noqa: E731
            rows.append({
                "fault": name, "signal": FAULTS[name].signal, "severity": sev,
                "n_pos": len(pos), "n_neg": len(neg),
                "precision": prec, "recall": rec,
                "f1": 2 * prec * rec / (prec + rec) if tp else 0.0,
                "recall_isolation_forest": float(pos["if_alarm"].mean()),
                "recall_expected_value": float(pos["ev_alarm"].mean()),
                "auc": auc("score"), "auc_isolation_forest": auc("if_score"), "auc_expected_value": auc("ev_score"),
                "events": n_events, "events_detected": len(delays),
                "median_delay_s": float(np.median(delays)) if delays else None,
            })
    return rows
