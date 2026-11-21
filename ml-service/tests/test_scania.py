import numpy as np
import pytest

from ml_service.scania.cost import COST, min_cost_decision, total_cost
from ml_service.scania.data import label_from_tte


def test_labels_follow_challenge_windows():
    t = np.array([np.nan, 100, 48.5, 48, 30, 24, 13, 12, 7, 6, 0])
    assert list(label_from_tte(t)) == [0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4]


def test_total_cost_all_zero_baseline():
    y = np.array([0, 0, 1, 2, 3, 4])
    assert total_cost(y, np.zeros(6, int)) == 200 + 300 + 400 + 500
    assert total_cost(y, y) == 0


def test_min_cost_decision_alarms_on_modest_risk():
    # com 5% de chance de classe 4, vale alarmar (custo de perder = 500 >> 10)
    p = np.array([[0.95, 0, 0, 0, 0.05], [1, 0, 0, 0, 0]])
    # custo esperado: prever 0 = 0.05*500 = 25; prever 4 = 0.95*10 = 9.5
    assert list(min_cost_decision(p)) == [4, 0]


def test_features_rates_and_resets():
    import pandas as pd
    from ml_service.scania.features import build_features

    cols = {f"{v}_{b}": 0.0 for v, n in {"167": 10, "272": 10, "291": 11, "158": 10, "459": 20, "397": 36}.items() for b in range(n)}
    cols.update({c: 0.0 for c in ["171_0", "666_0", "427_0", "837_0", "309_0", "835_0", "370_0", "100_0"]})
    rows = []
    for t, c in [(10, 100.0), (20, 300.0), (30, 50.0)]:  # reinício do contador no 3º ponto
        rows.append({"vehicle_id": 1, "time_step": t, **cols, "171_0": c, "167_0": c, "167_1": 2 * c})
    rows.append({"vehicle_id": 2, "time_step": 5, **cols, "171_0": 7.0})
    f = build_features(pd.DataFrame(rows))
    assert f.loc[1, "171_0_rate_l1"] == pytest.approx(20.0)      # (300-100)/10
    assert np.isnan(f.loc[2, "171_0_rate_l1"])                   # reinício -> NaN
    assert np.isnan(f.loc[3, "171_0_rate_l1"])                   # primeira leitura de outro veículo
    assert f.loc[0, "h167_p1"] == pytest.approx(2 / 3)           # proporção de vida inteira
