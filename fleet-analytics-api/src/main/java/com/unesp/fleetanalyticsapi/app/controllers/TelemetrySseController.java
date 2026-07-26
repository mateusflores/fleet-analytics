package com.unesp.fleetanalyticsapi.app.controllers;

import com.unesp.fleetanalyticsapi.app.dtos.messaging.EventMessage;
import com.unesp.fleetanalyticsapi.app.dtos.messaging.PositionMessage;
import com.unesp.fleetanalyticsapi.app.dtos.messaging.TelemetryMessage;
import com.unesp.fleetanalyticsapi.app.sse.TelemetryEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/telemetry")
@RequiredArgsConstructor
public class TelemetrySseController {

    private final TelemetryEventPublisher publisher;
    private final JdbcTemplate jdbcTemplate;

    @GetMapping(value = "/positions/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('telemetry:read')")
    public SseEmitter streamPositions(
            @RequestParam(required = false) UUID vehicleId,
            @RequestParam(required = false) UUID tripId) {

        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        List<PositionMessage> recent = loadRecentPositions(vehicleId, tripId);
        for (PositionMessage msg : recent) {
            try {
                emitter.send(SseEmitter.event().data(msg));
            } catch (IOException e) {
                emitter.completeWithError(e);
                return emitter;
            }
        }

        Disposable subscription = publisher.positionFlux()
                .filter(msg -> vehicleId == null || vehicleId.equals(msg.vehicleId()))
                .filter(msg -> tripId == null || tripId.equals(msg.tripId()))
                .subscribe(
                        msg -> {
                            try {
                                emitter.send(SseEmitter.event().data(msg));
                            } catch (IOException ignored) {
                            }
                        },
                        emitter::completeWithError,
                        emitter::complete
                );

        emitter.onCompletion(subscription::dispose);
        emitter.onError(err -> subscription.dispose());
        emitter.onTimeout(subscription::dispose);

        return emitter;
    }

    @GetMapping(value = "/telemetry/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('telemetry:read')")
    public SseEmitter streamTelemetry(
            @RequestParam(required = false) UUID vehicleId,
            @RequestParam(required = false) UUID tripId) {

        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        List<TelemetryMessage> recent = loadRecentTelemetry(vehicleId, tripId);
        for (TelemetryMessage msg : recent) {
            try {
                emitter.send(SseEmitter.event().data(msg));
            } catch (IOException e) {
                emitter.completeWithError(e);
                return emitter;
            }
        }

        Disposable subscription = publisher.telemetryFlux()
                .filter(msg -> vehicleId == null || vehicleId.equals(msg.vehicleId()))
                .filter(msg -> tripId == null || tripId.equals(msg.tripId()))
                .subscribe(
                        msg -> {
                            try {
                                emitter.send(SseEmitter.event().data(msg));
                            } catch (IOException ignored) {
                            }
                        },
                        emitter::completeWithError,
                        emitter::complete
                );

        emitter.onCompletion(subscription::dispose);
        emitter.onError(err -> subscription.dispose());
        emitter.onTimeout(subscription::dispose);

        return emitter;
    }

    @GetMapping(value = "/events/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('telemetry:read')")
    public SseEmitter streamEvents(
            @RequestParam(required = false) UUID vehicleId,
            @RequestParam(required = false) UUID tripId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String severity) {

        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        List<EventMessage> recent = loadRecentEvents(vehicleId, tripId, eventType, severity);
        for (EventMessage msg : recent) {
            try {
                emitter.send(SseEmitter.event().data(msg));
            } catch (IOException e) {
                emitter.completeWithError(e);
                return emitter;
            }
        }

        Disposable subscription = publisher.eventFlux()
                .filter(msg -> vehicleId == null || vehicleId.equals(msg.vehicleId()))
                .filter(msg -> tripId == null || tripId.equals(msg.tripId()))
                .filter(msg -> eventType == null || eventType.equals(msg.eventType()))
                .filter(msg -> severity == null || severity.equals(msg.severity()))
                .subscribe(
                        msg -> {
                            try {
                                emitter.send(SseEmitter.event().data(msg));
                            } catch (IOException ignored) {
                            }
                        },
                        emitter::completeWithError,
                        emitter::complete
                );

        emitter.onCompletion(subscription::dispose);
        emitter.onError(err -> subscription.dispose());
        emitter.onTimeout(subscription::dispose);

        return emitter;
    }

