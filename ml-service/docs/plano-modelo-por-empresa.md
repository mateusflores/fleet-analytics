# Pipeline de ML treinável por empresa — viabilidade e plano

Data: 2026-11-14 · Decisão de origem: D04 (ver `decisoes.md`)

## 1. Ideia
Em vez de um modelo universal, o Fleet Analytics oferece um **pipeline de treinamento**: a empresa fornece o histórico de
telemetria dos seus veículos, o pipeline valida os dados, treina modelos exclusivos para aquela frota e os publica para
inferência em tempo real. Outra empresa → novo treinamento, novo modelo.

O produto do TCC deixa de ser "um modelo" e passa a ser **um processo reprodutível de treinamento + um modelo demo**.

## 2. Viabilidade

### O que é viável
| Insight | Precisa de rótulos? | Técnica | Situação |
|---|---|---|---|
| Anomalia de operação ("comportamento fora do normal desta frota") | Não | IsolationForest (baseline) / autoencoder | features prontas (E04) |
| Desvio do valor esperado ("arrefecimento 8 °C acima do esperado para esta carga") | Não | Regressão por sinal + resíduo | a fazer |
| Perfil de condução (marcha lenta, acelerações bruscas, consumo) | Não | Métricas + clustering | a fazer |
| Risco de falha de componente | **Sim** (registros de manutenção) | LightGBM + custo esperado | baseline pronto no Scania (E07) |

Os três primeiros só dependem de telemetria de operação normal — exatamente o que qualquer empresa consegue fornecer.
Isso torna a proposta viável sem rótulos. O quarto é um módulo opcional (D19).

### Por que a abordagem se sustenta
- **É o que os dados permitem afirmar.** Um modelo aprende o "normal" dos veículos que viu. O T660 tem faixas de operação
  bem diferentes das do T270 (E06); um modelo do T270 trataria o T660 inteiro como anômalo.
- **Detecção de anomalias é naturalmente específica do ativo/frota**: o "normal" depende de motor, carga, rota e clima.
- **O custo de retreinar é baixo**: decodificação de 1,9 GB em ~1 min; treino em segundos/minutos em CPU.

### Limitações e riscos (declarar na monografia)
| Risco | Efeito | Mitigação |
|---|---|---|
| *Cold start*: empresa nova não tem histórico | Sem modelo no início | Período de coleta mínimo; até lá, só alertas por limiar fixo (regras) |
| Pouca variedade nos dados (só cruzeiro, sem frio/marcha lenta) | Falsos positivos em estados raros | Relatório de cobertura por estado de operação; limiar por estado |
| Falhas presentes no histórico de treino | Modelo aprende a falha como "normal" | Treino robusto (contaminação), exclusão de períodos com DM1 ativo |
| Sinais diferentes por frota | Features incompatíveis | Contrato canônico + features geradas pelos sinais disponíveis (D17) |
| Veículos heterogêneos na mesma empresa | "Normal" misturado | Normalização/modelo por grupo de veículo (D18) |
| Mudança de comportamento ao longo do tempo (*drift*) | Modelo envelhece | Retreino periódico; monitorar taxa de alarmes |
| Sem rótulos, não há precisão/recall reais | Avaliação indireta | Falhas sintéticas injetadas em dados reais + taxa de falsos alarmes em dados normais |
| **Demo com um único caminhão real** | Não prova generalização entre empresas | Ver seção 5; declarar como limitação |

### Dados mínimos sugeridos por empresa (hipótese a validar)
- Sinais obrigatórios: `ts`, `vehicle_id`, `rpm`, `speed_kmh`, e ao menos um de `load_pct`/`engine_torque_pct`.
- Recomendados: `coolant_c`, `fuel_rate_lph`, `oil_kpa`, `battery_v`, `boost_kpa`, `intake_c`, `fuel_level_pct`.
- Volume: a definir pelo experimento de curva de aprendizado (seção 6, X3). Ponto de partida: ≥ 10 h de motor ligado por
  grupo de veículo, cobrindo os estados de operação usuais.

## 3. Arquitetura proposta

```
dados da empresa ──► [1 Ingestão] ──► [2 Validação] ──► [3 Features] ──► [4 Treino] ──► [5 Avaliação] ──► artefato
 (candump, CSV…)     adaptadores      relatório de       janelas          modelos        falhas            models/<empresa>/<versão>/
                     → esquema         qualidade e                         por grupo      sintéticas +
                       canônico        cobertura                                          relatório
                                                                                                  │
telemetria em tempo real (RabbitMQ) ──► [6 Inferência] carrega o artefato da empresa do veículo ──┴─► EventMessage ─► API ─► SSE
```

1. **Ingestão (adaptadores → esquema canônico).** Tabela a 1 Hz: `ts, vehicle_id, <sinais>`.
   Adaptadores: `candump J1939` (já existe: `decoder/` + `pipeline/build_dataset.py`) e `CSV genérico` com mapeamento de
   colunas/unidades declarado na configuração da empresa.
2. **Validação.** Sinais presentes, % ausentes, faixas físicas, horas por veículo e por estado de operação. Reprova o
   treino se os requisitos mínimos não forem atendidos e diz o que falta.
3. **Features.** `features/windows.py` generalizado: gera as features a partir dos sinais disponíveis (hoje a lista é fixa).
4. **Treino.** Por empresa e por grupo de veículo: regressores de valor esperado (principal) + IsolationForest (baseline);
   limiares calibrados por validação cruzada em blocos temporais para uma taxa-alvo de falsos alarmes (D21–D23).
5. **Avaliação.** Injeção de falhas sintéticas (superaquecimento, queda de tensão, queda de pressão de óleo, consumo
   anormal, RPM errático) em dados de teste reais → precisão, recall, F1, ROC-AUC e tempo até a detecção. Gera um
   **relatório de treinamento** por empresa (Markdown/HTML).
