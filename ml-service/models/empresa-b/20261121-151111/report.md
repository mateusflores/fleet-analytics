# Relatório de treinamento — empresa-b

Versão `20261121-151111` · janela 60 s / passo 10 s · taxa-alvo de falsos alarmes 1.0% · aquecimento 1200 s · semente 42 · 22 s

## 1. Validação dos dados

**Resultado: aprovado**

### Grupo `caminhao-medio-sim`

- Veículos: sim-01, sim-02, sim-03
- Período: 2026-11-02 09:26:59+00:00 a 2026-11-04 22:48:11+00:00
- Horas: 81.18 no total, 81.18 com motor ligado, em 33 segmentos
- Estados de operação: parado (<1 km/h) 23.2%, transição 56.2%, rodovia (>=80 km/h) 20.6%

| Sinal | Usado | Ausente | Fora da faixa | Mín | Média | Máx |
|---|---|---|---|---|---|---|
| rpm | sim | 0.0% | 0.0% | 467.0 | 1385.66 | 2209.0 |
| speed_kmh | sim | 0.0% | 0.0% | 0.0 | 42.94 | 93.0 |
| load_pct | sim | 0.0% | 0.0% | 0.0 | 24.83 | 100.0 |
| engine_torque_pct | sim | 0.0% | 0.0% | -8.0 | 22.54 | 100.0 |
| pedal_pct | sim | 0.0% | 0.0% | 0.0 | 21.37 | 100.0 |
| gear | sim | 0.0% | 0.0% | 1.0 | 2.96 | 5.0 |
| coolant_c | sim | 0.0% | 0.0% | 20.0 | 85.21 | 95.0 |
| oil_kpa | sim | 0.0% | 0.0% | 124.0 | 296.6 | 436.0 |
| fuel_rate_lph | sim | 0.0% | 0.0% | 0.0 | 10.47 | 48.0 |
| boost_kpa | sim | 0.0% | 0.0% | 0.0 | 28.54 | 156.0 |
| intake_c | sim | 0.0% | 0.0% | 29.0 | 44.99 | 69.0 |
| battery_v | sim | 0.0% | 0.0% | 13.85 | 14.0 | 14.15 |
| fuel_level_pct | sim | 0.0% | 0.0% | 20.0 | 61.93 | 98.0 |
| odometer_km | sim | 0.0% | 0.0% | 234265.76 | 234859.85 | 235557.77 |

## 2. Modelo do grupo `caminhao-medio-sim`

- Sinais: rpm, speed_kmh, load_pct, engine_torque_pct, pedal_pct, gear, coolant_c, oil_kpa, fuel_rate_lph, boost_kpa, intake_c, battery_v, fuel_level_pct (65 features por janela)
- Janelas: 21766 de treino (18766 fora do aquecimento, usadas no ajuste), 7260 de teste (18 descartadas na fronteira)
- Treino: {'sim-01/seg0': 1093, 'sim-01/seg1': 980, 'sim-01/seg2': 902, 'sim-01/seg3': 606, 'sim-01/seg4': 1193, 'sim-01/seg5': 897, 'sim-01/seg6': 1041, 'sim-01/seg7': 693, 'sim-02/seg0': 1021, 'sim-02/seg1': 1068, 'sim-02/seg2': 563, 'sim-02/seg3': 841, 'sim-02/seg4': 1094, 'sim-02/seg5': 641, 'sim-02/seg6': 1194, 'sim-02/seg7': 901, 'sim-03/seg0': 1237, 'sim-03/seg1': 804, 'sim-03/seg2': 858, 'sim-03/seg3': 868, 'sim-03/seg4': 671, 'sim-03/seg5': 688, 'sim-03/seg6': 734, 'sim-03/seg7': 959, 'sim-03/seg8': 219}
- Teste: {'sim-01/seg7': 69, 'sim-01/seg8': 582, 'sim-01/seg9': 804, 'sim-01/seg10': 1015, 'sim-02/seg7': 126, 'sim-02/seg8': 687, 'sim-02/seg9': 922, 'sim-02/seg10': 707, 'sim-03/seg8': 867, 'sim-03/seg9': 569, 'sim-03/seg10': 912}

### Falsos alarmes em dados normais de teste

- Janelas avaliadas: 6300 de 7260 (13.2% fora do escopo por estarem no aquecimento)
- Combinado: **0.3%** das janelas (≈ 1.0 janelas com alarme por hora; alvo 1.0%)
- IsolationForest: 0.0% · valor esperado: 0.3%

### Modelos de valor esperado

| Alvo | MAE teste | R² teste | MAE validação cruzada | R² validação cruzada | Limiar do resíduo | Falsos alarmes |
|---|---|---|---|---|---|---|
| coolant_c_mean | 0.473 | 0.81 | 0.544 | 0.71 | 6.83 | 0.1% |
| oil_kpa_mean | 0.51 | 1.00 | 0.504 | 1.00 | 6.15 | 0.1% |
| fuel_rate_lph_mean | 0.187 | 1.00 | 0.193 | 1.00 | 2.7 | 0.0% |
| boost_kpa_mean | 0.735 | 1.00 | 0.694 | 1.00 | 9.28 | 0.0% |
| intake_c_mean | 2.93 | 0.63 | 2.89 | 0.60 | 21.3 | 0.0% |
| battery_v_mean | 0.0089 | -0.03 | 0.00897 | -0.02 | 0.0733 | 0.0% |
| fuel_level_pct_slope | 0.004 | -0.01 | 0.00421 | 0.01 | 0.0551 | 0.2% |
| rpm_diff_abs_mean | 3.62 | 0.97 | 4.76 | 0.77 | 151 | 0.0% |