    private List<PositionMessage> loadRecentPositions(UUID vehicleId, UUID tripId) {
        return jdbcTemplate.query("""
                SELECT time, vehicle_id, trip_id, latitude, longitude, speed_kmh, heading, accuracy
                FROM telemetry.vehicle_positions
                WHERE (vehicle_id = ?::uuid OR ? IS NULL)
                  AND (trip_id = ?::uuid OR ? IS NULL)
                  AND time > ?::timestamptz
                ORDER BY time ASC
                LIMIT 500
                """,
                (rs, rowNum) -> new PositionMessage(
                        rs.getObject("vehicle_id", UUID.class),
                        rs.getObject("trip_id", UUID.class),
                        rs.getTimestamp("time").toInstant(),
                        rs.getDouble("latitude"),
                        rs.getDouble("longitude"),
                        rs.getObject("speed_kmh", Double.class),
                        rs.getObject("heading", Double.class),
                        rs.getObject("accuracy", Double.class)
                ),
                vehicleId, vehicleId,
                tripId, tripId,
                Instant.now().minusSeconds(300));
    }

    private List<TelemetryMessage> loadRecentTelemetry(UUID vehicleId, UUID tripId) {
        return jdbcTemplate.query("""
                SELECT time, vehicle_id, trip_id, engine_rpm, fuel_level_pct,
                       engine_temp_c, odometer_km, battery_voltage
                FROM telemetry.vehicle_telemetry
                WHERE (vehicle_id = ?::uuid OR ? IS NULL)
                  AND (trip_id = ?::uuid OR ? IS NULL)
                  AND time > ?::timestamptz
                ORDER BY time ASC
                LIMIT 500
                """,
                (rs, rowNum) -> new TelemetryMessage(
                        rs.getObject("vehicle_id", UUID.class),
                        rs.getObject("trip_id", UUID.class),
                        rs.getTimestamp("time").toInstant(),
                        rs.getObject("engine_rpm", Integer.class),
                        rs.getDouble("fuel_level_pct"),
                        rs.getDouble("engine_temp_c"),
                        rs.getObject("odometer_km", Double.class),
                        rs.getDouble("battery_voltage")
                ),
                vehicleId, vehicleId,
                tripId, tripId,
                Instant.now().minusSeconds(300));
    }

    private List<EventMessage> loadRecentEvents(UUID vehicleId, UUID tripId,
                                                 String eventType, String severity) {
        return jdbcTemplate.query("""
                SELECT time, vehicle_id, trip_id, event_type, severity, message
                FROM telemetry.events
                WHERE (vehicle_id = ?::uuid OR ? IS NULL)
                  AND (trip_id = ?::uuid OR ? IS NULL)
                  AND (event_type = ? OR ? IS NULL)
                  AND (severity = ? OR ? IS NULL)
                  AND time > ?::timestamptz
                ORDER BY time ASC
                LIMIT 500
                """,
                (rs, rowNum) -> new EventMessage(
                        rs.getObject("vehicle_id", UUID.class),
                        rs.getObject("trip_id", UUID.class),
                        rs.getTimestamp("time").toInstant(),
                        rs.getString("event_type"),
                        rs.getString("severity"),
                        rs.getString("message")
                ),
                vehicleId, vehicleId,
                tripId, tripId,
                eventType, eventType,
                severity, severity,
                Instant.now().minusSeconds(300));
    }
}
