package com.unesp.desktopsimulator.app.services.client.dto;

import java.time.ZonedDateTime;
import java.util.UUID;

public record ApiTripSummary(
        UUID id,
        UUID driverId,
        UUID vehicleId,
        ZonedDateTime startTime,
        ZonedDateTime endTime
) {}