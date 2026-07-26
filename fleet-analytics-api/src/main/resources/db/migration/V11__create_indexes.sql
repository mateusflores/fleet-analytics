CREATE INDEX idx_positions_vehicle_time
    ON telemetry.vehicle_positions (vehicle_id, time DESC);

CREATE INDEX idx_positions_trip_time
    ON telemetry.vehicle_positions (trip_id, time DESC);

CREATE INDEX idx_telemetry_vehicle_time
    ON telemetry.vehicle_telemetry (vehicle_id, time DESC);

CREATE INDEX idx_events_vehicle_time
    ON telemetry.events (vehicle_id, time DESC);

CREATE INDEX idx_events_type_time
    ON telemetry.events (event_type, time DESC);

CREATE INDEX idx_alerts_vehicle_time
    ON telemetry.alerts (vehicle_id, time DESC);

CREATE INDEX idx_alerts_acknowledged
    ON telemetry.alerts (acknowledged, time DESC);

CREATE INDEX idx_trip_metrics_vehicle
    ON telemetry.trip_metrics (vehicle_id);

CREATE INDEX idx_daily_summary_date
    ON telemetry.daily_vehicle_summary (date DESC);
