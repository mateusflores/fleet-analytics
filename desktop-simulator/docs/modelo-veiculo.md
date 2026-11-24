# Modelo de veículo do desktop-simulator

Documento de referência para o relatório: o que o simulador modela, com quais equações e parâmetros, como foi
calibrado e quais são as limitações. Código em `src/main/java/com/unesp/desktopsimulator/app/services/simulation/`.

## 1. Por que foi reescrito (2026-11-21)

O gerador anterior calculava cada sinal com uma fórmula isolada e ruído independente a cada segundo. Comparado com o
log CAN real do Kenworth T270 (15 h de motor ligado), ele tinha problemas que tiravam o realismo:

| Sinal | Gerador antigo | T270 real |
|---|---|---|
| RPM | 700 + 40 × velocidade, sem marchas; até 4.000 rpm | 800–2.240 rpm; dente de serra das trocas; 105 km/h ≈ 2.060 rpm |
| Velocidade | passeio aleatório (±3 km/h/s); 0→30 km/h em 1 s | aceleração entre −0,5 e +0,5 m/s² |
| Temperatura | 85 + até 35 °C conforme o RPM, ruído de ±1 °C/s | termostato: 88–92 °C; aquecimento lento na partida a frio |
| Combustível | ~25%/h parado, ~70%/h rodando | 2,6 L/h parado, 15–18 L/h em cruzeiro |
| Bateria | 13,8 V + ruído branco | 14,0 V estável (desvio 0,1 V) |
| Trajeto | direção aleatória a cada segundo | — |
| Falhas | somavam um valor fixo à saída (superaquecimento estabilizava em +3 °C) | — |

## 2. Arquitetura

```
Route (traçado, limite, rampa) ─┐
DriverStyle ────────────────────┼─► VehicleModel.step(dt) ─► VehicleSnapshot ─► SnapshotMapper ─► Telemetry / Position (MQTT)
VehicleProfile ─────────────────┤                                         └─► AlertMonitor ─► Message (MQTT)
FaultSet (UI) ──────────────────┘
```

- `VehicleModel`: estado do veículo integrado em subpassos de 0,2 s; um único estado gera posição e telemetria
  (antes eram dois geradores com relógios próprios).
- `VehicleSimulator`: laço de tempo real; avança o modelo pelo tempo decorrido (máx. 5 s por passo).
- `tools/DatasetGenerator`: o mesmo modelo sem interface, para gerar históricos (D31 do ml-service).
- Mesma semente ⇒ mesma simulação (teste `sameSeedReproducesTheSameSimulation`).

## 3. Rotas

Geradas uma vez por `tools/fetch_routes.py` e versionadas em `src/main/resources/routes/`:

| Rota | Tipo | Extensão | Altitude |
|---|---|---|---|
| Campinas → São Paulo (Bandeirantes) | rodovia | 90 km | 653–841 m |
| São Paulo → Santos (Anchieta) | serra | 72 km | 2–826 m |
| Entregas urbanas — Campinas | urbano | 21,5 km | 647–733 m |

- Traçado e velocidade estimada por trecho: OSRM (dados OpenStreetMap). O demo público do OSRM não expõe o
  `maxspeed` do OSM, então o limite é a velocidade estimada arredondada a 10 km/h; o simulador aplica por cima o
  limite do perfil (90 km/h) e um limitador de velocidade de frota (+3 km/h).
- Altitude: OpenTopoData (SRTM 90 m). Rampa calculada sobre ±500 m e limitada a ±8%; limite de velocidade suavizado
  por abertura e fechamento morfológicos de ±300 m.
- Paradas sorteadas por perna: semáforos (45% de chance a cada 350–900 m nos trechos ≤ 60 km/h, 15–60 s), pedágio
  na rodovia, duas entregas na rota urbana (2–5 min) e carga/descarga no fim (3–7 min). Depois o veículo faz a volta;
  reabastece no fim da perna se o tanque estiver abaixo de 25%.

## 4. Motorista

- Velocidade alvo = limite × fator do estilo + variação lenta (AR(1), τ = 60 s, σ = 0,6 m/s).
- Antecipação: olha 800 m à frente; a velocidade permitida a uma distância x é √(v_lim² + 2·b·x), com b a
  desaceleração de conforto; paradas entram com v_lim = 0.
- Aceleração pedida = (alvo − v)/3 s, limitada pelo estilo e com limite de *jerk*.
- Piloto automático (estilo econômico): em velocidade estável acima de 60 km/h por mais de 20 s o pedal fica em 0 —
  igual ao log do T270, em que o pedal tem mediana 0 em cruzeiro.

