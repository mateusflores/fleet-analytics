package com.unesp.fleetanalyticsapi.app.messaging;

import com.unesp.fleetanalyticsapi.app.dtos.messaging.EventMessage;
import com.unesp.fleetanalyticsapi.app.dtos.messaging.PositionMessage;
import com.unesp.fleetanalyticsapi.app.dtos.messaging.TelemetryMessage;
import com.unesp.fleetanalyticsapi.app.sse.TelemetryEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@RequiredArgsConstructor
@Validated
public class TelemetryConsumer {

    private final JdbcTemplate jdbcTemplate;
    private final TelemetryEventPublisher eventPublisher;

    @RabbitListener(queues = "${app.messaging.queue-position}")
    public void handlePosition(PositionMessage msg) {
        jdbcTemplate.update("""
                INSERT INTO telemetry.vehicle_positions
                    (time, vehicle_id, trip_id, latitude, longitude, speed_kmh, heading, accuracy)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                msg.timestamp(),
                msg.vehicleId(),
                msg.tripId(),
                msg.latitude(),
                msg.longitude(),
                msg.speedKmh(),
                msg.heading(),
                msg.accuracy()
        );
        eventPublisher.publishPosition(msg);
    }

    @RabbitListener(queues = "${app.messaging.queue-telemetry}")
    public void handleTelemetry(TelemetryMessage msg) {
        jdbcTemplate.update("""
                INSERT INTO telemetry.vehicle_telemetry
                    (time, vehicle_id, trip_id, engine_rpm, fuel_level_pct,
                     engine_temp_c, odometer_km, battery_voltage)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                msg.timestamp(),
                msg.vehicleId(),
                msg.tripId(),
                msg.engineRpm(),
                msg.fuelLevelPct(),
                msg.engineTempC(),
                msg.odometerKm(),
                msg.batteryVoltage()
        );
        eventPublisher.publishTelemetry(msg);
    }

    @RabbitListener(queues = "${app.messaging.queue-event}")
    public void handleEvent(EventMessage msg) {
        jdbcTemplate.update("""
                INSERT INTO telemetry.events
                    (time, vehicle_id, trip_id, event_type, severity, message)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                msg.timestamp(),
                msg.vehicleId(),
                msg.tripId(),
                msg.eventType(),
                msg.severity(),
                msg.message()
        );
        eventPublisher.publishEvent(msg);
    }
}
