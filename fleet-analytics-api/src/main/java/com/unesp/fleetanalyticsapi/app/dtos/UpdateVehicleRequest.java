package com.unesp.fleetanalyticsapi.app.dtos;

import com.unesp.fleetanalyticsapi.model.enums.VehicleType;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record UpdateVehicleRequest(
        String serialNumber,
        String licensePlate,
        String brand,
        String model,
        Integer year,
        String color,
        @PositiveOrZero BigDecimal mileageKm,
        VehicleType vehicleType,
        @PositiveOrZero BigDecimal maxLoadKg,
        String fuelType,
        String nickname,
        String renavam,
        boolean active
) {
}
