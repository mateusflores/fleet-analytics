package com.unesp.desktopsimulator.app.services.client.dto;

import java.util.UUID;

public record ApiVehicleSummary(
        UUID id,
        String licensePlate,
        String brand,
        String model,
        Integer year,
        String color,
        String vehicleType,
        String nickname,
        boolean active
) {}