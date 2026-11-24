# Plano — insights a partir do histórico

Complementa o detector em tempo real (`plano-modelo-por-empresa.md`). Decisões D34–D38 em `decisoes.md`.

## 1. Objetivo e diferença para o detector em tempo real

| | Tempo real (feito) | Histórico (este plano) |
|---|---|---|
| Pergunta | "Esta leitura está estranha agora?" | "O que mudou ou se destaca nas últimas semanas?" |
| Entrada | janelas de 60 s | métricas por viagem e por dia |
| Saída | evento `ML_*` em segundos | insight persistente, revisado periodicamente |
| Exemplo | "pressão do óleo 58 kPa abaixo do esperado" | "caminhão X: consumo 25% acima do próprio histórico nos últimos 7 dias" |

Os insights apoiam decisões de gestão (motoristas, custo) e manutenção preventiva. São estatística comparativa
cuidadosa; o ML entra para **normalizar pelas condições de operação** (o modelo de valor esperado separa
"gastou mais porque rodou na serra carregado" de "gastou mais porque o motor piorou") e para detectar
**degradação lenta** que uma janela de 60 s não enxerga.

## 2. Catálogo de insights (primeira versão)

Nenhum insight depende do limite de velocidade da via (D35). Textos são frases prontas com os números (D36).

| # | Categoria | Escopo | Métrica | Linha de base | Exemplo de texto |
|---|---|---|---|---|---|
| 1 | `DRIVER_SPEEDING` | motorista | tempo acima do limite da **política da frota** por hora dirigida | mediana dos motoristas do mesmo tipo de veículo, 30 dias | "João passa 40% mais tempo acima de 90 km/h que a mediana da frota (12 vs 8,6 min a cada 10 h)" |
| 2 | `DRIVER_HARSH_DRIVING` | motorista | frenagens + acelerações bruscas por 100 km | idem | "Maria tem 2,1× mais frenagens bruscas por 100 km que a mediana" |
| 3 | `DRIVER_IDLE` | motorista | % do tempo parado com motor ligado (trechos > limite da política) | idem | "Carlos deixa o motor ligado parado 18% do tempo (frota: 7%) — ≈ R$ 310/mês" |
| 4 | `DRIVER_RPM` | motorista | % do tempo acima da faixa econômica do tipo de veículo | idem | "Pedro dirige 22% do tempo acima de 2.000 rpm (frota: 9%)" |
| 5 | `VEHICLE_FUEL_TREND` | veículo | consumo real ÷ consumo esperado pelo modelo | o próprio veículo, 90 dias anteriores | "Caminhão X: consumo 25% acima do esperado para as rotas e cargas dos últimos 7 dias (histórico: +2%)" |
| 6 | `VEHICLE_HEALTH_DRIFT` | veículo | resíduo médio diário (real − esperado) de óleo, arrefecimento, bateria | tendência nas últimas 3 semanas | "Pressão do óleo do caminhão Y vem caindo: −8% em 3 semanas em relação ao esperado" |
| 7 | `VEHICLE_ALERT_RECURRENCE` | veículo | alertas (fixos e de ML) por semana | 8 semanas anteriores | "Caminhão Z teve 6 alertas de temperatura nesta semana (média: 1/semana)" |
| 8 | `FLEET_IDLE_COST` | frota | litros consumidos parado × preço da política | — | "Ociosidade custou ≈ R$ 1.900 no mês; reduzir 20% economizaria ≈ R$ 380" |
| 9 | `FLEET_EFFICIENCY_RANKING` | frota | consumo real ÷ esperado por veículo | — | "Os 3 veículos menos eficientes do mês: …" |

Cuidados estatísticos (D37): taxas normalizadas por exposição (hora dirigida ou 100 km), exposição mínima
(ex.: 10 h dirigidas no período), comparação só dentro do mesmo tipo de veículo, intervalo de confiança da razão
de taxas (Poisson) ou *bootstrap*, e limiar de efeito mínimo (ex.: +25%) para virar insight.

## 3. Arquitetura

