package com.unesp.desktopsimulator.app.services.simulation;

/**
 * Parâmetros físicos de um tipo de veículo.
 *
 * <p>{@link #MEDIUM_RIGID} foi calibrado com o log CAN real do Kenworth T270 (motor PACCAR PX-7, câmbio
 * automático de 5 marchas usadas): as relações abaixo reproduzem os rpm/(km/h) medidos por marcha
 * (102 / 51 / 39 / 28 / 19,7). {@link #HEAVY_TRACTOR} representa um cavalo mecânico 6x2 típico no Brasil
 * (motor ~13 L, 460 cv, câmbio automatizado de 12 marchas, sistema elétrico de 24 V).
 */
public record VehicleProfile(
        String name,
        double massKg,
        double payloadMaxKg,
        double frontalAreaCdA,
        double rollingCoef,
        double wheelRadiusM,
        double[] gearRatios,
        int startGear,
        double finalDrive,
        double drivetrainEff,
        int idleRpm,
        int maxRpm,
        int ratedRpm,
        int upshiftRpm,
        int downshiftRpm,
        double peakTorqueNm,
        double maxPowerKw,
        double tankL,
        double idleFuelLph,
        double bsfcGPerKwh,
        double maxBoostKpa,
        double oilIdleKpa,
        double oilRatedKpa,
        double radiatorKwPerK,
        double thermalMassKjPerK,
        int systemVolts,
        double electricalLoadA,
        double batteryAh,
        double maxSpeedKmh
) {

    public static final VehicleProfile MEDIUM_RIGID = new VehicleProfile(
            "Caminhão médio (calibrado com Kenworth T270)",
            8_000, 4_000, 3.6, 0.0065, 0.49,
            new double[]{3.49, 1.86, 1.41, 1.00, 0.75}, 1, 4.88, 0.90,
            700, 2_250, 2_200, 1_950, 950,
            760, 179, 380, 2.6, 195, 150,
            210, 360, 2.0, 400,
            12, 35, 150, 90);

    public static final VehicleProfile HEAVY_TRACTOR = new VehicleProfile(
            "Cavalo mecânico 6x2 (13 L, 12 marchas)",
            16_000, 29_000, 6.0, 0.0060, 0.52,
            new double[]{14.94, 11.73, 9.04, 7.09, 5.54, 4.35, 3.44, 2.70, 2.08, 1.63, 1.27, 1.00}, 3, 3.08, 0.92,
            600, 1_900, 1_800, 1_450, 1_000,
            2_300, 340, 600, 3.0, 188, 220,
            180, 400, 3.2, 650,
            24, 60, 200, 90);

    /** Perfil a partir do {@code vehicleType} cadastrado na API. */
    public static VehicleProfile forVehicleType(String vehicleType) {
        if (vehicleType == null) return MEDIUM_RIGID;
        return switch (vehicleType) {
            case "ARTICULATED_VEHICLE" -> HEAVY_TRACTOR;
            default -> MEDIUM_RIGID;
        };
    }

    public int topGear() {
        return gearRatios.length;
    }

    /** Rotação do motor (rpm) para a velocidade (m/s) numa marcha (1-based). */
    public double rpmAt(double speedMs, int gear) {
        double wheelRpm = speedMs / wheelRadiusM * 60.0 / (2 * Math.PI);
        return wheelRpm * gearRatios[gear - 1] * finalDrive;
    }

    /** Torque máximo (N·m) disponível na rotação: sobe a partir da marcha lenta, platô e queda por potência. */
    public double maxTorqueAt(double rpm) {
        double plateauStart = idleRpm + 500;
        double rising = rpm < plateauStart
                ? peakTorqueNm * (0.55 + 0.45 * Math.max(0, rpm - idleRpm) / (plateauStart - idleRpm))
                : peakTorqueNm;
        double omega = Math.max(rpm, 1) * 2 * Math.PI / 60;
        double powerLimited = maxPowerKw * 1000 / omega;
        double governor = rpm > ratedRpm ? Math.max(0, 1 - (rpm - ratedRpm) / (double) (maxRpm - ratedRpm)) : 1;
        return Math.min(rising, powerLimited) * governor;
    }

    /** Fator de tensão: 1 para 12 V, 2 para 24 V. */
    public double voltageScale() {
        return systemVolts / 12.0;
    }
}
