package com.unesp.fleetanalyticsapi.app.controllers;

import com.unesp.fleetanalyticsapi.app.dtos.messaging.EventMessage;
import com.unesp.fleetanalyticsapi.app.dtos.messaging.PositionMessage;
import com.unesp.fleetanalyticsapi.app.dtos.messaging.TelemetryMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class TelemetryRestControllerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private TelemetryRestController controller;

    private static final Instant FROM = Instant.parse("2025-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2025-01-15T23:59:59Z");
    private static final Pageable PAGE = PageRequest.of(0, 100);

    @BeforeEach
    void setUp() {
        controller = new TelemetryRestController(jdbcTemplate);
    }

    @Test
    void shouldReturnPaginatedPositions() {
        PositionMessage row = new PositionMessage(
                UUID.randomUUID(), null, FROM, -23.5, -46.6, 60.0, 180.0, 5.0);

        doReturn(1L).when(jdbcTemplate)
                .queryForObject(anyString(), eq(Long.class), any(Object[].class));

        doReturn(List.of(row)).when(jdbcTemplate)
                .query(anyString(), any(RowMapper.class), any(Object[].class));

        ResponseEntity<Page<PositionMessage>> response =
                controller.findPositions(FROM, TO, null, null, PAGE);

        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getTotalElements());
        assertEquals(-23.5, response.getBody().getContent().getFirst().latitude());
    }

    @Test
    void shouldReturnEmptyPositionsPage() {
        doReturn(0L).when(jdbcTemplate)
                .queryForObject(anyString(), eq(Long.class), any(Object[].class));

        doReturn(Collections.emptyList()).when(jdbcTemplate)
                .query(anyString(), any(RowMapper.class), any(Object[].class));

        ResponseEntity<Page<PositionMessage>> response =
                controller.findPositions(FROM, TO, null, null, PAGE);

        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
        assertEquals(0, response.getBody().getTotalElements());
    }

    @Test
    void shouldReturnPaginatedTelemetry() {
        TelemetryMessage row = new TelemetryMessage(
                UUID.randomUUID(), null, FROM, 2500, 75.5, 90.0, 123456.0, 12.6);

        doReturn(1L).when(jdbcTemplate)
                .queryForObject(anyString(), eq(Long.class), any(Object[].class));

        doReturn(List.of(row)).when(jdbcTemplate)
                .query(anyString(), any(RowMapper.class), any(Object[].class));

        ResponseEntity<Page<TelemetryMessage>> response =
                controller.findTelemetry(FROM, TO, null, null, PAGE);

        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getTotalElements());
        assertEquals(2500, response.getBody().getContent().getFirst().engineRpm());
    }

    @Test
    void shouldReturnPaginatedEvents() {
        EventMessage row = new EventMessage(
                UUID.randomUUID(), null, FROM, "harsh_braking", "warning", "test");

        doReturn(1L).when(jdbcTemplate)
                .queryForObject(anyString(), eq(Long.class), any(Object[].class));

        doReturn(List.of(row)).when(jdbcTemplate)
                .query(anyString(), any(RowMapper.class), any(Object[].class));

        ResponseEntity<Page<EventMessage>> response =
                controller.findEvents(FROM, TO, null, null, null, null, PAGE);

        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getTotalElements());
        assertEquals("harsh_braking",
                response.getBody().getContent().getFirst().eventType());
    }

    @Test
    void shouldFilterEventsByTypeAndSeverity() {
        EventMessage row = new EventMessage(
                UUID.randomUUID(), null, FROM, "speeding", "critical", "over limit");

        doReturn(1L).when(jdbcTemplate)
                .queryForObject(anyString(), eq(Long.class), any(Object[].class));

        doReturn(List.of(row)).when(jdbcTemplate)
                .query(anyString(), any(RowMapper.class), any(Object[].class));

        ResponseEntity<Page<EventMessage>> response =
                controller.findEvents(FROM, TO, null, null,
                        "speeding", "critical", PAGE);

        assertEquals(1, response.getBody().getTotalElements());
        assertEquals("speeding", response.getBody().getContent().getFirst().eventType());
        assertEquals("critical", response.getBody().getContent().getFirst().severity());
    }
}
