# Registro de experimentos e achados

Comandos executados a partir de `ml-service/` com `.venv/bin/python`.

## E01 — Decodificação J1939 (2026-11-02)
- **O quê:** parser candump + 14 sinais (SPNs) validados nos logs do Kenworth T270 2014.
- **Resultado:** valores fisicamente plausíveis (RPM médio ~2000, ~100 km/h, arrefecimento ~90 °C, bateria ~14 V, odômetro ~426 mil km).
- **Reproduzir:** `pytest tests/test_decoder.py`

## E02 — Dataset a 1 Hz (2026-11-02)
- **Comando:** `python -m ml_service.pipeline.build_dataset <log>`
- **Resultado:** cross-country (1,9 GB, 37,7 M mensagens) → 57.846 s em 8 segmentos, 96,9% com motor ligado, ~1 min de processamento.

## E03 — Análise exploratória (2026-11-02)
- **Notebook:** `notebooks/01_eda.ipynb` (gerado por `notebooks/make_eda_notebook.py`); figuras em `reports/figures/`.
- **Achados:** operação quase só em cruzeiro de rodovia (~90% do tempo); marcha lenta e partida a frio raras; relações físicas claras (carga→consumo, RPM→torque).
- **⚠️ Pendência:** o notebook ainda compara "BJ × cross-country", que são os mesmos dados (ver E05). Refazer com o split de D11.

## E04 — Features por janela (2026-11-02)
- **Resultado na época:** 58 features; treino 3.267 janelas, validação 578, teste 1.688.
- **Substituído** pelo pipeline por empresa (E08): features dinâmicas e sem conjunto de validação separado. O comando antigo não existe mais.

## E05 — Erro corrigido: vazamento treino/teste (2026-11-02)
- `CSU090BJ`, `CSU090AT` e `CSU090B6` coincidem segundo a segundo com os segmentos 7, 3 e 4 do cross-country (≥ 99,99% de valores idênticos).
- O primeiro split treinava no BJ e testava no segmento 7. Corrigido em D10/D11 antes de treinar qualquer modelo.

## E06 — Kenworth T660 2015 (2026-11-02)
- Mesmo decodificador funciona sem alterações (outro motor: PACCAR MX-13).
- Apenas 412 s, baixa velocidade (0–55 km/h), RPM 570–1790, consumo médio 9,8 L/h — faixas bem diferentes do T270.
- **Uso previsto:** evidência de que o "normal" de um veículo não vale para outro (justifica D04/D18).

## E07 — Scania Component X, baseline (2026-11-02)
- **Dados:** 23.550 veículos no treino (9,6% com reparo), 1,12 M leituras, 105 colunas (8 contadores + 6 histogramas), valores acumulados.
- **Comando:** `python -m ml_service.scania.train` (17 s). 260 features (taxas e distribuições, vida inteira e recentes).
- **Resultado (custo IDA 2024, menor é melhor):**

| Conjunto | Em risco | Custo "sempre 0" | Custo do modelo | Redução | Falhas detectadas | Falsos alarmes |
|---|---|---|---|---|---|---|
| Validação | 136 / 5.046 | 57.400 | 33.904 | −41% | 73% | 1.759 |
| Teste | 142 / 5.045 | 56.100 | 36.264 | −35% | 66% | 1.539 |

- **Limitações:** ~35% de falsos alarmes; o modelo separa "em risco × saudável", mas não a urgência (classes 1–3 quase não são usadas); sem ajuste de hiperparâmetros; não comparado aos resultados publicados do desafio.

## E08 — Pipeline por empresa: primeira execução e diagnóstico (2026-11-14)
- **Comando:** `python -m ml_service.train --config configs/empresa-a.yaml`
- **Primeira versão** (IsolationForest + valor esperado, limiar por detector com Bonferroni, sem tratar aquecimento):
  IsolationForest com recall 0% em todas as falhas; limiar do arrefecimento em 45,5 °C e da pressão do óleo em 112 kPa;
  superaquecimento de +15 °C não detectado (recall 0,7%).
- **Causa (verificada):** 99% dos resíduos do arrefecimento ficavam abaixo de ~1 °C, mas uma única partida a frio no
  treino (100 janelas, 93 delas no segmento 0) gerava resíduos de até 52 °C; o quantil 99,86% caía nessa cauda.
  As janelas mais anômalas para o IsolationForest eram marcha lenta com motor frio.
- **Correções:** D22 (calibração do alarme combinado), D23 (aquecimento fora do escopo), D24 (inclinação do combustível),
  D25 (irregularidade do RPM). Dois defeitos da avaliação também foram corrigidos: o vazamento sintético acumulava entre
  eventos até zerar o tanque, e eventos inteiros no aquecimento contavam como não detectados.

## E09 — Modelos demo das duas empresas (2026-11-14)
- **Comandos:** `python -m ml_service.synthetic.simulator_fleet` · `python -m ml_service.train --config configs/empresa-{a,b}.yaml`
- **Artefatos:** `models/empresa-a/20261114-152209/` e `models/empresa-b/20261114-152220/` (relatório completo em `report.md`).

