# Relatório de treinamento — empresa-b

Versão `20261121-143230` · janela 60 s / passo 10 s · taxa-alvo de falsos alarmes 1.0% · aquecimento 1200 s · semente 42 · 21 s

## 1. Validação dos dados

**Resultado: aprovado**

### Grupo `caminhao-medio-sim`

- Veículos: sim-01, sim-02, sim-03
- Período: 2026-11-02 09:26:59+00:00 a 2026-11-04 22:48:11+00:00
- Horas: 81.18 no total, 81.18 com motor ligado, em 33 segmentos
- Estados de operação: parado (<1 km/h) 22.0%, transição 57.4%, rodovia (>=80 km/h) 20.6%

| Sinal | Usado | Ausente | Fora da faixa | Mín | Média | Máx |
|---|---|---|---|---|---|---|
| rpm | sim | 0.0% | 0.0% | 467.0 | 1393.25 | 2209.0 |
| speed_kmh | sim | 0.0% | 0.0% | 0.0 | 43.48 | 93.0 |
| load_pct | sim | 0.0% | 0.0% | 0.0 | 25.27 | 100.0 |
| engine_torque_pct | sim | 0.0% | 0.0% | -8.0 | 22.97 | 100.0 |
| pedal_pct | sim | 0.0% | 0.0% | 0.0 | 21.96 | 100.0 |
| gear | sim | 0.0% | 0.0% | 1.0 | 2.99 | 5.0 |
| coolant_c | sim | 0.0% | 0.0% | 20.0 | 85.24 | 95.0 |
| oil_kpa | sim | 0.0% | 0.0% | 124.0 | 297.63 | 436.0 |
| fuel_rate_lph | sim | 0.0% | 0.0% | 0.0 | 10.59 | 48.0 |
| boost_kpa | sim | 0.0% | 0.0% | 0.0 | 29.18 | 156.0 |
| intake_c | sim | 0.0% | 0.0% | 29.0 | 45.08 | 69.0 |
| battery_v | sim | 0.0% | 0.0% | 13.85 | 14.0 | 14.15 |
| fuel_level_pct | sim | 0.0% | 0.0% | 20.0 | 61.35 | 98.0 |
| odometer_km | sim | 0.0% | 0.0% | 234265.76 | 234873.98 | 235566.8 |

## 2. Modelo do grupo `caminhao-medio-sim`

- Sinais: rpm, speed_kmh, load_pct, engine_torque_pct, pedal_pct, gear, coolant_c, oil_kpa, fuel_rate_lph, boost_kpa, intake_c, battery_v, fuel_level_pct (65 features por janela)
- Janelas: 21766 de treino (18766 fora do aquecimento, usadas no ajuste), 7260 de teste (18 descartadas na fronteira)
- Treino: {'sim-01/seg0': 1093, 'sim-01/seg1': 980, 'sim-01/seg2': 902, 'sim-01/seg3': 606, 'sim-01/seg4': 1193, 'sim-01/seg5': 897, 'sim-01/seg6': 1041, 'sim-01/seg7': 693, 'sim-02/seg0': 1021, 'sim-02/seg1': 1068, 'sim-02/seg2': 563, 'sim-02/seg3': 841, 'sim-02/seg4': 1094, 'sim-02/seg5': 641, 'sim-02/seg6': 1194, 'sim-02/seg7': 901, 'sim-03/seg0': 1237, 'sim-03/seg1': 804, 'sim-03/seg2': 858, 'sim-03/seg3': 868, 'sim-03/seg4': 671, 'sim-03/seg5': 688, 'sim-03/seg6': 734, 'sim-03/seg7': 959, 'sim-03/seg8': 219}
- Teste: {'sim-01/seg7': 69, 'sim-01/seg8': 582, 'sim-01/seg9': 804, 'sim-01/seg10': 1015, 'sim-02/seg7': 126, 'sim-02/seg8': 687, 'sim-02/seg9': 922, 'sim-02/seg10': 707, 'sim-03/seg8': 867, 'sim-03/seg9': 569, 'sim-03/seg10': 912}

### Falsos alarmes em dados normais de teste

- Janelas avaliadas: 6300 de 7260 (13.2% fora do escopo por estarem no aquecimento)
- Combinado: **0.4%** das janelas (≈ 1.3 janelas com alarme por hora; alvo 1.0%)
- IsolationForest: 0.0% · valor esperado: 0.4%

### Modelos de valor esperado

| Alvo | MAE teste | R² teste | MAE validação cruzada | R² validação cruzada | Limiar do resíduo | Falsos alarmes |
|---|---|---|---|---|---|---|
| coolant_c_mean | 0.483 | 0.82 | 0.55 | 0.71 | 7.68 | 0.1% |
| oil_kpa_mean | 0.5 | 1.00 | 0.507 | 1.00 | 6.78 | 0.0% |
| fuel_rate_lph_mean | 0.192 | 1.00 | 0.204 | 1.00 | 3.34 | 0.0% |
| boost_kpa_mean | 0.784 | 1.00 | 0.761 | 1.00 | 13.6 | 0.0% |
| intake_c_mean | 2.91 | 0.65 | 2.86 | 0.62 | 23.9 | 0.0% |
| battery_v_mean | 0.00861 | -0.02 | 0.00902 | -0.02 | 0.0817 | 0.0% |
| fuel_level_pct_slope | 0.00404 | -0.02 | 0.00403 | 0.01 | 0.0522 | 0.2% |
| rpm_diff_abs_mean | 3.06 | 0.97 | 4.03 | 0.83 | 104 | 0.0% |

