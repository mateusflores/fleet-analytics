"""Métrica de custo do desafio IDA 2024 e decisão de mínimo custo esperado."""

import numpy as np

# COST[real][previsto]
COST = np.array([
    [0, 7, 8, 9, 10],
    [200, 0, 7, 8, 9],
    [300, 200, 0, 7, 8],
    [400, 300, 200, 0, 7],
    [500, 400, 300, 200, 0],
], dtype=float)


def total_cost(y_true, y_pred) -> float:
    return float(COST[np.asarray(y_true, int), np.asarray(y_pred, int)].sum())


def min_cost_decision(proba: np.ndarray) -> np.ndarray:
    """Para cada linha escolhe a classe que minimiza o custo esperado: argmin_j sum_i p_i * C[i, j]."""
    return (proba @ COST).argmin(axis=1)