| | Empresa A (Kenworth T270, real) | Empresa B (simulador, sintética) |
|---|---|---|
| Dados | 1 veículo, 15,6 h de motor ligado | 3 veículos, 24 h |
| Sinais / features | 13 / 64 | 5 / 24 |
| Janelas treino / teste | 3.857 / 1.688 | 6.411 / 2.141 |
| Teste fora do escopo (aquecimento) | 18,4% | 15,3% |
| Falsos alarmes em teste normal (alvo 1%) | 0,0% | 1,1% |
| Tempo de treino | 10 s (com cache da decodificação) | 6 s |

Recall por janela nas falhas sintéticas (severidade 0,25 / 0,5 / 1,0):

| Falha (severidade 1,0) | Empresa A | Empresa B |
|---|---|---|
| Superaquecimento (+15 °C) | 47% / 78% / 90% | 89% / 95% / 98% |
| Queda de tensão (−2 V) | 90% / 95% / 99% | 96% / 99% / 100% |
| Queda da pressão do óleo (−35%) | 0% / 54% / 81% | sem o sinal |
| Consumo elevado (+35%) | 0% / 0% / 61% | sem o sinal |
| Vazamento no turbo (−35%) | 0% / 4% / 55% | sem o sinal |
| RPM errático (σ 200 rpm) | 52% / 81% / 91% | 87% / 95% / 99% |
| Vazamento de combustível (0,04 %/s) | 1% / 7% / 92% | 100% / 100% / 100% |

- Precisão nas janelas injetadas: 100% (A) e ~98% (B). Na severidade 1,0 todos os eventos no escopo foram detectados
  (16/16 em A, 20/20 em B), com atraso mediano de 30–120 s (A) e 10–35 s (B).
- **Leituras:** (1) todas as detecções vêm dos modelos de valor esperado; o IsolationForest não detectou nada (D21).
  (2) Na Empresa A o modelo é conservador (0,0% de falsos alarmes contra alvo de 1%) e pouco sensível a desvios
  pequenos em óleo, consumo e turbo — os limiares desses sinais são largos (ex.: óleo 45,6 kPa para MAE de 0,5 kPa),
  sinal de cauda pesada nos resíduos da validação cruzada. (3) Os números da Empresa B são altos porque o simulador é
  quase determinístico; **não medem desempenho em veículo real**. (4) Modelos de `intake_c` e `battery_v` têm R²
  negativo no teste: não são previsíveis pelas condições de operação e funcionam só como faixa em torno da média.

## E10 — X1: transferência entre frotas (2026-11-14; refeito em 2026-11-21, ver E12)
- **Comando:** `python -m ml_service.experiments.transfer --source configs/empresa-a.yaml --targets configs/t660.yaml configs/empresa-b.yaml`
  (e o inverso com `--source configs/empresa-b.yaml`). Resultado em `reports/experiments/x1_transfer.json`.
- Modelo treinado só com os sinais comuns às duas frotas; alarmes medidos em dados **normais**.

| Modelo de | Aplicado a | Sinais comuns | Alarmes na própria frota (teste) | Alarmes na outra frota |
|---|---|---|---|---|
| Empresa A (T270) | Kenworth T660 | 12 | 0,0% (1.378 janelas) | **100%** (36 janelas) |
| Empresa A (T270) | Empresa B | 5 | 0,4% | **100%** (7.103 janelas) |
| Empresa B | Empresa A (T270) | 5 | 1,1% (1.814 janelas) | **100%** (4.955 janelas) |
| Empresa B | Kenworth T660 | 5 | 1,1% | **100%** (36 janelas) |

- **Conclusão:** o modelo de uma frota trata a operação normal de outra como anômala em todas as janelas — sustenta D04.
- **Refeito em 2026-11-21 com a nova Empresa B (E12, 13 sinais comuns):** A→B **61%** (25.084 janelas), B→A **87%**
  (4.955), B→T660 100%; na própria frota 0,0% (A) e 0,4% (B). A transferência deixou de ser total porque o simulador
  agora é calibrado com o próprio T270 — mesmo assim os alarmes ficam 2 ordens de grandeza acima da própria frota.
  Em A→B os alarmes vêm principalmente da pressão do óleo (42%); em B→A, de turbo (64%) e consumo (46%).
- **Ressalvas:** o T660 tem só 412 s (36 janelas) e assumimos motor quente (`assume_warm`); A↔B compara real com
  sintético. No T270→T660, os alarmes vêm de tensão da bateria e pressão do óleo (100%), arrefecimento (81%) e
  consumo (78%): são dois caminhões Kenworth com motores diferentes.

## E11 — Serviço de inferência e replay (2026-11-14)
- **Código:** `inference/engine.py` (motor, independente do broker), `inference/service.py` (RabbitMQ), `tools/replay.py`.
  O motor reutiliza o código de features do treino (`segment_windows`), então treino e inferência calculam o mesmo.
- **Replay a seco** (`--dry-run`, sem broker), trecho do segmento 7 do T270, falha iniciando em t = 120 s:

