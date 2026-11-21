# Relatório de treinamento — empresa-b

Versão `20261114-152220` · janela 60 s / passo 10 s · taxa-alvo de falsos alarmes 1.0% · aquecimento 1200 s · semente 42 · 6 s

## 1. Validação dos dados

**Resultado: aprovado**

Avisos:
- [simulador-leve] estado 'parado (<1 km/h)' pouco representado (3.7% do tempo): mais falsos alarmes são esperados nele

### Grupo `simulador-leve`

- Veículos: sim-01, sim-02, sim-03
- Período: 2026-10-01 08:00:00+00:00 a 2026-10-02 02:16:53+00:00
- Horas: 24.0 no total, 24.0 com motor ligado, em 13 segmentos
- Estados de operação: parado (<1 km/h) 3.7%, transição 88.8%, rodovia (>=80 km/h) 7.5%

| Sinal | Usado | Ausente | Fora da faixa | Mín | Média | Máx |
|---|---|---|---|---|---|---|
| rpm | sim | 0.0% | 0.0% | 700.0 | 3168.57 | 3996.0 |
| speed_kmh | sim | 0.0% | 0.0% | 0.0 | 61.73 | 82.96 |
| coolant_c | sim | 0.0% | 0.0% | 83.98 | 111.17 | 120.83 |
| battery_v | sim | 0.0% | 0.0% | 13.34 | 13.8 | 14.21 |
| fuel_level_pct | sim | 0.0% | 0.0% | 8.0 | 55.04 | 100.0 |
| odometer_km | sim | 0.0% | 0.0% | 19069.53 | 61387.78 | 98615.26 |

## 2. Modelo do grupo `simulador-leve`

- Sinais: rpm, speed_kmh, coolant_c, battery_v, fuel_level_pct (25 features por janela)
- Janelas: 6411 de treino (5277 fora do aquecimento, usadas no ajuste), 2141 de teste (18 descartadas na fronteira)
- Treino: {'sim-01/seg0': 1000, 'sim-01/seg1': 730, 'sim-01/seg2': 408, 'sim-02/seg0': 533, 'sim-02/seg1': 947, 'sim-02/seg2': 605, 'sim-02/seg3': 54, 'sim-03/seg0': 838, 'sim-03/seg1': 952, 'sim-03/seg2': 344}
- Teste: {'sim-01/seg2': 22, 'sim-01/seg3': 692, 'sim-02/seg3': 714, 'sim-03/seg2': 25, 'sim-03/seg3': 661, 'sim-03/seg4': 27}

### Falsos alarmes em dados normais de teste

- Janelas avaliadas: 1814 de 2141 (15.3% fora do escopo por estarem no aquecimento)
- Combinado: **1.1%** das janelas (≈ 4.0 janelas com alarme por hora; alvo 1.0%)
- IsolationForest: 0.0% · valor esperado: 1.1%

### Modelos de valor esperado

| Alvo | MAE teste | R² teste | MAE validação cruzada | R² validação cruzada | Limiar do resíduo | Falsos alarmes |
|---|---|---|---|---|---|---|
| coolant_c_mean | 0.131 | 1.00 | 0.149 | 1.00 | 0.787 | 0.7% |
| battery_v_mean | 0.0114 | -0.12 | 0.0111 | -0.11 | 0.0463 | 0.1% |
| fuel_level_pct_slope | 9.5e-05 | 1.00 | 0.000116 | 1.00 | 0.00104 | 0.2% |
| rpm_diff_abs_mean | 1.24 | 0.90 | 1.24 | 0.91 | 5.83 | 0.2% |

### Detecção de falhas sintéticas (injetadas nos dados de teste)

Eventos de 300 s a cada 900 s; rampa na primeira metade. Janela positiva = ≥ 50% dentro do evento; janelas parcialmente sobrepostas são excluídas.

| Falha | Severidade | Precisão | Recall | F1 | AUC | Recall IF | Recall VE | Eventos detectados | Atraso mediano |
|---|---|---|---|---|---|---|---|---|---|
| overheating | 0.25 | 98.0% | 89.1% | 0.93 | 0.974 | 0.0% | 89.1% | 20/20 | 65 s |
| overheating | 0.5 | 98.1% | 94.5% | 0.96 | 0.991 | 0.0% | 94.5% | 20/20 | 50 s |
| overheating | 1.0 | 98.2% | 98.4% | 0.98 | 0.999 | 0.0% | 98.4% | 20/20 | 35 s |
| battery_drain | 0.25 | 98.2% | 95.8% | 0.97 | 0.992 | 0.0% | 95.8% | 20/20 | 45 s |
| battery_drain | 0.5 | 98.2% | 99.0% | 0.99 | 1.000 | 0.0% | 99.0% | 20/20 | 30 s |
| battery_drain | 1.0 | 98.2% | 100.0% | 0.99 | 1.000 | 0.0% | 100.0% | 20/20 | 20 s |
| erratic_rpm | 0.25 | 98.0% | 86.6% | 0.92 | 0.961 | 0.0% | 86.6% | 20/20 | 70 s |
| erratic_rpm | 0.5 | 98.1% | 95.0% | 0.97 | 0.992 | 0.0% | 95.0% | 20/20 | 50 s |
| erratic_rpm | 1.0 | 98.2% | 99.2% | 0.99 | 1.000 | 0.0% | 99.2% | 20/20 | 30 s |
| fuel_leak | 0.25 | 98.2% | 100.0% | 0.99 | 1.000 | 0.0% | 100.0% | 20/20 | 20 s |
| fuel_leak | 0.5 | 98.2% | 100.0% | 0.99 | 1.000 | 0.0% | 100.0% | 20/20 | 10 s |
| fuel_leak | 1.0 | 98.2% | 100.0% | 0.99 | 1.000 | 0.0% | 100.0% | 20/20 | 10 s |

Falhas (severidade 1,0): `overheating` — arrefecimento sobe até +15 °C; `battery_drain` — tensão cai até -2,0 V; `erratic_rpm` — ruído gaussiano no RPM (σ até 200 rpm); `fuel_leak` — nível de combustível perde 0,04 %/s.

IF = IsolationForest; VE = valor esperado. A precisão depende da proporção de janelas com falha no conjunto injetado (cerca de um terço), não da frequência real de falhas.

## 3. Limitações

- As falhas são sintéticas: medem a sensibilidade a desvios de magnitude conhecida, não a falhas reais.
- Não há detecção nos primeiros 1200 s após a partida do motor (aquecimento).
- O modelo vale só para os grupos de veículo e condições presentes nos dados de treino.
- Os limiares foram calibrados por validação cruzada em blocos temporais do treino; a taxa de falsos alarmes no teste pode diferir do alvo se a operação do teste for diferente.
