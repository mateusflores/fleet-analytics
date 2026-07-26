package com.unesp.desktopsimulator.app.services.client.dto;

import java.util.UUID;

public record ApiCreateTripRequest(
        UUID driverId,
        UUID vehicleId
) {}