"""Detectores por grupo de veículo, treinados só com operação normal.

1. Anomalia global: IsolationForest sobre todas as features da janela (baseline).
2. Valor esperado: para cada sinal de resposta, um regressor prevê a média do sinal na janela a partir das
   condições de operação (sinais 'driver' + contexto); para sinais acumulados (nível de combustível) o alvo
   é a inclinação. O resíduo (real - esperado) é o indicador, e é explicável.

Escopo: janelas no período de aquecimento (`warmup_s` após a partida do motor) não são usadas no treino
nem pontuadas — o comportamento térmico nesse regime não é previsível só pelas condições de operação.

Limiares (validação cruzada em blocos temporais contíguos do treino, escores *out-of-fold*):
- cada detector é normalizado pelo seu quantil (1 - far);
- o escore combinado é o máximo dos escores normalizados, e um fator global `c` é escolhido para que o
  alarme combinado tenha a taxa-alvo de falsos alarmes. Alarme = escore combinado > 1.
"""

from __future__ import annotations

import numpy as np
import pandas as pd
from sklearn.ensemble import HistGradientBoostingRegressor, IsolationForest
from sklearn.metrics import mean_absolute_error, r2_score

from ml_service.features.windows import CONTEXT_FEATURES, feature_columns
from ml_service.schema import SIGNALS, by_role


ROUGHNESS = "rpm_roughness"