6. **Artefato e inferência.** `models/<empresa>/<versão>/` com modelos, lista de features, limiares, métricas e hash dos
   dados. O serviço de inferência escolhe o artefato pela empresa do veículo (a API já tem `Company` ↔ `Vehicle`) e publica
   `EventMessage` em `fleet.telemetry.event`.

Configuração por empresa (exemplo):
```yaml
company: transportadora-demo
sources:
  - adapter: candump_j1939
    path: data/2014 Kenworth T270/2014_KW_T270_cross_country_can_log.txt
    vehicle_id: t270-01
    vehicle_group: kenworth-t270-px7
window: {size_s: 60, stride_s: 10}
models: [isolation_forest, expected_value]
target_false_alarm_rate: 0.01
```

Interface: `python -m ml_service.train --config configs/<empresa>.yaml` (um comando, do dado bruto ao relatório).

## 4. O que já existe e é reaproveitado
| Peça | Arquivo | Ajuste necessário |
|---|---|---|
| Decodificador J1939 | `src/ml_service/decoder/` | nenhum |
| Dataset 1 Hz | `src/ml_service/pipeline/build_dataset.py` | adicionar `vehicle_id`; virar adaptador |
| Features por janela | `src/ml_service/features/windows.py` | sinais dinâmicos; split configurável |
| Módulo supervisionado | `src/ml_service/scania/` | melhorar modelo; documentar como estudo de caso |

## 5. Versão demo para a apresentação
- **Empresa A — "frota real":** Kenworth T270 (cross-country, ~16 h). Treino completo pelo pipeline, relatório gerado.
- **Empresa B — "frota simulada":** dados gerados pelo `desktop-simulator` (outro perfil de veículo). Mesmo comando, outro
  modelo. Mostra que o processo é repetível e que os modelos resultantes são diferentes.
- **Experimento-chave (justifica D04):** aplicar o modelo da Empresa A aos dados do T660 e aos da Empresa B e medir a taxa
  de alarmes — espera-se que seja muito alta, mostrando que o modelo de uma frota não serve para outra. *É uma hipótese;
  o resultado entra em `experimentos.md` seja qual for.*
- **Módulo supervisionado (Scania):** apresentado como o que a plataforma oferece quando a empresa também fornece histórico
  de manutenção. Associação a veículos da frota apenas ilustrativa.
- **Ao vivo:** replay de um trecho real do T270 pelo RabbitMQ com uma falha injetada → alerta aparece no dashboard.

Limitação a declarar: a Empresa B é sintética; o TCC demonstra o **processo**, não a generalização em várias frotas reais.

## 6. Fases

| Fase | Entrega | Situação (2026-11-14) |
|---|---|---|
| F1 | Esquema canônico, configuração por empresa, adaptadores (candump, CSV), validação | ✅ feito |
| F2 | Features dinâmicas + treino (IsolationForest, valor esperado) + artefato versionado | ✅ feito |
| F3 | Falhas sintéticas + métricas + relatório de treinamento | ✅ feito (E09) |
| F4 | X1 transferência entre frotas | ✅ feito (E10) |
| F4 | X2 baseline × autoencoder; X3 curva de aprendizado | pendente |
| F5 | Serviço de inferência multiempresa + replay | ✅ feito e testado com RabbitMQ (E11); falta o teste com a API e o frontend |
| F6 | Scania: melhorar baseline | pendente |
| F7 | Atualizar EDA, README e material para o relatório | pendente |

Semana de aperfeiçoamento (ordem sugerida): teste com API e frontend → sensibilidade da Empresa A (problema 5 em `experimentos.md`) → X3 → X2 → F6.

## 7. Como executar

```bash
cd ml-service
(cd ../desktop-simulator && ./mvnw -q compile exec:java -Dexec.mainClass=com.unesp.desktopsimulator.tools.DatasetGenerator \
    -Dexec.args="--out ../ml-service/data/synthetic/empresa-b.csv --vehicles 3 --days 3 --seed 42")   # frota sintética (D31)
.venv/bin/python -m ml_service.train --config configs/empresa-a.yaml                           # treina e gera o relatório
.venv/bin/python -m ml_service.train --config configs/empresa-b.yaml
.venv/bin/python -m ml_service.experiments.transfer --source configs/empresa-a.yaml --targets configs/t660.yaml configs/empresa-b.yaml
.venv/bin/pytest
```

Demonstração ao vivo (com o RabbitMQ e a API do `fleet-analytics-api/compose.yaml` no ar):

```bash
# 1. em configs/inference.yaml, trocar o UUID de exemplo pelo UUID de um veículo cadastrado na plataforma
.venv/bin/python -m ml_service.inference.service --config configs/inference.yaml
# 2. em outro terminal: 10 min de dados reais do T270 com superaquecimento a partir de 2 min
.venv/bin/python -m ml_service.tools.replay --config configs/empresa-a.yaml --vehicle t270-01 \
    --vehicle-uuid <UUID do veículo> --segment 7 --start 3000 --duration 600 --fault overheating --fault-onset 120
# sem broker, só para ver os eventos: acrescentar --dry-run
```

Falhas disponíveis no replay: `overheating`, `battery_drain`, `oil_pressure_drop`, `fuel_overconsumption`, `boost_leak`,
`erratic_rpm`, `fuel_leak`. O alerta aparece cerca de 1 a 2 min após o início da falha.

Saída do treino: `models/<empresa>/<versão>/` com `model.joblib`, `metrics.json`, `quality.json`, `report.md` e `config.yaml`;
`models/<empresa>/LATEST` aponta para a versão mais recente.
