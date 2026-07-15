package com.unesp.fleetanalyticsapi.app.dtos;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateTripRequest(
        @NotNull UUID driverId,
        @NotNull UUID vehicleId
) {
}
