-- =====================================================================================================
-- V13 — Base para os insights históricos (ver ml-service/docs/plano-insights-historicos.md)
--
-- 1. Sinais J1939 extras na telemetria (já enviados pelo simulador/ESP32 e usados pelo ML)
-- 2. Métricas por viagem (substitui as tabelas não utilizadas da V10) + visões diárias
-- 3. Insights gerados pelo motor de insights (ml-service)
-- 4. Políticas da frota (limites configuráveis pela empresa)
-- =====================================================================================================

-- 1. Sinais extras -------------------------------------------------------------------------------------
-- Colunas anuláveis e sem DEFAULT: permitido em hypertables com compressão ativa.
ALTER TABLE telemetry.vehicle_telemetry ADD COLUMN speed_kmh         DOUBLE PRECISION;
ALTER TABLE telemetry.vehicle_telemetry ADD COLUMN load_pct          DOUBLE PRECISION;
ALTER TABLE telemetry.vehicle_telemetry ADD COLUMN engine_torque_pct DOUBLE PRECISION;
ALTER TABLE telemetry.vehicle_telemetry ADD COLUMN pedal_pct         DOUBLE PRECISION;
ALTER TABLE telemetry.vehicle_telemetry ADD COLUMN gear              SMALLINT;
ALTER TABLE telemetry.vehicle_telemetry ADD COLUMN oil_kpa           DOUBLE PRECISION;
ALTER TABLE telemetry.vehicle_telemetry ADD COLUMN fuel_rate_lph     DOUBLE PRECISION;
ALTER TABLE telemetry.vehicle_telemetry ADD COLUMN boost_kpa         DOUBLE PRECISION;
ALTER TABLE telemetry.vehicle_telemetry ADD COLUMN intake_c          DOUBLE PRECISION;

CREATE INDEX IF NOT EXISTS idx_telemetry_trip_time
    ON telemetry.vehicle_telemetry (trip_id, time DESC);

-- 2. Métricas por viagem --------------------------------------------------------------------------------
-- As tabelas da V10 nunca foram usadas; recriadas com o conteúdo de que os insights precisam.
DROP TABLE IF EXISTS telemetry.trip_metrics;
DROP TABLE IF EXISTS telemetry.daily_vehicle_summary;

CREATE TABLE telemetry.trip_metrics
(
    trip_id             UUID             NOT NULL PRIMARY KEY,
    vehicle_id          UUID             NOT NULL,
    driver_id           UUID,                         -- copiado de fleet.trips para consultas por motorista
    start_time          TIMESTAMPTZ      NOT NULL,
    end_time            TIMESTAMPTZ,
    engine_on_s         INTEGER          NOT NULL DEFAULT 0,
    driving_s           INTEGER          NOT NULL DEFAULT 0, -- velocidade >= 1 km/h
    idle_s              INTEGER          NOT NULL DEFAULT 0, -- parado com motor ligado
    excessive_idle_s    INTEGER          NOT NULL DEFAULT 0, -- trechos de ociosidade acima de idle_threshold_s
    distance_km         DOUBLE PRECISION NOT NULL DEFAULT 0,
    fuel_used_l         DOUBLE PRECISION,             -- integral do consumo instantâneo
    fuel_expected_l     DOUBLE PRECISION,             -- consumo previsto pelo modelo de valor esperado
    avg_speed_kmh       DOUBLE PRECISION,             -- média em movimento
    max_speed_kmh       DOUBLE PRECISION,
    avg_load_pct        DOUBLE PRECISION,
    speeding_s          INTEGER          NOT NULL DEFAULT 0, -- acima de speed_limit_kmh da política
    harsh_accel_count   INTEGER          NOT NULL DEFAULT 0,
    harsh_brake_count   INTEGER          NOT NULL DEFAULT 0,
    rpm_over_s          INTEGER          NOT NULL DEFAULT 0, -- acima da faixa econômica do tipo de veículo
    events_warning      INTEGER          NOT NULL DEFAULT 0,
    events_critical     INTEGER          NOT NULL DEFAULT 0,
    events_ml           INTEGER          NOT NULL DEFAULT 0,
    health              JSONB            NOT NULL DEFAULT '{}', -- resíduo médio por sinal (real - esperado)
    policy              JSONB            NOT NULL DEFAULT '{}', -- limites usados no cálculo (auditoria)
    source              VARCHAR(20)      NOT NULL DEFAULT 'LIVE', -- LIVE | BACKFILL
    computed_at         TIMESTAMPTZ      NOT NULL DEFAULT now()
);

CREATE INDEX idx_trip_metrics_vehicle_start ON telemetry.trip_metrics (vehicle_id, start_time DESC);
CREATE INDEX idx_trip_metrics_driver_start ON telemetry.trip_metrics (driver_id, start_time DESC);

-- Resumos diários derivados das viagens (dia no fuso de operação da frota)
CREATE VIEW telemetry.daily_vehicle_summary AS
SELECT vehicle_id,
       (start_time AT TIME ZONE 'America/Sao_Paulo')::date AS date,
       count(*)                                            AS trips,
       sum(engine_on_s)                                    AS engine_on_s,
       sum(driving_s)                                      AS driving_s,
       sum(idle_s)                                         AS idle_s,
       sum(distance_km)                                    AS distance_km,
       sum(fuel_used_l)                                    AS fuel_used_l,
       sum(fuel_expected_l)                                AS fuel_expected_l,
       sum(speeding_s)                                     AS speeding_s,
       sum(harsh_accel_count)                              AS harsh_accel_count,
       sum(harsh_brake_count)                              AS harsh_brake_count,
       sum(rpm_over_s)                                     AS rpm_over_s,
       sum(events_warning)                                 AS events_warning,
       sum(events_critical)                                AS events_critical,
       sum(events_ml)                                      AS events_ml
