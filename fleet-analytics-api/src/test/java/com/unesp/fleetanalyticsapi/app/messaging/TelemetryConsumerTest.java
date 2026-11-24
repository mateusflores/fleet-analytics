package com.unesp.fleetanalyticsapi.app.messaging;

import com.unesp.fleetanalyticsapi.app.dtos.messaging.EventMessage;
import com.unesp.fleetanalyticsapi.app.dtos.messaging.PositionMessage;
import com.unesp.fleetanalyticsapi.app.dtos.messaging.TelemetryMessage;
import com.unesp.fleetanalyticsapi.app.sse.TelemetryEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelemetryConsumerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private TelemetryEventPublisher eventPublisher;

    @InjectMocks
    private TelemetryConsumer telemetryConsumer;

    @Test
    void shouldPersistAndPublishPosition() {
        PositionMessage msg = new PositionMessage(
                UUID.randomUUID(), null, Instant.now(),
                -23.5, -46.6, 60.0, 180.0, 5.0);

        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        telemetryConsumer.handlePosition(msg);

        verify(jdbcTemplate).update(anyString(), any(Object[].class));
        verify(eventPublisher).publishPosition(msg);
    }

    @Test
    void shouldPersistExtendedSignals() {
        TelemetryMessage msg = new TelemetryMessage(
                UUID.randomUUID(), UUID.randomUUID(), Instant.now(),
                1800, 70.0, 90.0, 1000.0, 14.0,
                82.5, 47.0, 40.0, 30.0, 5, 340.0, 18.2, 66.0, 50.0);

        telemetryConsumer.handleTelemetry(msg);

        org.mockito.ArgumentCaptor<Object[]> args = org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(anyString(), args.capture());
        org.assertj.core.api.Assertions.assertThat(args.getValue())
                .hasSize(17)
                .endsWith(82.5, 47.0, 40.0, 30.0, 5, 340.0, 18.2, 66.0, 50.0);
    }

    @Test
    void shouldPersistAndPublishTelemetry() {
        TelemetryMessage msg = new TelemetryMessage(
                UUID.randomUUID(), null, Instant.now(),
                2500, 75.5, 90.0, 123456.0, 12.6);

        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        telemetryConsumer.handleTelemetry(msg);

        verify(jdbcTemplate).update(anyString(), any(Object[].class));
        verify(eventPublisher).publishTelemetry(msg);
    }

    @Test
    void shouldPersistAndPublishEvent() {
        EventMessage msg = new EventMessage(
                UUID.randomUUID(), null, Instant.now(),
                "harsh_braking", "warning", "Braking too hard");

        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        telemetryConsumer.handleEvent(msg);

        verify(jdbcTemplate).update(anyString(), any(Object[].class));
        verify(eventPublisher).publishEvent(msg);
    }
}
