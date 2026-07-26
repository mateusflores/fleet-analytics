CREATE TABLE telemetry.trip_metrics
(
    trip_id              UUID        NOT NULL PRIMARY KEY,
    vehicle_id           UUID        NOT NULL,
    avg_speed_kmh        DOUBLE PRECISION,
    max_speed_kmh        DOUBLE PRECISION,
    total_distance_km    DOUBLE PRECISION,
    total_fuel_l         DOUBLE PRECISION,
    fuel_efficiency_km_l DOUBLE PRECISION,
    idle_time_seconds    INTEGER,
    harsh_events_count   INTEGER,
    driver_score         INTEGER,
    computed_at          TIMESTAMPTZ NOT NULL,
    insights_json        JSONB
);

CREATE TABLE telemetry.daily_vehicle_summary
(
    vehicle_id          UUID NOT NULL,
    date                DATE NOT NULL,
    total_distance_km   DOUBLE PRECISION,
    total_fuel_l        DOUBLE PRECISION,
    total_trips         INTEGER,
    active_time_seconds INTEGER,
    PRIMARY KEY (vehicle_id, date)
);

CREATE TABLE telemetry.alerts
(
    id           UUID         NOT NULL PRIMARY KEY,
    time         TIMESTAMPTZ  NOT NULL,
    vehicle_id   UUID         NOT NULL,
    trip_id      UUID,
    alert_type   VARCHAR(100) NOT NULL,
    severity     VARCHAR(20)  NOT NULL,
    message      TEXT,
    acknowledged BOOLEAN DEFAULT false,
    metadata     JSONB
);
