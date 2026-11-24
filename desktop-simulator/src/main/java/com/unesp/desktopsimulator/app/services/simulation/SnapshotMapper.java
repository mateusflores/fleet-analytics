package com.unesp.desktopsimulator.app.services.simulation;

import com.unesp.desktopsimulator.model.telemetry.Position;
import com.unesp.desktopsimulator.model.telemetry.Telemetry;

import java.time.Instant;

/** Converte o estado do modelo nas mensagens publicadas por MQTT. */
public final class SnapshotMapper {

    private SnapshotMapper() {
    }

    public static Telemetry toTelemetry(VehicleSnapshot s, Instant timestamp) {
        return new Telemetry.Builder()
                .timestamp(timestamp)
                .engineRpm(s.engineRpm())
                .fuelLevelPct(s.fuelLevelPct())
                .engineTempC(s.coolantC())
                .odometerKm(s.odometerKm())
                .batteryVoltage(s.batteryV())
                .speedKmh(s.speedKmh())
                .loadPct(s.loadPct())
                .engineTorquePct(s.engineTorquePct())
                .pedalPct(s.pedalPct())
                .gear(s.gear())
                .oilKpa(s.oilKpa())
                .fuelRateLph(s.fuelRateLph())
                .boostKpa(s.boostKpa())
                .intakeC(s.intakeC())
                .build();
    }

    public static Position toPosition(VehicleSnapshot s, Instant timestamp) {
        return new Position.Builder()
                .timestamp(timestamp)
                .latitude(s.latitude())
                .longitude(s.longitude())
                .speedKmh(s.speedKmh())
                .heading(s.headingDeg())
                .accuracy(s.gpsAccuracyM())
                .build();
    }
}