```
telemetria 1 Hz ──► telemetry.vehicle_telemetry (+ sinais extras, V13)
                          │  ao fim de cada viagem: ml-service calcula as métricas (usa a política vigente e o
                          │  modelo de valor esperado para fuel_expected_l e health)
                          ▼
                    telemetry.trip_metrics ──► visões daily_vehicle_summary / daily_driver_summary
                          │  motor de insights (ml-service, agendado: diário; horário na demo)
                          ▼
                    telemetry.insights ──► API GET/PATCH ──► frontend (aba "Insights")
```

- **Por que métricas por viagem calculadas no Python, e não agregados contínuos do TimescaleDB:** frenagens
  bruscas e trechos de ociosidade exigem funções de janela (diferença entre leituras consecutivas), que os
  agregados contínuos não suportam; o consumo esperado vem do modelo de ML; e o histórico de demonstração pode ser
  gravado direto em `trip_metrics` sem 12 meses de dados a 1 Hz (D38).
- **Deduplicação:** um insight aberto por `dedup_key` (categoria + assunto), garantido por índice único parcial.
  Uma nova execução **atualiza** o insight aberto (números, gravidade) em vez de criar outro; quando a condição
  some, o insight vira `RESOLVED`.
- **Políticas da frota** (`fleet.fleet_policy`, `fleet.vehicle_type_policy`): limite de velocidade, limiares de
  aceleração/frenagem brusca, tempo mínimo de ociosidade, preço do diesel e faixa econômica de rotação por tipo de
  veículo — configuradas uma vez e editáveis pela API (D35). Cada linha de `trip_metrics` guarda em `policy` os
  limites usados, para auditoria.

## 4. Modelo de dados (V13)

| Objeto | Conteúdo |
|---|---|
| `telemetry.vehicle_telemetry` | + `speed_kmh, load_pct, engine_torque_pct, pedal_pct, gear, oil_kpa, fuel_rate_lph, boost_kpa, intake_c` |
| `telemetry.trip_metrics` | por viagem: motorista, duração, tempo dirigindo/parado/ocioso excessivo, distância, combustível real, esperado e gasto parado (`idle_fuel_l`), velocidade média/máx., carga média, tempo acima do limite, acelerações/frenagens bruscas, tempo acima da faixa econômica, eventos (atenção/crítico/ML), `health` (resíduos), `policy`, `source` (LIVE/BACKFILL) |
| `telemetry.daily_vehicle_summary`, `telemetry.daily_driver_summary` | visões diárias sobre `trip_metrics` (dia no fuso America/Sao_Paulo) |
| `telemetry.insights` | escopo, assunto, categoria, gravidade, título, descrição, período, evidências (JSON), status, quem reconheceu, `dedup_key` |
| `fleet.fleet_policy` | 1 linha: 90 km/h, 0,7 m/s², 1,5 m/s², 300 s, R$ 6,20/L (valores iniciais) |
| `fleet.vehicle_type_policy` | rotação máxima econômica por tipo: rígido 2.000, cavalo 1.600, passageiros 2.200, agrícola 2.000 |

As tabelas `trip_metrics` e `daily_vehicle_summary` da V10 nunca foram usadas e foram recriadas no formato acima.

## 5. Fases

| Fase | Conteúdo | Status |
|---|---|---|
| 1. Dados | V13; `TelemetryMessage` e consumidor com os sinais extras; REST/SSE devolvendo os sinais; políticas da frota (`GET/PUT /api/v1/fleet-policy`, permissão `fleet-policy:update`); sincronização de permissões novas no `DataSeeder` | ✅ 2026-11-23 — migração testada atualizando um banco em V12 com dados; 60 testes da API passando |
| 2. Motor | métricas por viagem ao encerrar a viagem; motor de insights agendado com os 9 tipos; deduplicação/expiração | ✅ 2026-11-23 — `ml_service.insights` (E13) |
| 3. Exposição | `GET /api/v1/insights` (filtros status/escopo/assunto/categoria, nome do assunto), `GET/PATCH /api/v1/insights/{id}` (visto/descartado); integração no frontend (documentada em `docs/integracao-frontend.md`) | ✅ 2026-11-23 na API — testado contra banco temporário; 67 testes da API passando. Frontend: a cargo do colega |
| 4. Demo e avaliação | histórico de 12 meses só em `trip_metrics` (+ 2 semanas a 1 Hz) com cenários plantados (motorista agressivo, injetor que desregula numa data, alternador que degrada); medir acerto, atraso e insights falsos | pendente |

