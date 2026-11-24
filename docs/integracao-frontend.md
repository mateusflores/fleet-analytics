# Integração do frontend — insights históricos e políticas da frota

Guia para o responsável pelo frontend (`frontend/fleet-analytics`). Lista o que o backend já oferece e o que
virá, com os contratos e os arquivos do frontend afetados. Contexto técnico completo em
`ml-service/docs/plano-insights-historicos.md`.

| Item | Backend | Frontend |
|---|---|---|
| 1. Sinais extras na telemetria | ✅ disponível | opcional (novos gráficos) |
| 2. Tela de políticas da frota | ✅ disponível | **a fazer** |
| 3. Insights históricos (substituir o mock) | ✅ disponível | **a fazer** |
| 4. Rótulos dos tipos de alerta | ✅ | ✅ já feito em `alert-type-label.pipe.ts` |

---

## 1. Sinais extras na telemetria (disponível)

`GET /api/v1/telemetry/vehicle-data` e o SSE de telemetria passaram a devolver, além dos campos atuais, os
campos abaixo. Todos podem vir `null` (leituras antigas ou dispositivos que não enviam o sinal).

| Campo | Unidade | Descrição |
|---|---|---|
| `speedKmh` | km/h | velocidade (também continua vindo nas posições) |
| `loadPct` | % | carga do motor |
| `engineTorquePct` | % | torque do motor (pode ser negativo no freio-motor) |
| `pedalPct` | % | acelerador |
| `gear` | — | marcha engatada |
| `oilKpa` | kPa | pressão do óleo |
| `fuelRateLph` | L/h | consumo instantâneo |
| `boostKpa` | kPa | pressão do turbo |
| `intakeC` | °C | temperatura de admissão |

Correção junto: campos numéricos ausentes no banco agora chegam como `null`, não como `0`.

Arquivos: `models/machine/telemetry/machine-telemetry.model.ts` (e o `...-series-point.model.ts`) — acrescentar
os campos como opcionais (`speedKmh?: number | null`). Uso sugerido: gráficos de consumo e pressão do óleo no
detalhe do veículo (`machines/details/:id`).

---

## 2. Tela de políticas da frota (disponível)

Limites que a empresa configura uma vez e que alimentam os insights (limite de velocidade, direção brusca,
ociosidade, preço do diesel, faixa econômica de rotação por tipo de veículo).

### Endpoints

| Método | Rota | Permissão | Descrição |
|---|---|---|---|
| `GET` | `/api/v1/fleet-policy` | `analytics:view` | política atual |
| `PUT` | `/api/v1/fleet-policy` | `fleet-policy:update` (nova; o perfil Admin recebe automaticamente) | atualiza |

Resposta (`GET` e `PUT`):

```json
{
  "speedLimitKmh": 90.0,
  "harshAccelMs2": 0.7,
  "harshBrakeMs2": 1.5,
  "idleThresholdS": 300,
  "fuelPricePerLiter": 6.20,
  "vehicleTypes": [
    { "vehicleType": "ARTICULATED_VEHICLE", "economyRpmMax": 1600 },
    { "vehicleType": "RIGID_TRUCK", "economyRpmMax": 2000 }
  ],
  "updatedAt": "2026-11-23T20:42:01.080-03:00",
  "updatedBy": "1ba80deb-5c90-42cc-9475-845f6df6c912"
}
```

Requisição do `PUT` (mesmos campos, sem `updatedAt`/`updatedBy`; `vehicleTypes` é opcional e só os tipos
enviados são alterados). Validação no backend — erro `422` com a mensagem do campo:

| Campo | Regra | Texto sugerido na tela |
|---|---|---|
| `speedLimitKmh` | 20–150 | "Limite de velocidade da frota (km/h)" |
| `harshAccelMs2` | 0,1–5 | "Aceleração brusca a partir de (m/s²)" |
| `harshBrakeMs2` | 0,1–10 | "Frenagem brusca a partir de (m/s²)" |
| `idleThresholdS` | 30–3600 | "Motor ligado parado conta como ociosidade após (min)" — converter min ↔ s |
| `fuelPricePerLiter` | > 0 | "Preço do diesel (R$/L)" |
| `vehicleTypes[].economyRpmMax` | 500–8000 | "Rotação máxima econômica" por tipo de veículo |

Sugestões:
- Nova rota, por exemplo `settings/fleet-policy`, com `permissionGuard('analytics:view')`; formulário somente
  leitura sem `fleet-policy:update`.
- Mostrar "Última alteração em {updatedAt}". `updatedBy` é o id do usuário.
- Observação na tela: alterações valem para as próximas viagens; viagens já calculadas guardam os limites usados.
- Rótulos dos tipos de veículo: `RIGID_TRUCK` Caminhão rígido, `ARTICULATED_VEHICLE` Cavalo mecânico,
  `PASSAGER_VEHICLE` Veículo de passageiros, `AGRICULTURAL_VEHICLE` Veículo agrícola.

---

## 3. Insights históricos (disponível)

Substituem os insights fixos de `services/insight/insight.service.ts` (`aiInsights`, hoje um array no código)
na seção "Insights gerados por IA" de `components/insight/insight-list`. Os textos já chegam prontos em
português, com os números; o frontend só exibe.

### Endpoints