### Detecção de falhas sintéticas (injetadas nos dados de teste)

Eventos de 300 s a cada 900 s; rampa na primeira metade. Janela positiva = ≥ 50% dentro do evento; janelas parcialmente sobrepostas são excluídas.

| Falha | Severidade | Precisão | Recall | F1 | AUC | Recall IF | Recall VE | Eventos detectados | Atraso mediano |
|---|---|---|---|---|---|---|---|---|---|
| overheating | 0.25 | 61.1% | 0.5% | 0.01 | 0.830 | 0.0% | 0.5% | 4/72 | 140 s |
| overheating | 0.5 | 99.2% | 40.6% | 0.58 | 0.926 | 0.0% | 40.6% | 72/72 | 180 s |
| overheating | 1.0 | 99.6% | 77.4% | 0.87 | 0.966 | 0.0% | 77.4% | 72/72 | 100 s |
| battery_drain | 0.25 | 99.7% | 92.4% | 0.96 | 0.993 | 0.0% | 92.4% | 72/72 | 60 s |
| battery_drain | 0.5 | 99.7% | 97.1% | 0.98 | 1.000 | 0.0% | 97.1% | 72/72 | 40 s |
| battery_drain | 1.0 | 99.7% | 100.0% | 1.00 | 1.000 | 0.0% | 100.0% | 72/72 | 30 s |
| oil_pressure_drop | 0.25 | 99.6% | 87.9% | 0.93 | 0.981 | 0.0% | 87.9% | 72/72 | 70 s |
| oil_pressure_drop | 0.5 | 99.7% | 93.7% | 0.97 | 0.997 | 0.0% | 93.7% | 72/72 | 50 s |
| oil_pressure_drop | 1.0 | 99.7% | 97.8% | 0.99 | 1.000 | 0.0% | 97.8% | 72/72 | 40 s |
| fuel_overconsumption | 0.25 | 90.1% | 2.9% | 0.06 | 0.634 | 0.0% | 2.9% | 12/72 | 175 s |
| fuel_overconsumption | 0.5 | 98.4% | 18.9% | 0.32 | 0.759 | 0.0% | 18.9% | 42/72 | 170 s |
| fuel_overconsumption | 1.0 | 99.3% | 43.8% | 0.61 | 0.856 | 0.0% | 43.8% | 66/72 | 130 s |
| boost_leak | 0.25 | 91.1% | 3.3% | 0.06 | 0.592 | 0.0% | 3.3% | 14/72 | 150 s |
| boost_leak | 0.5 | 98.0% | 15.5% | 0.27 | 0.680 | 0.0% | 15.5% | 36/72 | 145 s |
| boost_leak | 1.0 | 99.1% | 35.0% | 0.52 | 0.757 | 0.0% | 35.0% | 56/72 | 125 s |
| erratic_rpm | 0.25 | 58.8% | 0.5% | 0.01 | 0.620 | 0.0% | 0.5% | 2/72 | 40 s |
| erratic_rpm | 0.5 | 58.8% | 0.5% | 0.01 | 0.850 | 0.0% | 0.5% | 2/72 | 40 s |
| erratic_rpm | 1.0 | 99.4% | 54.2% | 0.70 | 0.932 | 0.0% | 54.2% | 71/72 | 140 s |
| fuel_leak | 0.25 | 58.8% | 0.5% | 0.01 | 0.545 | 0.0% | 0.5% | 2/72 | 40 s |
| fuel_leak | 0.5 | 58.8% | 0.5% | 0.01 | 0.774 | 0.0% | 0.5% | 2/72 | 40 s |
| fuel_leak | 1.0 | 73.1% | 0.9% | 0.02 | 0.979 | 0.0% | 0.9% | 8/72 | 165 s |

Falhas (severidade 1,0): `overheating` — arrefecimento sobe até +15 °C; `battery_drain` — tensão cai até -2,0 V; `oil_pressure_drop` — pressão do óleo cai até -35%; `fuel_overconsumption` — consumo sobe até +35%; `boost_leak` — pressão do turbo cai até -35%; `erratic_rpm` — ruído gaussiano no RPM (σ até 200 rpm); `fuel_leak` — nível de combustível perde 0,04 %/s.

IF = IsolationForest; VE = valor esperado. A precisão depende da proporção de janelas com falha no conjunto injetado (cerca de um terço), não da frequência real de falhas.

## 3. Limitações

- As falhas são sintéticas: medem a sensibilidade a desvios de magnitude conhecida, não a falhas reais.
- Não há detecção nos primeiros 1200 s após a partida do motor (aquecimento).
- O modelo vale só para os grupos de veículo e condições presentes nos dados de treino.
- Os limiares foram calibrados por validação cruzada em blocos temporais do treino; a taxa de falsos alarmes no teste pode diferir do alvo se a operação do teste for diferente.
