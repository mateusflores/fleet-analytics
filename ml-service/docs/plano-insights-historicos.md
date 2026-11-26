# Plano — insights a partir do histórico

Complementa o detector em tempo real (`plano-modelo-por-empresa.md`). Decisões D34–D47 em `decisoes.md`.

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
| 4. Demo e avaliação | histórico de 12 meses só em `trip_metrics` (+ 2 semanas a 1 Hz) com cenários plantados (motorista agressivo, injetor que desregula numa data, alternador que degrada); medir acerto, atraso e insights falsos | ✅ 2026-11-24 — `ml_service.insights.demo` (E14): 11/11 insights esperados, 1 episódio falso em 275 dias |

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

Critérios das regras (constantes no topo de `rules.py`; ciclo de vida com histerese de 48 h antes de resolver, D46):

| Regra | Período | Mínimo de dados | Disparo | Confirmação | Gravidade |
|---|---|---|---|---|---|
| Motoristas (4) | 30 dias | 10 h dirigidas (300 km na direção brusca); ≥ 3 motoristas no tipo de veículo | taxa ≥ 1,25 × mediana dos colegas (com piso) **e** diferença absoluta mínima (5 min/10 h, 3/100 km, 2 p.p.) | percentil 5% do *bootstrap* das viagens do motorista ≥ 1,25 × mediana (D46) | ≥ 2× crítico |
| Consumo × histórico | 7 dias × 90 anteriores | 3 viagens / 20 L esperados × 5 viagens / 60 L | razão real÷esperado ≥ +10% sobre o histórico, **ou** ≥ +15% sobre o modelo (persistente, D47) | percentil 5% do *bootstrap* > +3% (tendência) / > +7,5% (persistente) | ≥ +25% crítico |
| Desgaste | 7 dias × 60 anteriores | 3 dias × 7 dias | tendência: óleo −5%, bateria −2,5%, turbo −10%, temperatura +3 °C; **ou** persistente sobre o modelo: −8%, −4%, −15%, +4 °C (D47) | tendência: Mann-Whitney unilateral p < 0,05; persistente: ≥ 80% dos dias recentes além de metade do limiar | óleo −12%, bateria −6%, turbo −20%, +6 °C |
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

No compose (raiz do repositório), o motor roda como o serviço `ml-insights` com `configs/insights.compose.yaml`;
`INSIGHTS_INTERVAL_S` ajusta o intervalo (ex.: 300 na demo).

O motor usa o mapeamento veículo → empresa/grupo de `configs/inference.yaml`; veículos sem modelo têm todas as
métricas, exceto consumo esperado e saúde (e ficam fora das regras de consumo e desgaste).

## 8. Histórico de demonstração e avaliação (Fase 4)

Código em `src/ml_service/insights/demo/` e `desktop-simulator/.../tools/HistoryGenerator.java`:

| Módulo | Papel |
|---|---|
| `configs/demo-history.yaml` | frota, motoristas (estilo e hábito de ociosidade), clima e os cenários S1–S7 com a verdade conhecida |
| `scenario.py` | YAML → agenda veículo × dia (motorista, estilo, ociosidade, ambiente, intensidade das falhas) e `ground_truth` (D43) |
| `HistoryGenerator` (Java) | executa a agenda com o `VehicleModel`: viagens, alertas (`AlertMonitor`) e telemetria a 1 Hz |
| `backfill.py` | métricas por viagem com o mesmo `compute_trip_metrics` do caminho ao vivo |
| `build.py` | orquestra mês a mês, em paralelo; guarda a telemetria 1 Hz só dos meses das 2 últimas semanas |
| `evaluate.py` | replay dia a dia das regras e do planner e comparação com a verdade conhecida (D45) |
| `load.py` | carga no banco **da demo**: cadastros pela API, viagens/métricas/alertas por `COPY`, depois o executor normal |

**Demonstração pronta** (na raiz do repositório): `docker compose -f demo/compose.yaml up -d --build`. O serviço
`ml-insights` desse compose roda `demo.seed` antes do motor contínuo: espera a API, gera o histórico no volume
`demo_history` (se não existe ou terminou antes de ontem), carrega com `load.load`, calcula as métricas LIVE e os
insights e grava a marca `public.demo_seed`, de modo que as próximas partidas não recarregam nada. A imagem
(`demo/Containerfile`) é a do ml-service mais um JRE e as classes do simulador (`DEMO_SIMULATOR_CLASSPATH`).

Passo a passo manual (avaliação, ou carga num banco já existente):

```bash
# 1. gerar (≈ 3 min com 8 processos; data/demo/, ~100 MB)
cd ml-service
.venv/bin/python -m ml_service.insights.demo.build --config configs/demo-history.yaml --jobs 8 [--end-date AAAA-MM-DD]

# 2. avaliar (≈ 1 min; reports/insights/e14_demo_evaluation.json + tabela no terminal)
.venv/bin/python -m ml_service.insights.demo.evaluate

# 3. carregar num banco que só tenha a demo (API rodando sobre ele)
DEMO_DATABASE_URL=postgresql://<usuário>:<senha>@localhost:5432/<banco> ADMIN_PASSWORD=<senha do admin> \
  .venv/bin/python -m ml_service.insights.demo.load --api http://localhost:8080/api/v1 [--reset]
```

- Veículos `DEMO-*`, motoristas `demo.*` (senha `DEMO_DRIVER_PASSWORD`, padrão `demo-driver`); o simulador desktop
  pode mandar telemetria ao vivo para a demo com a configuração padrão.
- O histórico termina em `--end-date` (padrão: hoje). Para a apresentação, gere e carregue no próprio dia (ou na
  véspera) para os insights refletirem "os últimos 7/30 dias".
