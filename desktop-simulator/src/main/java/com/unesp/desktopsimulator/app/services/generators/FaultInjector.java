package com.unesp.desktopsimulator.app.services.generators;

import com.unesp.desktopsimulator.model.FaultType;
import com.unesp.desktopsimulator.model.telemetry.Position;
import com.unesp.desktopsimulator.model.telemetry.Telemetry;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.Set;

@Service
public class FaultInjector {

    private static final double OVERHEAT_DRIFT_C = 0.3;
    private static final double BATTERY_DRAIN_V = 0.01;
    private static final double FUEL_LEAK_PCT = 0.02;
    private static final double GPS_DRIFT_DEG = 0.0001;
    private static final int IDLE_RPM = 700;
    private static final int MAX_RPM = 4000;

    private final Random random = new Random();

    public Position applyToPosition(Position original, Set<FaultType> faults) {
        if (!faults.contains(FaultType.GPS_DRIFT)) {
            return original;
        }

        double lat = original.getLatitude() + (random.nextDouble() - 0.5) * 2 * GPS_DRIFT_DEG;
        double lon = original.getLongitude() + (random.nextDouble() - 0.5) * 2 * GPS_DRIFT_DEG;
        double degradedAccuracy = original.getAccuracy() + 15.0 + random.nextDouble() * 10;

        Position modified = new Position.Builder()
                .timestamp(original.getTimestamp())
                .latitude(lat)
                .longitude(lon)
                .speedKmh(original.getSpeedKmh())
                .heading(original.getHeading())
                .accuracy(degradedAccuracy)
                .build();
        modified.setVehicleId(original.getVehicleId());
        modified.setTripId(original.getTripId());
        modified.setDriverId(original.getDriverId());
        return modified;
    }

    public Telemetry applyToTelemetry(Telemetry original, Set<FaultType> faults) {
        if (faults.isEmpty()) {
            return original;
        }

        double temp = original.getEngineTempC();
        double voltage = original.getBatteryVoltage();
        double fuel = original.getFuelLevelPct();
        int rpm = original.getEngineRpm();

        if (faults.contains(FaultType.OVERHEATING)) {
            temp += OVERHEAT_DRIFT_C;
        }

        if (faults.contains(FaultType.BATTERY_DRAIN)) {
            voltage -= BATTERY_DRAIN_V;
            voltage = Math.max(0, voltage);
        }

        if (faults.contains(FaultType.FUEL_LEAK)) {
            fuel -= FUEL_LEAK_PCT;
            fuel = Math.max(0, fuel);
        }

        if (faults.contains(FaultType.ERRATIC_RPM)) {
            double sinusoid = Math.sin(System.currentTimeMillis() / 500.0) * 1500 + 1800;
            rpm = IDLE_RPM + (int) Math.round(sinusoid);
            rpm += (int) randomGaussian(0, 200);
            rpm = Math.max(IDLE_RPM, Math.min(MAX_RPM, rpm));
        }

        Telemetry modified = new Telemetry.Builder()
                .timestamp(original.getTimestamp())
                .engineRpm(rpm)
                .fuelLevelPct(fuel)
                .engineTempC(temp)
                .odometerKm(original.getOdometerKm())
                .batteryVoltage(voltage)
                .build();
        modified.setVehicleId(original.getVehicleId());
        modified.setTripId(original.getTripId());
        modified.setDriverId(original.getDriverId());
        return modified;
    }

    private double randomGaussian(double mean, double stddev) {
        return mean + random.nextGaussian() * stddev;
    }

}
