package com.unesp.fleetanalyticsapi.app.dtos.messaging;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record PositionMessage(
        @NotNull UUID vehicleId,
        UUID tripId,
        @NotNull java.time.Instant timestamp,
        @NotNull Double latitude,
        @NotNull Double longitude,
        @PositiveOrZero Double speedKmh,
        Double heading,
        Double accuracy
) {
}
