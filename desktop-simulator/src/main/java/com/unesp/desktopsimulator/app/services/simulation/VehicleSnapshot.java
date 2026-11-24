package com.unesp.desktopsimulator.app.services.simulation;

/** Estado observável do veículo num instante: o que os sensores (J1939 e GPS) reportariam. */
public record VehicleSnapshot(
        double elapsedS,
        double latitude,
        double longitude,
        double headingDeg,
        double gpsAccuracyM,
        double speedKmh,
        int engineRpm,
        int gear,
        double loadPct,
        double engineTorquePct,
        double pedalPct,
        double coolantC,
        double oilKpa,
        double fuelRateLph,
        double fuelLevelPct,
        double boostKpa,
        double intakeC,
        double batteryV,
        double odometerKm,
        String phase,
        double gradePct,
        double speedLimitKmh,
        double legProgress,
        boolean returning
) {
}
