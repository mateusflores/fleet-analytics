package com.unesp.fleetanalyticsapi.app.sse;

import com.unesp.fleetanalyticsapi.app.dtos.messaging.EventMessage;
import com.unesp.fleetanalyticsapi.app.dtos.messaging.PositionMessage;
import com.unesp.fleetanalyticsapi.app.dtos.messaging.TelemetryMessage;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Component
public class TelemetryEventPublisher {

    private final Sinks.Many<PositionMessage> positionSink =
            Sinks.many().multicast().directBestEffort();
    private final Sinks.Many<TelemetryMessage> telemetrySink =
            Sinks.many().multicast().directBestEffort();
    private final Sinks.Many<EventMessage> eventSink =
            Sinks.many().multicast().directBestEffort();

    public void publishPosition(PositionMessage msg) {
        positionSink.tryEmitNext(msg);
    }

    public void publishTelemetry(TelemetryMessage msg) {
        telemetrySink.tryEmitNext(msg);
    }

    public void publishEvent(EventMessage msg) {
        eventSink.tryEmitNext(msg);
    }

    public Flux<PositionMessage> positionFlux() {
        return positionSink.asFlux();
    }

    public Flux<TelemetryMessage> telemetryFlux() {
        return telemetrySink.asFlux();
    }

    public Flux<EventMessage> eventFlux() {
        return eventSink.asFlux();
    }
}