| Cenário | Janelas | Evento gerado | Instante |
|---|---|---|---|
| Sem falha (900 s) | 84 | nenhum | — |
| Superaquecimento | 54 | `ML_COOLANT_C`: arrefecimento 4,9 °C acima do esperado | 210 s |
| Queda da pressão do óleo | 54 | `ML_OIL_KPA`: 58,3 kPa abaixo do esperado | 220 s |
| RPM errático | 54 | `ML_RPM_ROUGHNESS` | 190 s |
| Vazamento de combustível | 54 | `ML_FUEL_LEVEL_PCT`: caindo 2,18 %/min além do esperado | 170 s |
| Empresa B, queda de tensão | 54 | `ML_BATTERY_V` (CRITICAL) | 160 s |

- **Ponta a ponta com RabbitMQ real** (contêiner temporário `rabbitmq:4-management-alpine`, sem a API): 600 mensagens
  publicadas em `telemetry.telemetry` chegaram a uma fila equivalente à da API; o serviço publicou em `telemetry.event`
  dois eventos `application/json` no formato do `EventMessage` (WARNING e depois CRITICAL).
- **Não testado:** a API Spring de verdade consumindo esses eventos, a exibição no frontend e a telemetria vinda do
  ESP32/simulador via MQTT. O simulador não envia `speedKmh` na telemetria (só na posição; o motor combina as duas).
- **Reproduzir:** ver seção 7 de `plano-modelo-por-empresa.md`.

## E12 — Empresa B com o novo simulador físico (2026-11-21)
- **Comandos:** `DatasetGenerator --vehicles 3 --days 3 --seed 42` (desktop-simulator, D31) · `python -m ml_service.train --config configs/empresa-b.yaml`
- **Artefato:** `models/empresa-b/20261121-143230/` (substitui `20261114-152220` como `LATEST`).
- **Dados:** 3 caminhões médios × 3 dias, 81 h de motor ligado em 33 segmentos (partidas a frio pela manhã e após
  pausas longas), 3 rotas reais (rodovia, serra, urbano), condução econômica (2) e agressiva (1); 13 sinais.
  Estados: parado 22%, transição 57%, rodovia 21% (o T270 é 4% parado — viagem de longa distância).
- **Falsos alarmes em teste normal:** 0,4% (alvo 1%); 13,2% das janelas no aquecimento.

| Falha | Recall 0,25 / 0,5 / 1,0 (novo) | Antes (E09, simulador antigo) |
|---|---|---|
| Superaquecimento | 0,5% / 13% / 73% | 89% / 95% / 98% |
| Queda de tensão | 92% / 97% / 100% | 96% / 99% / 100% |
| Queda da pressão do óleo | 87% / 94% / 98% | sem o sinal |
| Consumo elevado | 1,5% / 13% / 37% | sem o sinal |
| Vazamento no turbo | 1% / 9% / 25% | sem o sinal |
| RPM errático | 0,5% / 19% / 71% | 87% / 95% / 99% |
| Vazamento de combustível | 0,5% / 0,5% / 1,7% | 100% / 100% / 100% |

- **Leituras:** (1) os números caíram porque o novo simulador tem a variabilidade de operação real (paradas,
  trocas de marcha, rampas, dois estilos de condução) — os dados sintéticos agora reproduzem a mesma dificuldade da
  Empresa A. (2) O padrão de limiar largo se repete: MAE do arrefecimento 0,48 °C com limiar de 7,7 °C; consumo
  0,19 L/h com limiar de 3,3 L/h; turbo 0,78 kPa com limiar de 13,6 kPa — reforça o problema 5 (limiar por regime de
  operação). (3) O vazamento de 0,04 %/s não é detectado: o limiar da inclinação do nível (0,052 %/s) fica acima dele
  por causa da resolução de 0,4% do sensor e do balanço do combustível na janela de 60 s.

## Problemas em aberto
1. EDA desatualizada (E03).
2. O summary do T270 fala em 4 dias de viagem, mas os timestamps cobrem ~25 h.
3. O CSV VSpy da T660 (3,4 M mensagens segundo o summary, contra 137 mil no candump) não foi explorado.
4. DM1 (códigos de falha) ainda não decodificado.
5. Empresa A: baixa sensibilidade a desvios pequenos em óleo, consumo e turbo (limiares largos — investigar a cauda dos resíduos; testar persistência de alarme).
6. IsolationForest sem utilidade na configuração atual; comparar com autoencoder (X2).
7. Curva de aprendizado (X3: horas de dados × desempenho) não feita — o requisito de "≥ 10 h" continua sendo hipótese.
8. Inferência: falta testar com a API e o frontend reais; faltam Containerfile e entrada no `compose.yaml`; os UUIDs em `configs/inference.yaml` são de exemplo.
9. Avaliar o ML com as falhas geradas pelo próprio simulador (`DatasetGenerator --fault`, D32), além das injetadas pelo pipeline.
10. Vazamento de combustível: janela de 60 s é curta para a resolução do sensor de nível; testar inclinação em janela longa (10–30 min).
