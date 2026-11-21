# Registro de decisões

Formato: **decisão** — motivo — alternativas descartadas. Status: ✅ vigente · ♻️ substituída · 🕓 proposta (aguardando validação).

## Escopo e direção

| # | Data | Decisão | Motivo | Alternativas descartadas | Status |
|---|---|---|---|---|---|
| D01 | 2026-11-02 | Serviço de ML em Python separado (`ml-service/`), integrado por mensageria (RabbitMQ), publicando `EventMessage` | API e frontend já tratam eventos via SSE; nenhum acoplamento com o Spring | ML dentro da API Java | ✅ |
| D02 | 2026-11-02 | O esquema de dados do ML é definido pelos sinais reais, não pelo `TelemetryMessage` atual | Os dados da API eram mockados para teste da plataforma | Limitar o modelo aos 5 campos mockados | ✅ |
| D03 | 2026-11-02 | O ESP32 é validado com um módulo gerador de J1939 isolado, não em um caminhão | Escopo do TCC: provar a viabilidade da coleta; foco na plataforma e nos insights | Instalação em veículo real | ✅ |
| D04 | 2026-11-14 | **Modelo treinável por empresa**: em vez de um modelo universal, um pipeline que treina um modelo exclusivo com os dados históricos de cada empresa | Não há datasets públicos com variedade suficiente de veículos pesados; o único dataset J1939 longo é de um caminhão. Decisão tomada com o orientador | Modelo único para qualquer veículo (inviável com os dados disponíveis) | ✅ |

## Dados e decodificação

| # | Data | Decisão | Motivo | Alternativas descartadas | Status |
|---|---|---|---|---|---|
| D05 | 2026-11-02 | Decodificador J1939 próprio (tabela de SPNs em Python) | DBC J1939 completo costuma ser pago; tabela pequena e didática | `cantools` + DBC | ✅ |
| D06 | 2026-11-02 | Não filtrar por endereço de origem (SA); priorizar SA 0x00 quando há duas fontes | O nível de combustível só é transmitido pelo SA 0x31 | Usar só SA 0x00 | ✅ |
| D07 | 2026-11-02 | Reamostragem a 1 Hz (média por segundo, *forward-fill* limitado: 5 s; 30 s para sinais lentos) e divisão em segmentos em lacunas > 60 s | Timestamps com artefatos sub-segundo; sinais com taxas diferentes | Manter taxa nativa por sinal | ✅ |
| D08 | 2026-11-02 | Remover `oil_c` do esquema | Nunca vem válido no T270 | — | ✅ |
| D09 | 2026-11-02 | Zeros de `fuel_level_pct` no início de cada segmento tratados como ausentes | Artefato de inicialização do nó 0x31 | — | ✅ |
| D10 | 2026-11-02 | Usar apenas o log *cross-country* do T270; descartar `CSU090BJ/AT/B6` | São trechos idênticos do cross-country (segmentos 7, 3 e 4) → vazamento treino/teste | Treinar no BJ e testar no cross-country (♻️ erro corrigido) | ✅ |
| D11 | 2026-11-02 | Split por segmento: treino = seg. 0 e 2; teste = seg. 7 + curtos. (A validação separada de 15% foi substituída por validação cruzada no treino — D22) | Evita vazamento temporal | Split aleatório de janelas | ✅ |
| D12 | 2026-11-02 | Pasta "2011 Freightliner Cascadia" descartada | Arquivos idênticos aos da T660 (download incorreto) | — | ✅ |

## Modelagem

