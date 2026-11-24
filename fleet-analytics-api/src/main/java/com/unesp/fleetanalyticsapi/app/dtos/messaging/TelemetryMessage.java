package com.unesp.fleetanalyticsapi.app.dtos.messaging;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

/**
 * Leitura de telemetria (1 Hz) recebida do ESP32/simulador.
 *
 * <p>Os campos a partir de {@code speedKmh} são sinais J1939 adicionais (V13), usados pelo serviço de ML e pelas
 * métricas de viagem; são opcionais — dispositivos que não os enviam continuam válidos.
 */
public record TelemetryMessage(
        @NotNull UUID vehicleId,
        UUID tripId,
        @NotNull java.time.Instant timestamp,
        @PositiveOrZero Integer engineRpm,
        Double fuelLevelPct,
        Double engineTempC,
        @PositiveOrZero Double odometerKm,
        Double batteryVoltage,
        @PositiveOrZero Double speedKmh,
        Double loadPct,
        Double engineTorquePct,
        Double pedalPct,
        Integer gear,
        Double oilKpa,
        @PositiveOrZero Double fuelRateLph,
        Double boostKpa,
        Double intakeC
) {

    /** Leitura só com os sinais básicos (formato anterior à V13). */
    public TelemetryMessage(UUID vehicleId, UUID tripId, java.time.Instant timestamp, Integer engineRpm,
                            Double fuelLevelPct, Double engineTempC, Double odometerKm, Double batteryVoltage) {
        this(vehicleId, tripId, timestamp, engineRpm, fuelLevelPct, engineTempC, odometerKm, batteryVoltage,
                null, null, null, null, null, null, null, null, null);
    }
}
