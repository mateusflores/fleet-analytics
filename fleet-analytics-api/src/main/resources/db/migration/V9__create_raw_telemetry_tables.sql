CREATE TABLE telemetry.vehicle_positions
(
    time       TIMESTAMPTZ NOT NULL,
    vehicle_id UUID        NOT NULL,
    trip_id    UUID,
    latitude   DOUBLE PRECISION,
    longitude  DOUBLE PRECISION,
    speed_kmh  DOUBLE PRECISION,
    heading    DOUBLE PRECISION,
    accuracy   DOUBLE PRECISION
);

SELECT create_hypertable('telemetry.vehicle_positions', 'time');

CREATE TABLE telemetry.vehicle_telemetry
(
    time            TIMESTAMPTZ NOT NULL,
    vehicle_id      UUID        NOT NULL,
    trip_id         UUID,
    engine_rpm      INTEGER,
    fuel_level_pct  DOUBLE PRECISION,
    engine_temp_c   DOUBLE PRECISION,
    odometer_km     DOUBLE PRECISION,
    battery_voltage DOUBLE PRECISION
);

SELECT create_hypertable('telemetry.vehicle_telemetry', 'time');

CREATE TABLE telemetry.events
(
    time       TIMESTAMPTZ  NOT NULL,
    vehicle_id UUID         NOT NULL,
    trip_id    UUID,
    event_type VARCHAR(100) NOT NULL,
    severity   VARCHAR(20),
    message    TEXT,
    metadata   JSONB
);

SELECT create_hypertable('telemetry.events', 'time');