FROM telemetry.trip_metrics
GROUP BY vehicle_id, (start_time AT TIME ZONE 'America/Sao_Paulo')::date;

CREATE VIEW telemetry.daily_driver_summary AS
SELECT driver_id,
       (start_time AT TIME ZONE 'America/Sao_Paulo')::date AS date,
       count(*)                                            AS trips,
       sum(driving_s)                                      AS driving_s,
       sum(idle_s)                                         AS idle_s,
       sum(excessive_idle_s)                               AS excessive_idle_s,
       sum(distance_km)                                    AS distance_km,
       sum(fuel_used_l)                                    AS fuel_used_l,
       sum(fuel_expected_l)                                AS fuel_expected_l,
       sum(speeding_s)                                     AS speeding_s,
       sum(harsh_accel_count)                              AS harsh_accel_count,
       sum(harsh_brake_count)                              AS harsh_brake_count,
       sum(rpm_over_s)                                     AS rpm_over_s
FROM telemetry.trip_metrics
WHERE driver_id IS NOT NULL
GROUP BY driver_id, (start_time AT TIME ZONE 'America/Sao_Paulo')::date;

-- 3. Insights ----------------------------------------------------------------------------------------------
CREATE TABLE telemetry.insights
(
    id              UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    scope           VARCHAR(20)  NOT NULL,  -- VEHICLE | DRIVER | FLEET
    subject_id      UUID,                   -- veículo ou motorista; NULL para FLEET
    category        VARCHAR(50)  NOT NULL,  -- ex.: FUEL_EFFICIENCY_TREND, DRIVER_HARSH_BRAKING
    severity        VARCHAR(20)  NOT NULL,  -- INFO | WARNING | CRITICAL
    title           TEXT         NOT NULL,
    description     TEXT         NOT NULL,
    period_start    TIMESTAMPTZ,
    period_end      TIMESTAMPTZ,
    evidence        JSONB        NOT NULL DEFAULT '{}', -- números, linha de base e teste usados
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | ACKNOWLEDGED | DISMISSED | RESOLVED
    acknowledged_by UUID,
    acknowledged_at TIMESTAMPTZ,
    dedup_key       VARCHAR(200) NOT NULL,  -- categoria + assunto: um insight aberto por chave
    CONSTRAINT ck_insights_scope CHECK (scope IN ('VEHICLE', 'DRIVER', 'FLEET')),
    CONSTRAINT ck_insights_severity CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    CONSTRAINT ck_insights_status CHECK (status IN ('ACTIVE', 'ACKNOWLEDGED', 'DISMISSED', 'RESOLVED'))
);

CREATE UNIQUE INDEX ux_insights_open_dedup
    ON telemetry.insights (dedup_key) WHERE status IN ('ACTIVE', 'ACKNOWLEDGED');
CREATE INDEX idx_insights_status_updated ON telemetry.insights (status, updated_at DESC);
CREATE INDEX idx_insights_subject ON telemetry.insights (scope, subject_id, updated_at DESC);

-- 4. Políticas da frota ------------------------------------------------------------------------------------
-- Uma linha (id = 1): a plataforma atende uma empresa por instalação.
CREATE TABLE fleet.fleet_policy
(
    id                   SMALLINT         NOT NULL PRIMARY KEY,
    speed_limit_kmh      DOUBLE PRECISION NOT NULL,
    harsh_accel_ms2      DOUBLE PRECISION NOT NULL,
    harsh_brake_ms2      DOUBLE PRECISION NOT NULL,
    idle_threshold_s     INTEGER          NOT NULL,
    fuel_price_per_liter NUMERIC(10, 2)   NOT NULL,
    updated_at           TIMESTAMPTZ      NOT NULL,
    updated_by           UUID,
    CONSTRAINT ck_fleet_policy_single_row CHECK (id = 1)
);

-- Valores iniciais: 90 km/h = limite do CTB para caminhões em rodovias de pista dupla (art. 61);
-- limiares de aceleração/frenagem brusca para veículos pesados; ociosidade a partir de 5 min.
INSERT INTO fleet.fleet_policy (id, speed_limit_kmh, harsh_accel_ms2, harsh_brake_ms2, idle_threshold_s,
                                fuel_price_per_liter, updated_at)
VALUES (1, 90, 0.7, 1.5, 300, 6.20, now());

-- Faixa econômica de rotação por tipo de veículo (acima dela conta como rpm_over_s)
CREATE TABLE fleet.vehicle_type_policy
(
    vehicle_type    VARCHAR(50) NOT NULL PRIMARY KEY,
    economy_rpm_max INTEGER     NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL
);

INSERT INTO fleet.vehicle_type_policy (vehicle_type, economy_rpm_max, updated_at)
VALUES ('RIGID_TRUCK', 2000, now()),
       ('ARTICULATED_VEHICLE', 1600, now()),
       ('PASSAGER_VEHICLE', 2200, now()),
       ('AGRICULTURAL_VEHICLE', 2000, now());