### Detecção de falhas sintéticas (injetadas nos dados de teste)

Eventos de 300 s a cada 900 s; rampa na primeira metade. Janela positiva = ≥ 50% dentro do evento; janelas parcialmente sobrepostas são excluídas.

| Falha | Severidade | Precisão | Recall | F1 | AUC | Recall IF | Recall VE | Eventos detectados | Atraso mediano |
|---|---|---|---|---|---|---|---|---|---|
| overheating | 0.25 | 45.5% | 0.5% | 0.01 | 0.836 | 0.0% | 0.5% | 3/72 | 60 s |
| overheating | 0.5 | 96.0% | 13.1% | 0.23 | 0.928 | 0.0% | 13.1% | 60/72 | 200 s |
| overheating | 1.0 | 99.3% | 73.1% | 0.84 | 0.969 | 0.0% | 73.1% | 72/72 | 110 s |
| battery_drain | 0.25 | 99.4% | 91.5% | 0.95 | 0.993 | 0.0% | 91.5% | 72/72 | 60 s |
| battery_drain | 0.5 | 99.4% | 96.6% | 0.98 | 1.000 | 0.0% | 96.6% | 72/72 | 40 s |
| battery_drain | 1.0 | 99.5% | 99.9% | 1.00 | 1.000 | 0.0% | 99.9% | 72/72 | 30 s |
| oil_pressure_drop | 0.25 | 99.4% | 87.1% | 0.93 | 0.982 | 0.0% | 87.1% | 72/72 | 70 s |
| oil_pressure_drop | 0.5 | 99.4% | 93.6% | 0.96 | 0.997 | 0.0% | 93.6% | 72/72 | 50 s |
| oil_pressure_drop | 1.0 | 99.4% | 97.5% | 0.98 | 1.000 | 0.0% | 97.5% | 72/72 | 40 s |
| fuel_overconsumption | 0.25 | 73.3% | 1.5% | 0.03 | 0.623 | 0.0% | 1.5% | 8/72 | 160 s |
| fuel_overconsumption | 0.5 | 96.1% | 13.2% | 0.23 | 0.742 | 0.0% | 13.2% | 35/72 | 190 s |
| fuel_overconsumption | 1.0 | 98.5% | 36.8% | 0.54 | 0.841 | 0.0% | 36.8% | 57/72 | 130 s |
| boost_leak | 0.25 | 62.5% | 0.9% | 0.02 | 0.562 | 0.0% | 0.9% | 8/72 | 235 s |
| boost_leak | 0.5 | 94.0% | 8.6% | 0.16 | 0.649 | 0.0% | 8.6% | 27/72 | 200 s |
| boost_leak | 1.0 | 97.9% | 25.0% | 0.40 | 0.732 | 0.0% | 25.0% | 44/72 | 140 s |
| erratic_rpm | 0.25 | 45.5% | 0.5% | 0.01 | 0.782 | 0.0% | 0.5% | 2/72 | 40 s |
| erratic_rpm | 0.5 | 97.2% | 19.1% | 0.32 | 0.907 | 0.0% | 19.1% | 62/72 | 195 s |
| erratic_rpm | 1.0 | 99.2% | 70.6% | 0.83 | 0.957 | 0.0% | 70.6% | 72/72 | 110 s |
| fuel_leak | 0.25 | 45.5% | 0.5% | 0.01 | 0.574 | 0.0% | 0.5% | 2/72 | 40 s |
| fuel_leak | 0.5 | 45.5% | 0.5% | 0.01 | 0.857 | 0.0% | 0.5% | 2/72 | 40 s |
| fuel_leak | 1.0 | 75.5% | 1.7% | 0.03 | 0.986 | 0.0% | 1.7% | 17/72 | 220 s |

Falhas (severidade 1,0): `overheating` — arrefecimento sobe até +15 °C; `battery_drain` — tensão cai até -2,0 V; `oil_pressure_drop` — pressão do óleo cai até -35%; `fuel_overconsumption` — consumo sobe até +35%; `boost_leak` — pressão do turbo cai até -35%; `erratic_rpm` — ruído gaussiano no RPM (σ até 200 rpm); `fuel_leak` — nível de combustível perde 0,04 %/s.

IF = IsolationForest; VE = valor esperado. A precisão depende da proporção de janelas com falha no conjunto injetado (cerca de um terço), não da frequência real de falhas.

## 3. Limitações

- As falhas são sintéticas: medem a sensibilidade a desvios de magnitude conhecida, não a falhas reais.
- Não há detecção nos primeiros 1200 s após a partida do motor (aquecimento).
- O modelo vale só para os grupos de veículo e condições presentes nos dados de treino.
- Os limiares foram calibrados por validação cruzada em blocos temporais do treino; a taxa de falsos alarmes no teste pode diferir do alvo se a operação do teste for diferente.