| # | Data | Decisão | Motivo | Alternativas descartadas | Status |
|---|---|---|---|---|---|
| D13 | 2026-11-02 | Modelo principal não supervisionado (detecção de anomalias), avaliado com falhas sintéticas | Logs J1939 não têm falhas rotuladas | Classificador supervisionado de falhas | ✅ |
| D14 | 2026-11-02 | Janelas de 60 s, passo de 10 s; estatísticas + inclinação por sinal | Captura nível e tendência; simples de explicar | Séries brutas em rede recorrente | ✅ |
| D15 | 2026-11-02 | Scania Component X tratado como eixo separado (manutenção preditiva supervisionada) | Variáveis anonimizadas, acumuladas e esparsas — incompatíveis com J1939 a 1 Hz | Misturar com o T270 | ✅ |
| D16 | 2026-11-02 | No Scania: LightGBM multiclasse + decisão de mínimo custo esperado; holdout por veículo | Classes muito desbalanceadas (~1,5% positivas); matriz de custo assimétrica | `argmax` (equivale a nunca alarmar) | ✅ |
| D17 | 2026-11-14 | Contrato de dados canônico + features geradas a partir dos sinais **disponíveis** em cada empresa | Cada frota expõe um conjunto diferente de sinais | Lista fixa de sinais obrigatórios | ✅ |
| D18 | 2026-11-14 | Modelos por empresa, com normalização por grupo de veículo (modelo/motor) | Veículos diferentes têm faixas normais diferentes (ver E06) | Um único "normal" para toda a frota | ✅ |
| D19 | 2026-11-14 | Módulo supervisionado **opcional**, ativado só se a empresa fornecer registros de manutenção | Sem rótulos não há como prever falha específica; Scania é o estudo de caso | Exigir rótulos de todas as empresas | ✅ |
| D20 | 2026-11-14 | Empresa B da demo é sintética: porte offline (Python) das equações e constantes do `desktop-simulator` | O objetivo é provar o conceito (processo repetível); o simulador Java só gera em tempo real | Procurar outro dataset real; rodar o simulador por horas | ✅ |
| D21 | 2026-11-14 | Detector principal = **valor esperado** (regressor por sinal de resposta, a partir das condições de operação; resíduo = indicador). IsolationForest fica como baseline | O IsolationForest global não detectou nenhuma falha de um único sinal (E08); o resíduo é explicável ("X acima do esperado") | Só IsolationForest; autoencoder (adiado) | ✅ |
| D22 | 2026-11-14 | Limiares por validação cruzada em 5 blocos temporais do treino (escores *out-of-fold*); cada detector normalizado pelo seu quantil 99% e um fator global calibra o **alarme combinado** para a taxa-alvo | Conjunto de validação separado era pequeno (578 janelas); Bonferroni por detector levava o limiar para o extremo da cauda (E08) | Validação separada; quantil extremo por detector | ✅ |
| D23 | 2026-11-14 | **Aquecimento fora do escopo**: janelas nos primeiros 1200 s após a partida não entram no treino nem são pontuadas; "tempo desde a partida" vira feature | Uma única partida a frio no treino (2,6% das janelas) gerava resíduos de até 52 °C e definia o limiar do arrefecimento em 45 °C (E08) | Manter tudo no escopo; filtrar pela temperatura (exigiria o sensor) | ✅ |
| D24 | 2026-11-14 | Sinais acumulados (nível de combustível): alvo = inclinação na janela, com saltos de reabastecimento (> 2 %/s) removidos | Detectar vazamento exige comparar a queda real com a esperada; reabastecimento em degrau distorcia a inclinação | Só a feature de inclinação no IsolationForest | ✅ |
| D25 | 2026-11-14 | Detector de irregularidade do RPM: variação média do RPM prevista pelas demais condições (sem as features do próprio RPM) | Ruído de média zero no RPM não altera a média; nenhum detector o via (E08) | — | ✅ |
| D26 | 2026-11-14 | Avaliação por falhas sintéticas em 3 severidades (0,25 / 0,5 / 1,0), eventos de 300 s com rampa, a cada 900 s nos dados de teste | Sem rótulos reais; as severidades mostram o limite de sensibilidade de cada detector | Uma única magnitude | ✅ |
| D27 | 2026-11-14 | Inferência sem alterar a API: o serviço usa uma fila própria e exclusiva ligada a `amq.topic` (`telemetry.telemetry` e `telemetry.position`) e publica `EventMessage` em `telemetry.event` | A API já persiste e envia eventos por SSE; a fila exclusiva não compete com as filas da API nem acumula mensagens se o serviço cair | Consumir da fila da API (dividiria as mensagens); endpoint REST na API | ✅ |
| D28 | 2026-11-14 | Os sinais extras do ML viajam como campos adicionais do JSON de telemetria (`speedKmh`, `loadPct`, `oilKpa`…); a velocidade também é lida das mensagens de posição | O `ObjectMapper` da API ignora propriedades desconhecidas; evita mudar o `TelemetryMessage` agora (D02) | Novo tópico só para o ML | ✅ |
| D29 | 2026-11-14 | Evento só após 2 janelas seguidas com alarme; mesmo tipo de evento repete no máximo a cada 300 s por veículo, exceto se agravar de WARNING para CRITICAL (escore ≥ 2× o limiar) | Reduz alarmes isolados e evita inundar o dashboard durante uma falha contínua | Um evento por janela com alarme | ✅ |
| D30 | 2026-11-14 | Veículo → empresa/grupo definido em `configs/inference.yaml` (UUID do veículo na plataforma) | Simples para a demo; a API tem `Vehicle` ↔ `Company`, mas consultá-la exigiria autenticação do serviço | Consulta à API ou ao banco (trabalho futuro) | ✅ |
