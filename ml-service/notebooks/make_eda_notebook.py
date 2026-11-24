"""Gera notebooks/01_eda.ipynb (executar com: python notebooks/make_eda_notebook.py)."""
import nbformat as nbf

md, code = nbf.v4.new_markdown_cell, nbf.v4.new_code_cell
cells = [
md("""# 01 — Análise exploratória dos dados J1939

Dados decodificados dos logs CAN do Kenworth T270 2014 (Colorado State University), reamostrados a 1 Hz.

- **Treino:** `CSU090BJ` (viagem contínua de ~4,2 h)
- **Teste:** `cross_country` (~16 h, 8 segmentos)

Objetivos: entender os sinais, os estados de operação, as relações entre variáveis e **quanto o teste difere do treino**."""),
code("""import pandas as pd, numpy as np, matplotlib.pyplot as plt
from pathlib import Path

P = Path("../data/processed"); FIG = Path("../reports/figures"); FIG.mkdir(parents=True, exist_ok=True)
plt.rcParams.update({"figure.dpi": 110, "axes.grid": True, "grid.alpha": .3})
bj = pd.read_parquet(P / "CSU090BJ.parquet")
cc = pd.read_parquet(P / "2014_KW_T270_cross_country_can_log.parquet")
SIG = [c for c in bj.columns if c not in ("ts", "segment", "engine_on")]
def save(name): plt.tight_layout(); plt.savefig(FIG / f"{name}.png", bbox_inches="tight"); plt.show()
print(len(bj), len(cc)); SIG"""),
md("## 1. Resumo estatístico (somente motor ligado)"),
code("""on = lambda d: d[d.engine_on]
pd.concat({"treino (BJ)": on(bj)[SIG].describe().T[["mean","std","min","max"]],
           "teste (cross-country)": on(cc)[SIG].describe().T[["mean","std","min","max"]]}, axis=1).round(2)"""),
md("## 2. Séries temporais — primeira hora do treino"),
code("""d = on(bj).iloc[:3600].set_index("ts")
cols = ["rpm", "speed_kmh", "load_pct", "fuel_rate_lph", "coolant_c", "boost_kpa"]
fig, ax = plt.subplots(len(cols), 1, figsize=(10, 12), sharex=True)
for a, c in zip(ax, cols): a.plot(d.index, d[c], lw=.7); a.set_ylabel(c)
save("series_bj_1h")"""),
md("## 3. Distribuições: treino × teste\n\nSe as distribuições forem muito diferentes, o limiar de anomalia aprendido no treino pode gerar falsos positivos no teste."),
code("""cols = ["rpm","speed_kmh","load_pct","fuel_rate_lph","coolant_c","intake_c","boost_kpa","oil_kpa","battery_v"]
fig, ax = plt.subplots(3, 3, figsize=(12, 9))
for a, c in zip(ax.ravel(), cols):
    lo, hi = on(bj)[c].quantile(.001), on(bj)[c].quantile(.999)
    bins = np.linspace(min(lo, on(cc)[c].quantile(.001)), max(hi, on(cc)[c].quantile(.999)), 50)
    a.hist(on(bj)[c].dropna(), bins, density=True, alpha=.55, label="treino")
    a.hist(on(cc)[c].dropna(), bins, density=True, alpha=.55, label="teste"); a.set_title(c)
ax[0,0].legend(); save("distribuicoes_treino_teste")"""),
md("## 4. Correlações (motor ligado, treino)"),
code("""corr = on(bj)[SIG].corr()
fig, a = plt.subplots(figsize=(8, 7)); im = a.imshow(corr, cmap="RdBu_r", vmin=-1, vmax=1)
a.set_xticks(range(len(SIG)), SIG, rotation=75, ha="right"); a.set_yticks(range(len(SIG)), SIG); a.grid(False)
for i in range(len(SIG)):
    for j in range(len(SIG)): a.text(j, i, f"{corr.iloc[i,j]:.1f}", ha="center", va="center", fontsize=6)
fig.colorbar(im); save("correlacao_bj")"""),
md("""## 5. Estados de operação

- **parado/marcha lenta:** motor ligado e velocidade < 1 km/h
- **cruzeiro:** velocidade ≥ 80 km/h
- **urbano/transição:** demais"""),
code("""def estado(d):
    d = on(d)
    return np.select([d.speed_kmh < 1, d.speed_kmh >= 80], ["parado", "cruzeiro"], "transição")
rows = {}
for nome, d in {"treino": bj, "teste": cc}.items():
    e = pd.Series(estado(d), index=on(d).index); g = on(d).groupby(e)
    rows[nome] = pd.DataFrame({"% tempo": g.size() / len(e) * 100, "rpm médio": g.rpm.mean(),
                               "consumo L/h": g.fuel_rate_lph.mean(), "carga %": g.load_pct.mean(),
                               "arrefec. °C": g.coolant_c.mean()}).round(1)
pd.concat(rows, axis=1)"""),
md("## 6. Relações físicas úteis para o regressor de valor esperado"),
code("""d = on(bj)
fig, ax = plt.subplots(1, 3, figsize=(14, 4))
for a, (x, y) in zip(ax, [("load_pct","fuel_rate_lph"), ("rpm","engine_torque_pct"), ("load_pct","coolant_c")]):
    h = a.hexbin(d[x], d[y], gridsize=40, mincnt=1, bins="log", cmap="viridis"); a.set_xlabel(x); a.set_ylabel(y)
save("relacoes_bj")"""),
md("## 7. Segmentos do cross-country"),
code("""g = cc.groupby("segment")
seg = pd.DataFrame({"início": g.ts.min(), "duração (h)": g.size() / 3600,
                    "distância (km)": g.odometer_km.max() - g.odometer_km.min(),
                    "combustível início %": g.fuel_level_pct.first(), "combustível fim %": g.fuel_level_pct.last(),
                    "vel. média km/h": g.speed_kmh.mean()}).round(1)
seg"""),
md("""## Conclusões

1. **O treino (BJ) é quase só cruzeiro:** 97,6% do tempo a ~100 km/h e ~2000 rpm, com arrefecimento em 90 ± 1 °C. Um detector de anomalias treinado só nele aprende um "normal" muito estreito.
2. **O teste tem estados que o treino quase não vê:** 4,2% parado/marcha lenta (0,7% no treino), partidas a frio (arrefecimento de 27 °C) e `gear` = −1 (ré). Esses casos legítimos seriam sinalizados como anomalia (falsos positivos).
3. **Relações físicas claras** (carga → consumo, RPM → torque) — bom para o regressor de valor esperado, que é robusto a mudanças de estado se tiver RPM, carga e velocidade como entradas.
4. **`fuel_level_pct`:** os 0% aparecem só nos primeiros segundos de cada segmento (inicialização do nó 0x31) e são tratados como ausentes. No restante o nível é consistente: 87 → 48% (seg. 0), 47 → 3% (seg. 2) e 100 → 78% (seg. 7), com reabastecimentos entre segmentos. No BJ o nível quase não varia. A tabela da seção 7 usa `first()`, que pega esses zeros de partida; interpretar com cuidado.
5. **Segmentos curtos** (1, 3, 4, 5, 6 do cross-country: ≤ 0,3 h) são manobras/paradas; os longos são os segmentos 0, 2 e 7 (4–6 h, 420–525 km).
"""),
]
nb = nbf.v4.new_notebook(); nb.cells = cells
nb.metadata["kernelspec"] = {"name": "python3", "display_name": "Python 3", "language": "python"}
nbf.write(nb, "notebooks/01_eda.ipynb")