| Estilo | a máx. (m/s²) | b conforto (m/s²) | jerk (m/s³) | fator do limite | troca acima |
|---|---|---|---|---|---|
| Econômico | 0,35 | 0,8 | 0,5 | 0,95 | +0 rpm |
| Agressivo | 0,8 | 1,8 | 1,2 | 1,06 | +250 rpm |

## 5. Dinâmica, câmbio e motor

- Resistência ao movimento: F = m·g·(C_rr·cos θ + sen θ) + ½·ρ·C_dA·v², com massa efetiva 1,05·m (inércia rotativa).
- Força pedida F = m_ef·a + F_res → torque no motor T = F·r / (i_marcha·i_dif·η), limitado pelo torque disponível
  T_max(rpm) (subida a partir da marcha lenta, platô, queda por potência máxima e governador).
- RPM = v/r · 60/(2π) · i_marcha · i_dif; abaixo da marcha lenta, patinação do conversor (marcha lenta + 350·pedal).
  Marcha lenta 150 rpm mais alta com o motor frio.
- Trocas: sobe acima de `upshiftRpm` (se a marcha seguinte não cair abaixo de `downshiftRpm` + 50), pulando uma
  marcha quando a seguinte ainda fica 250 rpm acima do ponto de redução; desce abaixo de `downshiftRpm` (nunca abaixo
  da marcha de saída) e reduz quando o torque pedido passa de 97% do disponível (subida). Interrupção de torque de
  0,3 s (automático) ou 0,8 s (automatizado de 12 marchas); espera de 1 s após subir e 2,5 s após reduzir.
  Sem o pulo e com espera fixa de 2,5 s, o cavalo mecânico com motorista agressivo batia no limitador a cada
  arrancada (180 s em 2 h na serra); agora são ~5 s em 2 h, só em descidas fortes.
- Freio-motor / corte de combustível: desacelerando acima da marcha lenta + 150 rpm, consumo 0 e torque −8%.
- Carga (%) = torque / T_max(rpm); torque (%) = torque / torque de pico; ambos incluem 2,5 kW (×2 em 24 V) de acessórios.

## 6. Consumo, temperatura, ar, óleo e elétrica

- Consumo (L/h) = base·rpm/rpm_lenta + BSFC·P_eixo/(1000·0,835), em que a base é o consumo em marcha lenta sem os
  acessórios; BSFC até 15% pior abaixo de 30% de carga.
- Arrefecimento: C·dT/dt = 0,28·P_combustível − UA·η_rad·abertura(T)·(T − T_amb)·ar − 0,08·(T − T_amb).
  Termostato abre de 86 a 96 °C; fluxo de ar = 0,35 + 0,65·min(1, v/22 m/s), mais 1,2 com a ventoinha
  (liga a 97 °C, desliga a 93 °C); limite físico de 125 °C (alívio da tampa).
- Admissão: T_amb + 10 + 32·carga + 6·(1 − fluxo de ar), atraso de 40 s.
- Turbo: P_max·carga^1,25·enchimento(rpm), atraso de 1,5 s.
- Óleo: p_lenta + (p_nominal − p_lenta)·x^0,6 (x = posição do rpm entre a marcha lenta e a rotação nominal),
  +25% com o óleo frio.
- Elétrica: com o alternador normal, 14,0 V (28,0 V); sem ele, a tensão segue o estado de carga da bateria
  (OCV = 11,8 + 0,9·SOC por bloco de 12 V) descarregando com a corrente dos consumidores.
- Sensores: ruído AR(1) e resolução J1939 — arrefecimento e admissão 1 °C, óleo 4 kPa, turbo 2 kPa, nível de
  combustível 0,4% (com balanço proporcional à aceleração), tensão 0,05 V, consumo 0,05 L/h; GPS com erro de ~2,5 m.

## 7. Perfis

| Parâmetro | Caminhão médio (`RIGID_TRUCK`) | Cavalo mecânico (`ARTICULATED_VEHICLE`) |
|---|---|---|
| Massa vazio + carga máx. | 8 t + 4 t | 16 t + 29 t |
| C_dA / C_rr | 3,6 m² / 0,0065 | 6,0 m² / 0,0060 |
| Marchas | 5 (3,49 … 0,75), dif. 4,88 | 12 (14,94 … 1,00), dif. 3,08 |
| Rotação lenta / troca / máx. | 700 / 1.950 / 2.250 | 600 / 1.450 / 1.900 |
| Torque de pico / potência | 760 N·m / 179 kW | 2.300 N·m / 340 kW |
| Tanque / marcha lenta / BSFC | 380 L / 2,6 L/h / 195 g/kWh | 600 L / 3,0 L/h / 188 g/kWh |
| Sistema elétrico | 12 V | 24 V |

Os demais tipos da API (`PASSAGER_VEHICLE`, `AGRICULTURAL_VEHICLE`) usam o perfil médio.

## 8. Calibração com o T270 (perfil médio)

