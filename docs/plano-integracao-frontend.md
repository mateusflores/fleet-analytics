# Plano de integração do frontend (insights, políticas, telemetria)

Base: `docs/integracao-frontend.md`. Projeto: `frontend/fleet-analytics` (Angular 19, standalone, Material, ApexCharts).
Caminhos abaixo relativos a `frontend/fleet-analytics/src/app/`.

## Problemas encontrados na leitura do código (afetam a integração)

| # | Onde | Problema | Consequência com a API atual |
|---|---|---|---|
| A | `interceptors/auth.interceptor.ts` | tenta refresh em **401 e 403** | 403 agora é "sem permissão" (ex.: `PUT /fleet-policy` sem `fleet-policy:update`) → refresh inútil e, no retry, **logout** do usuário |
| B | `components/machine/machine-details/*.html` | `machineTelemetry.engineRpm.toFixed(...)` | campos agora chegam `null` → `TypeError` e cards quebram |
| C | `components/login/login.component.ts` | 403 tratado como "senha inválida" | usuário desativado recebe mensagem errada |
| D | `guards/permission.guard.ts` | redireciona para `/dashboard`, rota inexistente | cai no `**` → home; funciona por acaso |
| E | `app.component.ts` | menu mostra todos os itens, sem checar permissão | usuário vê link de tela que o guard bloqueia |
| F | `shared/pipes/machine-type-label.pipe.ts` | rótulos diferentes dos sugeridos no doc ("Veículo pesado" × "Caminhão rígido") | proposta: **manter o pipe atual** em todas as telas, por consistência |

---

## Fase 0 — Ajustes de base (pré-requisito)

1. **Interceptor** (A): refresh só em `401`. Se o refresh falhar com `401`/`403` → `logout()` e ir para `/login`. `403` em
   requisição normal é repassado ao componente.
2. **Login** (C): `401` → "Usuário ou senha inválidos."; `403` → "Usuário desativado. Procure o administrador."
3. **Guard** (D): redirecionar para `/` em vez de `/dashboard`.
4. **Helper de erro** novo, `shared/utils/api-error.ts`: `apiErrorMessage(err, fallback)` que lê `err.error.message`
   (corpo `{ status, error, message }`) e mapeia `403/404/409/422`. Reaproveitado nas fases 2, 3 e 5.
5. **Menu** (E): campo opcional `permission` no `NavItem`, filtrado com `authService.hasAnyPermission`.

## Fase 1 — Telemetria: sinais extras (item 1 do doc)

1. Models `machine-telemetry.model.ts` e `machine-telemetry-series-point.model.ts`: campos atuais passam a
   `number | null`; novos opcionais `speedKmh, loadPct, engineTorquePct, pedalPct, gear, oilKpa, fuelRateLph,
   boostKpa, intakeC` (`?: number | null`).
2. **Null-safety** (B): método `fmt(value, unit, digits)` no `machine-details` que devolve `'--'` para `null`;
   substituir os `toFixed` do template.
3. `TelemetryMetric`: acrescentar `'speed' | 'fuelRate' | 'oilPressure'`. Trocar os dois `switch` do
   `telemetry-chart.component.ts` por um mapa `metric → campo` (remove duplicação) e ignorar pontos `null`
   (ApexCharts aceita `y: null` como lacuna). Remover o `console.log(changes)`.
4. `machine-details`: novos gráficos **Velocidade (km/h)**, **Consumo instantâneo (L/h)** e **Pressão do óleo (kPa)**;
   cards opcionais de velocidade e pressão do óleo.

## Fase 2 — Tela de políticas da frota (item 2)

Arquivos novos:
- `models/fleet-policy/fleet-policy.model.ts` — `FleetPolicy`, `VehicleTypePolicy`, `UpdateFleetPolicyRequest`.
- `services/fleet-policy/fleet-policy.service.ts` — `get()` e `update(req)` em `/fleet-policy`.
- `components/fleet-policy/fleet-policy.component.{ts,html,css}` — formulário reativo:
  - campos com os rótulos e validadores do doc (`min`/`max`); ociosidade exibida em **minutos** (0,5–60) e
    convertida para segundos no envio;
  - `FormArray` de `vehicleTypes` com rótulo pelo `machineTypeLabel` e `economyRpmMax` 500–8000;
  - **somente leitura** sem `fleet-policy:update` (form `disable()`, sem botão Salvar);
  - "Última alteração em {updatedAt | date}"; aviso "Alterações valem para as próximas viagens…";
  - erro `422` mostra `message` do backend via helper da fase 0.

Alterações:
- `app.routes.ts`: rota `settings/fleet-policy` com `permissionGuard('analytics:view')`.
- `app.component.ts`: item de menu "Políticas da frota" (ícone `tune`), permissão `analytics:view`.
- `shared/pipes/permission-translations.ts`: entrada para `fleet-policy:update` (aparece na tela de perfis).

`updatedBy` é um UUID: exibir só a data nesta fase (ver decisão 3).

## Fase 3 — Insights históricos (item 3)

Models (`models/insight/`):
- `insight.model.ts` — `Insight` com todos os campos da API; tipos `InsightScope`, `InsightCategory`,
  `InsightSeverity`, `InsightStatus`; `evidence: Record<string, number | string>`.
