# Relatório de treinamento — empresa-a

Versão `20261114-152209` · janela 60 s / passo 10 s · taxa-alvo de falsos alarmes 1.0% · aquecimento 1200 s · semente 42 · 10 s

## 1. Validação dos dados

**Resultado: aprovado**

Avisos:
- [kenworth-t270-px7] estado 'parado (<1 km/h)' pouco representado (4.2% do tempo): mais falsos alarmes são esperados nele

### Grupo `kenworth-t270-px7`

- Veículos: t270-01
- Período: 2020-07-13 17:11:11+00:00 a 2020-07-14 17:53:01+00:00
- Horas: 16.07 no total, 15.57 com motor ligado, em 8 segmentos
- Estados de operação: parado (<1 km/h) 4.2%, transição 5.8%, rodovia (>=80 km/h) 90.0%

| Sinal | Usado | Ausente | Fora da faixa | Mín | Média | Máx |
|---|---|---|---|---|---|---|
| rpm | sim | 0.0% | 0.0% | 13.72 | 1941.1 | 2237.83 |
| speed_kmh | sim | 0.0% | 0.0% | 0.0 | 95.1 | 108.55 |
| load_pct | sim | 0.0% | 0.0% | 0.0 | 51.19 | 100.0 |
| engine_torque_pct | sim | 0.0% | 0.0% | 0.0 | 42.49 | 93.0 |
| pedal_pct | sim | 0.0% | 0.0% | 0.0 | 13.92 | 100.0 |
| gear | sim | 0.0% | 0.0% | -1.0 | 4.67 | 5.0 |
| coolant_c | sim | 0.0% | 0.0% | 27.0 | 89.39 | 95.0 |
| oil_kpa | sim | 0.0% | 0.0% | 0.0 | 340.09 | 402.0 |
| fuel_rate_lph | sim | 0.0% | 0.0% | 0.0 | 17.75 | 46.91 |
| boost_kpa | sim | 0.0% | 0.0% | 0.0 | 67.46 | 187.0 |
| intake_c | sim | 0.0% | 0.0% | 30.0 | 52.71 | 68.0 |
| battery_v | sim | 0.0% | 0.0% | 11.3 | 13.99 | 14.2 |
| fuel_level_pct | sim | 0.0% | 0.0% | 2.8 | 64.33 | 100.0 |
| odometer_km | sim | 0.0% | 0.0% | 424997.5 | 425723.49 | 426477.62 |

## 2. Modelo do grupo `kenworth-t270-px7`

- Sinais: rpm, speed_kmh, load_pct, engine_torque_pct, pedal_pct, gear, coolant_c, oil_kpa, fuel_rate_lph, boost_kpa, intake_c, battery_v, fuel_level_pct (65 features por janela)
- Janelas: 3857 de treino (3577 fora do aquecimento, usadas no ajuste), 1688 de teste (0 descartadas na fronteira)
- Treino: {'t270-01/seg0': 2036, 't270-01/seg2': 1821}
- Teste: {'t270-01/seg4': 79, 't270-01/seg5': 58, 't270-01/seg6': 53, 't270-01/seg7': 1498}

### Falsos alarmes em dados normais de teste

- Janelas avaliadas: 1378 de 1688 (18.4% fora do escopo por estarem no aquecimento)
- Combinado: **0.0%** das janelas (≈ 0.0 janelas com alarme por hora; alvo 1.0%)
- IsolationForest: 0.0% · valor esperado: 0.0%

### Modelos de valor esperado

| Alvo | MAE teste | R² teste | MAE validação cruzada | R² validação cruzada | Limiar do resíduo | Falsos alarmes |
|---|---|---|---|---|---|---|
| coolant_c_mean | 0.385 | -4.17 | 0.286 | 0.15 | 2.83 | 0.0% |
| oil_kpa_mean | 0.537 | 1.00 | 1.15 | 0.97 | 45.6 | 0.0% |
| fuel_rate_lph_mean | 0.176 | 0.99 | 0.288 | 0.98 | 4.52 | 0.0% |
| boost_kpa_mean | 9.22 | 0.48 | 6.11 | 0.80 | 26.4 | 0.0% |
| intake_c_mean | 5.68 | -12.27 | 3.71 | -0.15 | 17.8 | 0.0% |
| battery_v_mean | 0.0329 | -11.19 | 0.0103 | 0.46 | 0.063 | 0.0% |
| fuel_level_pct_slope | 0.00356 | -0.07 | 0.00453 | -0.16 | 0.0272 | 0.0% |
| rpm_diff_abs_mean | 0.173 | 0.97 | 0.868 | 0.86 | 39.4 | 0.0% |

### Detecção de falhas sintéticas (injetadas nos dados de teste)

