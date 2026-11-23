package com.unesp.fleetanalyticsapi.app.dtos;

import com.unesp.fleetanalyticsapi.model.enums.VehicleType;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public record FleetPolicySummary(
        Double speedLimitKmh,
        Double harshAccelMs2,
        Double harshBrakeMs2,
        Integer idleThresholdS,
        BigDecimal fuelPricePerLiter,
        List<VehicleTypeRpm> vehicleTypes,
        ZonedDateTime updatedAt,
        UUID updatedBy
) {
    public record VehicleTypeRpm(VehicleType vehicleType, Integer economyRpmMax) {
    }
}