## 6. Avaliação (para o relatório)

Mesma lógica das falhas sintéticas (D26): cenários com verdade conhecida plantados no histórico gerado pelo
simulador (`DatasetGenerator`), e o motor deve encontrá-los. Métricas: cenários detectados, dias até a detecção
após o início do cenário, insights sem cenário correspondente (falsos), e estabilidade (quantas vezes um insight
abre/fecha sem mudança real).

## 7. Implementação (Fase 2)

Código em `src/ml_service/insights/`:

| Módulo | Papel |
|---|---|
| `trip_metrics.py` | métricas de uma viagem (função pura) |
| `rules.py` | as 9 regras (funções puras sobre DataFrames) e as frases prontas |
| `planner.py` | reconciliação: inserir, atualizar, reabrir, resolver, respeitar descarte |
| `db.py` | leitura/escrita no banco da plataforma (psycopg) |
| `run.py` | executor: viagens pendentes → regras → gravação; `--once` ou contínuo |

Convenções do cálculo por viagem:
- série levada a 1 Hz com as regras do treino (média por segundo, preenchimento limitado, novo trecho após
  lacuna > 60 s); "dirigindo" = velocidade ≥ 1 km/h; "parado" = motor ligado e velocidade < 1 km/h;
- ociosidade excessiva = trechos parados contínuos acima de `idle_threshold_s` (conta o trecho inteiro); o
  combustível desses trechos é estimado proporcionalmente (`idle_fuel_l × excessive_idle_s / idle_s`);
- direção brusca = episódios (bordas de subida) em que a variação de velocidade em 1 s passa do limiar;
- distância = odômetro por trecho, ou integral da velocidade se o odômetro faltar ou for incoerente;
- consumo esperado e saúde: janelas de 60 s **sem sobreposição**, fora do aquecimento (ou todas, para veículos
  com `assume_warm` no `configs/inference.yaml`), com o detector do grupo do veículo. A razão real ÷ esperado usa
  o combustível real **dessas mesmas janelas** (`health.fuel_real_l`), não o total da viagem.

Critérios das regras (constantes no topo de `rules.py`):

| Regra | Período | Mínimo de dados | Disparo | Confirmação | Gravidade |
|---|---|---|---|---|---|
| Motoristas (4) | 30 dias | 10 h dirigidas (300 km na direção brusca); ≥ 3 motoristas no tipo de veículo | taxa ≥ 1,25 × mediana dos colegas (com piso) | percentil 5% do *bootstrap* das viagens do motorista acima da mediana | ≥ 2× crítico |
| Consumo × histórico | 7 dias × 90 anteriores | 3 viagens / 20 L esperados × 5 viagens / 60 L | razão real÷esperado ≥ +10% | percentil 5% do *bootstrap* > +3% | ≥ +25% crítico |
| Desgaste | 7 dias × 60 anteriores | 3 dias × 7 dias | óleo −5%, bateria −2,5%, turbo −10%, temperatura +3 °C | Mann-Whitney unilateral p < 0,05 | óleo −12%, bateria −6%, turbo −20%, +6 °C |
| Alertas recorrentes | 7 dias × até 8 semanas | ≥ 5 semanas de histórico | ≥ 3 alertas e ≥ 3× a média semanal | Poisson p < 0,01 | crítico se houver alerta crítico |
| Custo da ociosidade | 30 dias | — | ≥ R$ 50 | — | informativo |
| Ranking de eficiência | 30 dias | ≥ 3 veículos com ≥ 50 L esperados | ≥ +8% acima da mediana | — | ≥ +20% atenção |

### Como executar

```bash
cd ml-service
export DATABASE_URL=jdbc:postgresql://localhost:5432/<banco> DATABASE_USERNAME=<usuário> DATABASE_PASSWORD=<senha>
.venv/bin/python -m ml_service.insights.run --config configs/insights.yaml --once   # uma execução
.venv/bin/python -m ml_service.insights.run --config configs/insights.yaml          # contínuo (interval_s)
```

O motor usa o mapeamento veículo → empresa/grupo de `configs/inference.yaml`; veículos sem modelo têm todas as
métricas, exceto consumo esperado e saúde (e ficam fora das regras de consumo e desgaste).
