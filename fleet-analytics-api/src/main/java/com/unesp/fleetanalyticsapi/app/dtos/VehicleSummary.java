package com.unesp.fleetanalyticsapi.app.dtos;

import com.unesp.fleetanalyticsapi.model.enums.VehicleType;

import java.math.BigDecimal;
import java.util.UUID;

public record VehicleSummary(
        UUID id,
        String serialNumber,
        String licensePlate,
        String brand,
        String model,
        Integer year,
        String color,
        BigDecimal mileageKm,
        VehicleType vehicleType,
        BigDecimal maxLoadKg,
        String fuelType,
        String nickname,
        String renavam,
        boolean active
) {
}
