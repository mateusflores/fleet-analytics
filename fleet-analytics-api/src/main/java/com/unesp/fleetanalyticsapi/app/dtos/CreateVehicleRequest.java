package com.unesp.fleetanalyticsapi.app.dtos;

import com.unesp.fleetanalyticsapi.model.enums.VehicleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record CreateVehicleRequest(
        @NotBlank String serialNumber,
        @NotBlank String licensePlate,
        @NotBlank String brand,
        @NotBlank String model,
        Integer year,
        String color,
        @PositiveOrZero BigDecimal mileageKm,
        @NotNull VehicleType vehicleType,
        @PositiveOrZero BigDecimal maxLoadKg,
        String fuelType,
        String nickname,
        String renavam
) {
}