Eventos de 300 s a cada 900 s; rampa na primeira metade. Janela positiva = ≥ 50% dentro do evento; janelas parcialmente sobrepostas são excluídas.

| Falha | Severidade | Precisão | Recall | F1 | AUC | Recall IF | Recall VE | Eventos detectados | Atraso mediano |
|---|---|---|---|---|---|---|---|---|---|
| overheating | 0.25 | 100.0% | 46.5% | 0.63 | 0.843 | 0.0% | 46.5% | 16/16 | 165 s |
| overheating | 0.5 | 100.0% | 78.1% | 0.88 | 0.932 | 0.0% | 78.1% | 16/16 | 100 s |
| overheating | 1.0 | 100.0% | 89.5% | 0.94 | 0.968 | 0.0% | 89.5% | 16/16 | 60 s |
| battery_drain | 0.25 | 100.0% | 90.3% | 0.95 | 0.944 | 0.0% | 90.3% | 16/16 | 60 s |
| battery_drain | 0.5 | 100.0% | 94.9% | 0.97 | 0.977 | 0.0% | 94.9% | 16/16 | 50 s |
| battery_drain | 1.0 | 100.0% | 99.2% | 1.00 | 1.000 | 0.0% | 99.2% | 16/16 | 30 s |
| oil_pressure_drop | 0.25 | — | 0.0% | 0.00 | 0.647 | 0.0% | 0.0% | 0/16 | — |
| oil_pressure_drop | 0.5 | 100.0% | 54.0% | 0.70 | 0.877 | 0.0% | 54.0% | 16/16 | 150 s |
| oil_pressure_drop | 1.0 | 100.0% | 80.9% | 0.89 | 0.945 | 0.0% | 80.9% | 16/16 | 90 s |
| fuel_overconsumption | 0.25 | — | 0.0% | 0.00 | 0.481 | 0.0% | 0.0% | 0/16 | — |
| fuel_overconsumption | 0.5 | — | 0.0% | 0.00 | 0.690 | 0.0% | 0.0% | 0/16 | — |
| fuel_overconsumption | 1.0 | 100.0% | 60.6% | 0.76 | 0.885 | 0.0% | 60.6% | 16/16 | 120 s |
| boost_leak | 0.25 | — | 0.0% | 0.00 | 0.613 | 0.0% | 0.0% | 0/16 | — |
| boost_leak | 0.5 | 100.0% | 4.3% | 0.08 | 0.788 | 0.0% | 4.3% | 7/16 | 200 s |
| boost_leak | 1.0 | 100.0% | 55.0% | 0.71 | 0.906 | 0.0% | 55.0% | 16/16 | 120 s |
| erratic_rpm | 0.25 | 100.0% | 52.3% | 0.69 | 0.849 | 0.0% | 52.3% | 16/16 | 150 s |
| erratic_rpm | 0.5 | 100.0% | 81.3% | 0.90 | 0.936 | 0.0% | 81.3% | 16/16 | 90 s |
| erratic_rpm | 1.0 | 100.0% | 91.3% | 0.95 | 0.969 | 0.0% | 91.3% | 16/16 | 60 s |
| fuel_leak | 0.25 | 100.0% | 1.0% | 0.02 | 0.526 | 0.0% | 1.0% | 2/16 | 115 s |
| fuel_leak | 0.5 | 100.0% | 7.1% | 0.13 | 0.796 | 0.0% | 7.1% | 11/16 | 80 s |
| fuel_leak | 1.0 | 100.0% | 92.1% | 0.96 | 0.990 | 0.0% | 92.1% | 16/16 | 40 s |

Falhas (severidade 1,0): `overheating` — arrefecimento sobe até +15 °C; `battery_drain` — tensão cai até -2,0 V; `oil_pressure_drop` — pressão do óleo cai até -35%; `fuel_overconsumption` — consumo sobe até +35%; `boost_leak` — pressão do turbo cai até -35%; `erratic_rpm` — ruído gaussiano no RPM (σ até 200 rpm); `fuel_leak` — nível de combustível perde 0,04 %/s.

IF = IsolationForest; VE = valor esperado. A precisão depende da proporção de janelas com falha no conjunto injetado (cerca de um terço), não da frequência real de falhas.

## 3. Limitações

- As falhas são sintéticas: medem a sensibilidade a desvios de magnitude conhecida, não a falhas reais.
- Não há detecção nos primeiros 1200 s após a partida do motor (aquecimento).
- O modelo vale só para os grupos de veículo e condições presentes nos dados de treino.
- Os limiares foram calibrados por validação cruzada em blocos temporais do treino; a taxa de falsos alarmes no teste pode diferir do alvo se a operação do teste for diferente.