def time_block_folds(n: int, k: int) -> np.ndarray:
    """Divide n janelas (já em ordem cronológica) em k blocos contíguos."""
    return (np.arange(n) * k // max(n, 1)).astype(int)


class GroupDetector:
    def __init__(self, signals: list[str], far: float = 0.01, n_folds: int = 5, seed: int = 42, warmup_s: int = 0):
        self.signals, self.far, self.n_folds, self.seed, self.warmup_s = signals, far, n_folds, seed, warmup_s
        self.features: list[str] = []
        self.ev_features: list[str] = []
        self.ev: dict[str, dict] = {}

    def _iforest(self):
        return IsolationForest(n_estimators=200, random_state=self.seed, n_jobs=-1)

    def _regressor(self):
        return HistGradientBoostingRegressor(max_iter=200, learning_rate=0.06, max_leaf_nodes=15,
                                             l2_regularization=1.0, random_state=self.seed)

    def in_scope(self, w: pd.DataFrame) -> np.ndarray:
        return w["engine_on_s"].to_numpy(float) >= self.warmup_s

    def fit(self, w: pd.DataFrame) -> "GroupDetector":
        w = w[self.in_scope(w)].sort_values(["vehicle_id", "segment", "start_idx"])
        self.features = feature_columns(w)
        drivers = set(by_role(self.signals, "driver"))
        self.ev_features = [f for f in self.features if f.rsplit("_", 1)[0] in drivers or f in CONTEXT_FEATURES]
        has_drivers = any(f.rsplit("_", 1)[0] in drivers for f in self.ev_features)
        targets = {t: f"{t}_mean" for t in by_role(self.signals, "response")}
        targets.update({t: f"{t}_slope" for t in by_role(self.signals, "cumulative")})
        if not has_drivers:
            targets = {}
        feats = {t: self.ev_features for t in targets}
        # irregularidade do RPM: prevista pelas demais condições (sem as features do próprio RPM)
        no_rpm = [f for f in self.ev_features if not f.startswith("rpm_")]
        if "rpm_diff_abs_mean" in self.features and any(f.rsplit("_", 1)[0] in drivers for f in no_rpm):
            targets[ROUGHNESS] = "rpm_diff_abs_mean"
            feats[ROUGHNESS] = no_rpm
        X = w[self.features].to_numpy(float)
        Xev = {t: w[f].to_numpy(float) for t, f in feats.items()}
        folds = time_block_folds(len(w), self.n_folds)

        oof_if = np.empty(len(w))
        oof_res = {t: np.empty(len(w)) for t in targets}
        for k in range(self.n_folds):
            tr, te = folds != k, folds == k
            oof_if[te] = -self._iforest().fit(X[tr]).score_samples(X[te])
            for t, col in targets.items():
                y = w[col].to_numpy(float)
                oof_res[t][te] = y[te] - self._regressor().fit(Xev[t][tr], y[tr]).predict(Xev[t][te])

        q = 1 - self.far
        q_if = float(np.quantile(oof_if, q))
        q_ev = {t: float(max(np.quantile(np.abs(r), q), 1e-9)) for t, r in oof_res.items()}
        combined = oof_if / q_if
        for t, r in oof_res.items():
            combined = np.maximum(combined, np.abs(r) / q_ev[t])
        self.calibration_factor = float(np.quantile(combined, q))

        self.iforest = self._iforest().fit(X)
        self.if_threshold = q_if * self.calibration_factor
        for t, col in targets.items():
            y, res = w[col].to_numpy(float), oof_res[t]
            self.ev[t] = {
                "column": col,
                "features": feats[t],
                "model": self._regressor().fit(Xev[t], y),
                "threshold": q_ev[t] * self.calibration_factor,
                "cv_mae": float(np.abs(res).mean()),
                "cv_r2": float(r2_score(y, y - res)),
            }
        self.n_train = len(w)
        return self

    def score(self, w: pd.DataFrame) -> pd.DataFrame:
        """Por janela: escores normalizados pelo limiar (> 1 = alarme), resíduos e alarmes.
        Janelas fora do escopo (aquecimento) têm `scored` = False e nunca geram alarme."""
        out = pd.DataFrame(index=w.index)
        scope = self.in_scope(w)
        out["scored"] = scope
        out["if_score"] = -self.iforest.score_samples(w[self.features].to_numpy(float)) / self.if_threshold
        ev_norm = np.zeros(len(w))
        for t, m in self.ev.items():
            res = w[m["column"]].to_numpy(float) - m["model"].predict(w[m["features"]].to_numpy(float))
            out[f"resid_{t}"] = res
            out[f"alarm_{t}"] = (np.abs(res) > m["threshold"]) & scope
            ev_norm = np.maximum(ev_norm, np.abs(res) / m["threshold"])
        out["ev_score"] = ev_norm
        out["score"] = np.maximum(out["if_score"], out["ev_score"])
        out["if_alarm"] = (out["if_score"] > 1) & scope
        out["ev_alarm"] = (out["ev_score"] > 1) & scope
        out["alarm"] = out["if_alarm"] | out["ev_alarm"]
        return out

    def explain_items(self, scores_row: pd.Series) -> list[tuple[str, str, float]]:
        """(chave do detector, mensagem legível, escore normalizado) para cada alarme de uma janela."""
        items = []
        for t, m in self.ev.items():
            if not scores_row.get(f"alarm_{t}", False):
                continue
            r = scores_row[f"resid_{t}"]
            level = abs(r) / m["threshold"]
            if t == ROUGHNESS:
                items.append((t, "rotação do motor irregular para a condição de operação", level))
                continue
            spec = SIGNALS[t]
            if m["column"].endswith("_slope"):
                items.append((t, f"{spec.label} {'caindo' if r < 0 else 'subindo'} {abs(r) * 60:.2f} {spec.unit}/min "
                                 "além do esperado para a condição de operação", level))
            else:
                items.append((t, f"{spec.label} {abs(r):.1f} {spec.unit} {'acima' if r > 0 else 'abaixo'} do esperado "
                                 "para a condição de operação", level))
        if scores_row.get("if_alarm", False) and not items:
            items.append(("pattern", "padrão de operação fora do normal aprendido para este veículo",
                          float(scores_row["if_score"])))
        return items

    def explain(self, scores_row: pd.Series) -> list[str]:
        """Mensagens legíveis para uma janela com alarme."""
        return [msg for _, msg, _ in self.explain_items(scores_row)]

    def expected_value_quality(self, w: pd.DataFrame) -> dict:
        res = {}
        w = w[self.in_scope(w)]
        if not self.ev or w.empty:
            return res
        for t, m in self.ev.items():
            y, p = w[m["column"]].to_numpy(float), m["model"].predict(w[m["features"]].to_numpy(float))
            res[t] = {"target": m["column"], "mae": float(mean_absolute_error(y, p)),
                      "r2": float(r2_score(y, p)) if np.ptp(y) > 0 else None,
                      "cv_mae": m["cv_mae"], "cv_r2": m["cv_r2"], "threshold": m["threshold"]}
        return res
