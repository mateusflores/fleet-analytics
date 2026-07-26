ALTER TABLE telemetry.vehicle_positions
    SET (timescaledb.compress, timescaledb.compress_segmentby = 'vehicle_id');

ALTER TABLE telemetry.vehicle_telemetry
    SET (timescaledb.compress, timescaledb.compress_segmentby = 'vehicle_id');

ALTER TABLE telemetry.events
    SET (timescaledb.compress, timescaledb.compress_segmentby = 'vehicle_id');

SELECT add_compression_policy('telemetry.vehicle_positions', INTERVAL '7 days');
SELECT add_compression_policy('telemetry.vehicle_telemetry', INTERVAL '7 days');
SELECT add_compression_policy('telemetry.events', INTERVAL '30 days');
