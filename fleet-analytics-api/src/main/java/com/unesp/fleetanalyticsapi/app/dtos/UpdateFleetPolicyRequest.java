package com.unesp.fleetanalyticsapi.app.dtos;

import com.unesp.fleetanalyticsapi.model.enums.VehicleType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

/** Atualização das políticas da frota. {@code vehicleTypes} é opcional: só os tipos enviados são alterados. */
public record UpdateFleetPolicyRequest(
        @NotNull @DecimalMin("20") @DecimalMax("150") Double speedLimitKmh,
        @NotNull @DecimalMin("0.1") @DecimalMax("5") Double harshAccelMs2,
        @NotNull @DecimalMin("0.1") @DecimalMax("10") Double harshBrakeMs2,
        @NotNull @Min(30) @Max(3600) Integer idleThresholdS,
        @NotNull @Positive BigDecimal fuelPricePerLiter,
        List<@Valid VehicleTypeRpm> vehicleTypes
) {
    public record VehicleTypeRpm(@NotNull VehicleType vehicleType, @NotNull @Min(500) @Max(8000) Integer economyRpmMax) {
    }
}