| Métrica | T270 real | Simulador |
|---|---|---|
| rpm/(km/h) por marcha (1ª … 5ª) | 102 / 51 / 39 / 28 / 19,7 | 92 / 49 / 37 / 26 / 19,8 |
| Consumo em cruzeiro (70–100 km/h, mediana) | 15,0 L/h (18,4 a 104 km/h) | 17,9 L/h |
| Consumo em marcha lenta | 2,6 L/h | 2,6 L/h |
| Arrefecimento (mediana, aquecido) | 90 °C | 88 °C |
| Tensão (mediana / variação de 1 s) | 14,0 V / 0,049 V | 14,0 V / 0,033 V |
| Média geral | 25–28 L/100 km (referência de mercado) | 24,8 L/100 km |

Diferenças esperadas: o T270 é uma viagem de longa distância nos EUA (4% do tempo parado, 105 km/h); as rotas do
simulador são brasileiras, com cidade e serra (22–26% parado, máx. 90 km/h), e o estilo agressivo acelera mais
(p99 0,8 m/s² contra 0,5 m/s²). O teste `mediumTruckMatchesT270Calibration` fixa essas faixas.

## 9. Falhas

Cada falha altera um parâmetro do modelo e cresce linearmente durante 120 s a partir da ativação. Intensidades
(`FaultSeverity`): leve 0,25, média 0,5, grave 1,0 — as mesmas da avaliação do ml-service.

| Falha | O que muda no modelo (× intensidade) | Efeito medido, leve → grave (1 h após a ativação, mesma semente) |
|---|---|---|
| Superaquecimento | eficiência do radiador −92% | temperatura média +0,5 °C → até 125 °C (grave chega ao alarme fixo) |
| Falha no alternador | alternador deixa de carregar | 14,0 V → 13,65 V (leve) / 12,3 V (grave) |
| Vazamento de combustível | +40 L/h saindo do tanque, fora do consumo | nível −2,4 → −10 pontos percentuais em 55 min |
| Rotação irregular | oscilação de 120 rpm + ruído de 100 rpm; −12% de potência | variação de 1 s 86 → 99 / 201 rpm |
| Queda de pressão do óleo | pressão −50% | 291 → 255 / 146 kPa |
| Vazamento no turbo | pressão do turbo −50%, potência −25% | 20,6 → 17,1 / 10,6 kPa (média com paradas) |
| Consumo excessivo | +35% de combustível para a mesma potência | 8,7 → 9,5 / 11,8 L/h |
| GPS impreciso | erro de posição de 2,5 + 30 m | precisão reportada > 20 m |

## 10. Alertas fixos (`AlertMonitor`)

Tipos de evento mantidos para compatibilidade com o frontend. Emitidos quando o nível sobe; rearmados só depois do
limite de retorno (histerese). Óleo e tensão só são avaliados 30 s após a partida.

| Evento | WARNING | CRITICAL | Rearma |
|---|---|---|---|
| `ENGINE_OVERHEAT` | ≥ 105 °C | ≥ 110 °C | < 100 °C |
| `LOW_OIL_PRESSURE` | ≤ 150 kPa | ≤ 90 kPa | > 190 kPa |
| `LOW_BATTERY` | ≤ 13,0 V (×2 em 24 V) | ≤ 12,2 V (×2) | > 13,4 V (×2) |
| `LOW_FUEL` | ≤ 15% | ≤ 8% | > 18% |
| `RPM_LIMIT` | ≥ rotação máx. − 20 | — | < rotação máx. − 200 |

## 11. Gerar um histórico (sem interface)

```bash
cd desktop-simulator
./mvnw -q compile exec:java -Dexec.mainClass=com.unesp.desktopsimulator.tools.DatasetGenerator \
    -Dexec.args="--out ../ml-service/data/synthetic/empresa-b.csv --vehicles 3 --days 3 --seed 42"
# opções: --profile heavy · --fault OVERHEATING:SEVERE:3600[:sim-01] (várias separadas por vírgula)
```

Cada veículo opera ~11 h por dia em viagens de 1,5–3,5 h por rotas sorteadas, com pausas de motor desligado de
30–90 min (partida a frio após pausas de mais de 45 min e no início do dia). Desempenho: ~18 h simuladas em ~3 s.

## 12. Limitações

- Física deliberadamente simplificada (modelo longitudinal, sem curvas, vento, pneus ou temperatura do óleo); o
  objetivo é plausibilidade e coerência entre sinais, não precisão de engenharia.
- Calibração feita com um único veículo real (T270); o perfil pesado usa valores típicos de catálogo, não um log.
- Limites de velocidade estimados (não o `maxspeed` do OSM); altitude SRTM de 90 m.
- O consumo em cruzeiro fica ~15–20% acima do T270 a 70–100 km/h.
