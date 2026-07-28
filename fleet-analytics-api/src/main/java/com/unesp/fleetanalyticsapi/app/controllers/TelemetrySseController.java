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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/telemetry")
@RequiredArgsConstructor
public class TelemetrySseController {

    private final TelemetryEventPublisher publisher;
    private final JdbcTemplate jdbcTemplate;

    @GetMapping(value = "/vehicle-positions/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('telemetry:read')")
    public SseEmitter streamPositions(
            @RequestParam(required = false) UUID vehicleId,
            @RequestParam(required = false) UUID tripId,
            @RequestParam(defaultValue = "5m") String duration,
            @RequestParam String token) {

        DurationConfig cfg = DurationConfig.from(duration);
        List<PositionMessage> snapshot = loadRecentPositions(vehicleId, tripId, cfg);
        return wireSse(snapshot,
                publisher.positionFlux()
                        .filter(msg -> vehicleId == null || vehicleId.equals(msg.vehicleId()))
                        .filter(msg -> tripId == null || tripId.equals(msg.tripId())));
    }

    @GetMapping(value = "/vehicle-data/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('telemetry:read')")
    public SseEmitter streamTelemetry(
            @RequestParam(required = false) UUID vehicleId,
            @RequestParam(required = false) UUID tripId,
            @RequestParam(defaultValue = "5m") String duration,
            @RequestParam String token) {

        DurationConfig cfg = DurationConfig.from(duration);
        List<TelemetryMessage> snapshot = loadRecentTelemetry(vehicleId, tripId, cfg);
        return wireSse(snapshot,
                publisher.telemetryFlux()
                        .filter(msg -> vehicleId == null || vehicleId.equals(msg.vehicleId()))
                        .filter(msg -> tripId == null || tripId.equals(msg.tripId())));
    }

    @GetMapping(value = "/events/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('telemetry:read')")
    public SseEmitter streamEvents(
            @RequestParam(required = false) UUID vehicleId,
            @RequestParam(required = false) UUID tripId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String severity,
            @RequestParam(defaultValue = "5m") String duration,
            @RequestParam String token) {

        DurationConfig cfg = DurationConfig.from(duration);
        List<EventMessage> snapshot = loadRecentEvents(vehicleId, tripId,
                eventType, severity, cfg);
        return wireSse(snapshot,
                publisher.eventFlux()
                        .filter(msg -> vehicleId == null || vehicleId.equals(msg.vehicleId()))
                        .filter(msg -> tripId == null || tripId.equals(msg.tripId()))
                        .filter(msg -> eventType == null || eventType.equals(msg.eventType()))
                        .filter(msg -> severity == null || severity.equals(msg.severity())));
    }

    private <T> SseEmitter wireSse(List<T> snapshot, reactor.core.publisher.Flux<T> liveFlux) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        for (T msg : snapshot) {
            try {
                emitter.send(SseEmitter.event().data(msg));
            } catch (IOException e) {
                emitter.completeWithError(e);
                return emitter;
            }
        }

        Disposable sub = liveFlux.subscribe(
                msg -> {
                    try {
                        emitter.send(SseEmitter.event().data(msg));
                    } catch (IOException ignored) {
                    }
                },
                emitter::completeWithError,
                emitter::complete
        );

        emitter.onCompletion(sub::dispose);
        emitter.onError(err -> sub.dispose());
        emitter.onTimeout(sub::dispose);

        return emitter;
    }

    private List<PositionMessage> loadRecentPositions(UUID vehicleId, UUID tripId,
                                                       DurationConfig cfg) {
        return jdbcTemplate.query("""
                SELECT time, vehicle_id, trip_id, latitude, longitude, speed_kmh, heading, accuracy
                FROM telemetry.vehicle_positions
                WHERE (?::uuid IS NULL OR vehicle_id = ?::uuid)
                  AND (?::uuid IS NULL OR trip_id = ?::uuid)
                  AND time > ?::timestamptz
                ORDER BY time ASC
                LIMIT ?
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
                Timestamp.from(Instant.now().minusSeconds(cfg.seconds())),
                cfg.limit());
    }

    private List<TelemetryMessage> loadRecentTelemetry(UUID vehicleId, UUID tripId,
                                                        DurationConfig cfg) {
        return jdbcTemplate.query("""
                SELECT time, vehicle_id, trip_id, engine_rpm, fuel_level_pct,
                       engine_temp_c, odometer_km, battery_voltage
                FROM telemetry.vehicle_telemetry
                WHERE (?::uuid IS NULL OR vehicle_id = ?::uuid)
                  AND (?::uuid IS NULL OR trip_id = ?::uuid)
                  AND time > ?::timestamptz
                ORDER BY time ASC
                LIMIT ?
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
                Timestamp.from(Instant.now().minusSeconds(cfg.seconds())),
                cfg.limit());
    }

    private List<EventMessage> loadRecentEvents(UUID vehicleId, UUID tripId,
                                                 String eventType, String severity,
                                                 DurationConfig cfg) {
        return jdbcTemplate.query("""
                SELECT time, vehicle_id, trip_id, event_type, severity, message
                FROM telemetry.events
                WHERE (?::uuid IS NULL OR vehicle_id = ?::uuid)
                  AND (?::uuid IS NULL OR trip_id = ?::uuid)
                  AND (?::text IS NULL OR event_type = ?)
                  AND (?::text IS NULL OR severity = ?)
                  AND time > ?::timestamptz
                ORDER BY time ASC
                LIMIT ?
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
                Timestamp.from(Instant.now().minusSeconds(cfg.seconds())),
                cfg.limit());
    }

    private record DurationConfig(long seconds, int limit) {
        static DurationConfig from(String val) {
            return switch (val) {
                case "1h" -> new DurationConfig(3600, 5000);
                case "7d" -> new DurationConfig(604800, 10000);
                case "30d" -> new DurationConfig(2592000, 20000);
                default -> new DurationConfig(300, 500);
            };
        }
    }
}
