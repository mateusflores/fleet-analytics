package com.unesp.fleetanalyticsapi.app.dtos.messaging;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record TelemetryMessage(
        @NotNull UUID vehicleId,
        UUID tripId,
        @NotNull java.time.Instant timestamp,
        @PositiveOrZero Integer engineRpm,
        Double fuelLevelPct,
        Double engineTempC,
        @PositiveOrZero Double odometerKm,
        Double batteryVoltage
) {
}