- `insight-filter.model.ts` — `{ status?, scope?, subjectId?, category?, page, size }`.
- `ai-insight.model.ts` deixa de ser usado e é removido.

`services/insight/insight.service.ts`:
- remove o array fixo `aiInsights` e `getAiInsights()`;
- `getInsights(filter): Observable<PaginatedResponse<Insight>>` com `HttpParams` (status em lista separada por vírgula);
- `getInsight(id)`, `updateInsightStatus(id, 'ACKNOWLEDGED' | 'DISMISSED')`;
- alertas determinísticos e SSE continuam como estão.

Pipes novos (`shared/pipes/`): `insight-category-label.pipe.ts` (tabela de categorias do doc, com ícone) e
`insight-scope-label.pipe.ts` (Veículo / Motorista / Frota).

`components/insight/insight-list`:
- seção "Insights gerados por IA" passa a consumir a API; classes de cor por `severity` (mesmos valores de hoje);
- **filtros** no topo: escopo (chips Todos/Veículos/Motoristas/Frota), categoria (select) e status
  (Abertos [padrão] / Resolvidos / Descartados);
- card mostra categoria, `subjectName`, período (`periodStart`–`periodEnd`), `updatedAt`, badge "Visto" quando
  `ACKNOWLEDGED`; `track insight.id`;
- ações **Marcar como visto** e **Descartar** (descartar pede confirmação via `ConfirmDialogService`, avisando dos
  14 dias); atualiza o item com a resposta do `PATCH`; `409` → snackbar "Insight já foi descartado ou resolvido" e
  recarrega;
- link "Ver veículo" → `machines/details/:subjectId` quando `scope = VEHICLE`;
- "Ver detalhes" expansível listando `evidence` (rótulos para as chaves mais comuns, número formatado);
- paginação com `mat-paginator` (já importado) ou "Carregar mais", `size=20`.

Detalhe do veículo (`machine-details`): seção "Insights do veículo" reutilizando o card — extrair
`components/insight/insight-card/insight-card.component` (input `insight`, outputs `acknowledge`/`dismiss`) para
usar nas duas telas, filtrando por `subjectId` = id do veículo.

`app.routes.ts`: `permissionGuard('analytics:view')` na rota `insights` (item 5 do doc) e no menu.

Fora do escopo: aviso em tempo real de insight novo (o backend marcou como "a definir").

## Fase 4 — Respostas da API que mudaram (item 8)

- Iniciar viagem (`start-trip-dialog`): mostrar `409` "Veículo inativo" / "Motorista desativado".
- Cadastro/edição de veículo (`add-machine`, `machine-details`): `409` com a mensagem (placa / nº de série repetido).
- Perfis (`role-form`, `role-list`): `409` nome repetido / perfil em uso.
- Todos via `apiErrorMessage` da fase 0. (`PUT /vehicles` sem `active`: o frontend já envia o campo; nada a fazer.)

## Fase 5 — Cadastro e edição de usuários (item 6)

- `user-form`: `username` com `minLength(6)` e `maxLength(30)`; `fieldError()` com mensagem por campo
  ("O usuário deve ter entre 6 e 30 caracteres", senha mantém a dela).
- `user-form` e `user-details`: `error:` usa `apiErrorMessage` (`409` → "Usuário ou e-mail já cadastrado",
  `422` → mensagem do backend, `404` → "Perfil não encontrado").

## Fase 6 — Verificação

- `npm run build` sem erros/avisos novos.
- Ambiente demo (`docker compose --profile demo up -d --build`; front em `http://localhost:8083`, API 8082), roteiro:
  1. Insights: lista carrega, filtros alteram a consulta, "visto"/"descartar" refletem na lista e no filtro de status,
     link para o veículo abre o detalhe com a seção de insights.
  2. Políticas: Admin edita e salva; valor fora da faixa mostra o `422`; perfil sem `fleet-policy:update` vê
     somente leitura e **não é deslogado**.
  3. Detalhe do veículo: cards com `--` em campo nulo, novos gráficos com dados.
  4. Login de usuário desativado; criação de usuário com username curto e duplicado.

## Ordem e tamanho estimado

| Fase | Depende de | Tamanho |
|---|---|---|
| 0 Base | — | pequeno |
| 1 Telemetria | 0 | médio |
| 2 Políticas | 0 | médio |
| 3 Insights | 0 | grande |
| 4 Respostas da API | 0 | pequeno |
| 5 Usuários | 0 | pequeno |
| 6 Verificação | todas | — |

Sugestão de commits: um por fase, na branch `feat/integracao-frontend-com-insights-gerados-por-ML`.

## Decisões para aprovação

1. **Escopo**: incluir as fases 4 e 5 (fora do tema insights, mas listadas no doc)? Proposta: sim.
2. **Gráficos novos** da fase 1 (velocidade, consumo, pressão do óleo): incluir? Proposta: sim.
3. **`updatedBy`** nas políticas: só a data (proposta) ou buscar o nome do usuário (exige permissão `users:read`)?
4. **Paginação dos insights**: `mat-paginator` (proposta, igual às outras listas) ou "Carregar mais" (igual aos alertas)?
5. **Rótulos de tipo de veículo**: manter os do pipe atual (proposta) ou trocar pelos do doc em todo o sistema?