| Método | Rota | Permissão | Descrição |
|---|---|---|---|
| `GET` | `/api/v1/insights` | `analytics:view` | lista paginada, mais recentes (`updatedAt`) primeiro |
| `GET` | `/api/v1/insights/{id}` | `analytics:view` | um insight |
| `PATCH` | `/api/v1/insights/{id}` com `{"status": "ACKNOWLEDGED" \| "DISMISSED"}` | `analytics:view` | marcar como visto / descartar; devolve o insight atualizado |

Filtros do `GET` (todos opcionais):

| Parâmetro | Valores | Observação |
|---|---|---|
| `status` | `ACTIVE`, `ACKNOWLEDGED`, `DISMISSED`, `RESOLVED`; aceita vários (`status=ACTIVE,RESOLVED`) | **sem o parâmetro, vêm os abertos** (`ACTIVE` + `ACKNOWLEDGED`) |
| `scope` | `VEHICLE`, `DRIVER`, `FLEET` | |
| `subjectId` | UUID do veículo ou motorista | ex.: aba de insights no detalhe do veículo |
| `category` | ver tabela de categorias abaixo | |
| `page`, `size`, `sort` | padrão do Spring (`size=20`, `sort=updatedAt,desc`) | resposta `Page<>` igual às demais listas |

Ciclo de vida (quem muda o quê):

| Status | Significado | Quem define |
|---|---|---|
| `ACTIVE` | condição detectada; números atualizados a cada execução do motor | motor (também reabre um `ACKNOWLEDGED` se a gravidade subir) |
| `ACKNOWLEDGED` | usuário viu; continua aberto e atualizado | usuário (`PATCH`) |
| `DISMISSED` | usuário descartou; o motor não recria o mesmo insight por 14 dias, salvo se agravar | usuário (`PATCH`) |
| `RESOLVED` | a condição deixou de existir | motor |

Erros do `PATCH`: `400` se o status não for `ACKNOWLEDGED`/`DISMISSED` (ou valor inválido), `404` se o id não
existir, `409` se o insight já estiver `DISMISSED` ou `RESOLVED`. `acknowledgedBy`/`acknowledgedAt` registram quem
viu ou descartou e quando.

Item da lista:

```json
{
  "id": "6f1c…",
  "scope": "VEHICLE",
  "subjectId": "a3e0…",
  "subjectName": "Bandeirante 01",
  "category": "VEHICLE_FUEL_TREND",
  "severity": "WARNING",
  "title": "Consumo acima do histórico",
  "description": "Caminhão Bandeirante 01: consumo 25% acima do esperado para as rotas e cargas dos últimos 7 dias (histórico: +2%).",
  "periodStart": "2026-11-16T00:00:00-03:00",
  "periodEnd": "2026-11-23T00:00:00-03:00",
  "evidence": { "ratio": 1.25, "baselineRatio": 1.02, "days": 7, "trips": 14 },
  "status": "ACTIVE",
  "acknowledgedBy": null,
  "acknowledgedAt": null,
  "createdAt": "2026-11-23T06:00:00-03:00",
  "updatedAt": "2026-11-23T06:00:00-03:00"
}
```

### Mapeamento para o modelo atual (`models/insight/ai-insight.model.ts`)

| `AiInsight` | API |
|---|---|
| `title` | `title` |
| `description` | `description` |
| `type` (`INFO` / `WARNING` / `CRITICAL`) | `severity` (mesmos valores) |
| `createdAt` | `updatedAt` (o insight é atualizado enquanto a condição persiste) |

Sugestões de evolução da tela:
- Acrescentar `id`, `scope`, `subjectId`, `subjectName`, `category` e `status` ao modelo.
- Botões "Marcar como visto" e "Descartar" (`PATCH`).
- Filtros por escopo (veículos / motoristas / frota) e link para o veículo (`machines/details/:subjectId`)
  quando `scope = VEHICLE`.
- `evidence` traz os números usados; pode alimentar um "ver detalhes" (opcional).
- Trocar o `track insight.title` por `track insight.id`.

Rótulos das categorias (para filtros/ícones):

| Categoria | Rótulo |
|---|---|
| `DRIVER_SPEEDING` | Velocidade acima da política |
| `DRIVER_HARSH_DRIVING` | Direção brusca |
| `DRIVER_IDLE` | Motor ligado parado |
| `DRIVER_RPM` | Rotação fora da faixa econômica |
| `VEHICLE_FUEL_TREND` | Consumo acima do histórico |
| `VEHICLE_HEALTH_DRIFT` | Desgaste progressivo |
| `VEHICLE_ALERT_RECURRENCE` | Alertas recorrentes |
| `FLEET_IDLE_COST` | Custo da ociosidade |
| `FLEET_EFFICIENCY_RANKING` | Eficiência da frota |

Opcional: um aviso em tempo real quando surgir um insight novo pode reaproveitar o SSE de eventos (a definir).

---

## 4. Rótulos dos tipos de alerta (feito)

`shared/pipes/alert-type-label.pipe.ts` já recebeu os rótulos de `LOW_OIL_PRESSURE` (novo alerta fixo do
simulador) e dos alertas do serviço de ML (`ML_*`). Nada a fazer.

## 5. Observação

A rota `insights` em `app.routes.ts` não tem `permissionGuard`; quando os insights vierem da API, sugerimos
`permissionGuard('analytics:view')`, que é a permissão exigida pelo endpoint.
