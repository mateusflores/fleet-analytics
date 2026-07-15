package com.unesp.fleetanalyticsapi.app.dtos;

import java.time.ZonedDateTime;
import java.util.UUID;

public record TripSummary(
        UUID id,
        UUID driverId,
        String driverName,
        UUID vehicleId,
        String vehiclePlate,
        ZonedDateTime startTime,
        ZonedDateTime endTime
) {
}
