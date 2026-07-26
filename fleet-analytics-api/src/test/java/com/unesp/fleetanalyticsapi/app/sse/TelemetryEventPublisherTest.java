package com.unesp.fleetanalyticsapi.app.sse;

import com.unesp.fleetanalyticsapi.app.dtos.messaging.EventMessage;
import com.unesp.fleetanalyticsapi.app.dtos.messaging.PositionMessage;
import com.unesp.fleetanalyticsapi.app.dtos.messaging.TelemetryMessage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TelemetryEventPublisherTest {

    private final TelemetryEventPublisher publisher = new TelemetryEventPublisher();

    @Test
    void shouldDeliverPositionToSubscriber() {
        List<PositionMessage> received = new ArrayList<>();
        publisher.positionFlux().subscribe(received::add);

        PositionMessage msg = new PositionMessage(
                UUID.randomUUID(), null, Instant.now(),
                -23.5, -46.6, 60.0, 180.0, 5.0);
        publisher.publishPosition(msg);

        assertEquals(1, received.size());
        assertEquals(msg, received.getFirst());
    }

    @Test
    void shouldDeliverTelemetryToSubscriber() {
        List<TelemetryMessage> received = new ArrayList<>();
        publisher.telemetryFlux().subscribe(received::add);

        TelemetryMessage msg = new TelemetryMessage(
                UUID.randomUUID(), null, Instant.now(),
                2500, 75.5, 90.0, 123456.0, 12.6);
        publisher.publishTelemetry(msg);

        assertEquals(1, received.size());
        assertEquals(msg, received.getFirst());
    }

    @Test
    void shouldDeliverEventToSubscriber() {
        List<EventMessage> received = new ArrayList<>();
        publisher.eventFlux().subscribe(received::add);

        EventMessage msg = new EventMessage(
                UUID.randomUUID(), null, Instant.now(),
                "harsh_braking", "warning", "Braking too hard");
        publisher.publishEvent(msg);

        assertEquals(1, received.size());
        assertEquals(msg, received.getFirst());
    }

    @Test
    void shouldDeliverToMultipleSubscribers() {
        PositionMessage msg = new PositionMessage(
                UUID.randomUUID(), null, Instant.now(),
                -23.5, -46.6, 60.0, 180.0, 5.0);

        List<PositionMessage> received1 = new ArrayList<>();
        List<PositionMessage> received2 = new ArrayList<>();

        publisher.positionFlux().subscribe(received1::add);
        publisher.positionFlux().subscribe(received2::add);

        publisher.publishPosition(msg);

        assertEquals(1, received1.size());
        assertEquals(1, received2.size());
        assertEquals(msg, received1.getFirst());
        assertEquals(msg, received2.getFirst());
    }

    @Test
    void subscriberShouldOnlyReceiveMessagesAfterSubscription() {
        PositionMessage firstMsg = new PositionMessage(
                UUID.randomUUID(), null, Instant.now(),
                -23.5, -46.6, 60.0, 180.0, 5.0);

        PositionMessage secondMsg = new PositionMessage(
                UUID.randomUUID(), null, Instant.now(),
                -23.6, -46.7, 65.0, 200.0, 4.0);

        publisher.publishPosition(firstMsg);

        List<PositionMessage> received = new ArrayList<>();
        publisher.positionFlux().subscribe(received::add);
        publisher.publishPosition(secondMsg);

        assertEquals(1, received.size());
        assertEquals(secondMsg, received.getFirst());
    }

    @Test
    void shouldNotDeliverMessageOfDifferentType() {
        PositionMessage positionMsg = new PositionMessage(
                UUID.randomUUID(), null, Instant.now(),
                -23.5, -46.6, 60.0, 180.0, 5.0);

        TelemetryMessage telemetryMsg = new TelemetryMessage(
                UUID.randomUUID(), null, Instant.now(),
                2500, 75.5, 90.0, 123456.0, 12.6);

        List<PositionMessage> positions = new ArrayList<>();
        publisher.positionFlux().subscribe(positions::add);

        publisher.publishTelemetry(telemetryMsg);

        assertEquals(0, positions.size());

        publisher.publishPosition(positionMsg);

        assertEquals(1, positions.size());
        assertEquals(positionMsg, positions.getFirst());
    }
}
