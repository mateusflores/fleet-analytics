package com.unesp.fleetanalyticsapi.app.controllers;

import com.unesp.fleetanalyticsapi.app.dtos.messaging.EventMessage;
import com.unesp.fleetanalyticsapi.app.dtos.messaging.PositionMessage;
import com.unesp.fleetanalyticsapi.app.dtos.messaging.TelemetryMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/telemetry")
@RequiredArgsConstructor
public class TelemetryRestController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/vehicle-positions")
    @PreAuthorize("hasAuthority('telemetry:read')")
    public ResponseEntity<List<PositionMessage>> findPositions(
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(required = false) UUID vehicleId,
            @RequestParam(required = false) UUID tripId) {

        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM telemetry.vehicle_positions
                WHERE time >= ?::timestamptz AND time <= ?::timestamptz
                  AND (?::uuid IS NULL OR vehicle_id = ?::uuid)
                  AND (?::uuid IS NULL OR trip_id = ?::uuid)
                """, Long.class, Timestamp.from(from), Timestamp.from(to), vehicleId, vehicleId, tripId, tripId);

        List<PositionMessage> data = jdbcTemplate.query("""
                SELECT time, vehicle_id, trip_id, latitude, longitude, speed_kmh, heading, accuracy
                FROM telemetry.vehicle_positions
                WHERE time >= ?::timestamptz AND time <= ?::timestamptz
                  AND (?::uuid IS NULL OR vehicle_id = ?::uuid)
                  AND (?::uuid IS NULL OR trip_id = ?::uuid)
                ORDER BY time ASC
                LIMIT ? OFFSET ?
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
                Timestamp.from(from), Timestamp.from(to),
                vehicleId, vehicleId, tripId, tripId);

        return ResponseEntity.ok(data);
    }

    @GetMapping("/vehicle-data")
    @PreAuthorize("hasAuthority('telemetry:read')")
    public ResponseEntity<List<TelemetryMessage>> findTelemetry(
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(required = false) UUID vehicleId,
            @RequestParam(required = false) UUID tripId) {

        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM telemetry.vehicle_telemetry
                WHERE time >= ?::timestamptz AND time <= ?::timestamptz
                  AND (?::uuid IS NULL OR vehicle_id = ?::uuid)
                  AND (?::uuid IS NULL OR trip_id = ?::uuid)
                """, Long.class, Timestamp.from(from), Timestamp.from(to), vehicleId, vehicleId, tripId, tripId);

        List<TelemetryMessage> data = jdbcTemplate.query("""
                SELECT time, vehicle_id, trip_id, engine_rpm, fuel_level_pct,
                       engine_temp_c, odometer_km, battery_voltage
                FROM telemetry.vehicle_telemetry
                WHERE time >= ?::timestamptz AND time <= ?::timestamptz
                  AND (?::uuid IS NULL OR vehicle_id = ?::uuid)
                  AND (?::uuid IS NULL OR trip_id = ?::uuid)
                ORDER BY time ASC
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
                Timestamp.from(from), Timestamp.from(to),
                vehicleId, vehicleId, tripId, tripId);

        return ResponseEntity.ok(data);
    }

    @GetMapping("/events")
    @PreAuthorize("hasAuthority('telemetry:read')")
    public ResponseEntity<List<EventMessage>> findEvents(
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(required = false) UUID vehicleId,
            @RequestParam(required = false) UUID tripId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String severity) {

        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM telemetry.events
                WHERE time >= ?::timestamptz AND time <= ?::timestamptz
                  AND (?::uuid IS NULL OR vehicle_id = ?::uuid)
                  AND (?::uuid IS NULL OR trip_id = ?::uuid)
                  AND (?::text IS NULL OR event_type = ?)
                  AND (?::text IS NULL OR severity = ?)
                """, Long.class,
                Timestamp.from(from), Timestamp.from(to), vehicleId, vehicleId, tripId, tripId,
                eventType, eventType, severity, severity);

        List<EventMessage> data = jdbcTemplate.query("""
                SELECT time, vehicle_id, trip_id, event_type, severity, message
                FROM telemetry.events
                WHERE time >= ?::timestamptz AND time <= ?::timestamptz
                  AND (?::uuid IS NULL OR vehicle_id = ?::uuid)
                  AND (?::uuid IS NULL OR trip_id = ?::uuid)
                  AND (?::text IS NULL OR event_type = ?)
                  AND (?::text IS NULL OR severity = ?)
                ORDER BY time ASC
                """,
                (rs, rowNum) -> new EventMessage(
                        rs.getObject("vehicle_id", UUID.class),
                        rs.getObject("trip_id", UUID.class),
                        rs.getTimestamp("time").toInstant(),
                        rs.getString("event_type"),
                        rs.getString("severity"),
                        rs.getString("message")
                ),
                Timestamp.from(from), Timestamp.from(to), vehicleId, vehicleId, tripId, tripId,
                eventType, eventType, severity, severity);

        return ResponseEntity.ok(data);
    }
}
