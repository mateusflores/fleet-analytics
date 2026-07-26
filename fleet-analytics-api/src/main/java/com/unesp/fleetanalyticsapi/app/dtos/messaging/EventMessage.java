package com.unesp.fleetanalyticsapi.app.dtos.messaging;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record EventMessage(
        @NotNull UUID vehicleId,
        UUID tripId,
        @NotNull java.time.Instant timestamp,
        @NotBlank String eventType,
        String severity,
        String message
) {
}
