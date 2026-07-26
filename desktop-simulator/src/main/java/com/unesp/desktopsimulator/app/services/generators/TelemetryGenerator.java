package com.unesp.desktopsimulator.app.services.generators;

import com.unesp.desktopsimulator.model.telemetry.Position;
import com.unesp.desktopsimulator.model.telemetry.Telemetry;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Random;

@Service
public class TelemetryGenerator {

    private static final int IDLE_RPM = 700;
    private static final int MAX_RPM = 4000;
    private static final double RPM_PER_KMH = 40.0;
    private static final double RPM_EMA_ALPHA = 0.3;

    private static final double BASE_TEMP_C = 85.0;
    private static final double MAX_TEMP_DELTA_C = 35.0;
    private static final double TEMP_EMA_ALPHA = 0.2;
    private static final double COOLDOWN_RATE_C = 0.02;

    private static final double FUEL_CONSUMPTION_FACTOR = 0.00001;
    private static final double MAX_FUEL_PCT = 100.0;

    private static final double INITIAL_ODOMETER_MIN = 10_000.0;
    private static final double INITIAL_ODOMETER_MAX = 100_000.0;

    private static final double NOMINAL_VOLTAGE = 13.8;
    private static final double MIN_VOLTAGE = 11.0;
    private static final double MAX_VOLTAGE = 14.4;
    private static final double VOLTAGE_DRAIN_RATE = 0.005;

    private final Random random = new Random();

    public Telemetry generate(Telemetry lastTelemetry, Position currentPosition) {
        Instant now = Instant.now();

        if (lastTelemetry == null) {
            return initialTelemetry(now);
        }

        double hoursDelta = deltaHours(lastTelemetry.getTimestamp(), now);
        if (hoursDelta <= 0) {
            hoursDelta = 1.0 / 3600.0;
        }

        double speed = currentPosition.getSpeedKmh();
        int newRpm = calcEngineRpm(lastTelemetry.getEngineRpm(), speed);
        double newEngineTemp = calcEngineTempC(lastTelemetry.getEngineTempC(), newRpm);
        double newFuelLevel = calcFuelLevelPct(lastTelemetry.getFuelLevelPct(), newRpm, hoursDelta);
        double newOdometer = calcOdometer(lastTelemetry.getOdometerKm(), speed, hoursDelta);
        double newBatteryVoltage = calcBatteryVoltage(lastTelemetry.getBatteryVoltage(), newRpm, hoursDelta);

        return new Telemetry.Builder()
                .timestamp(now)
                .engineRpm(newRpm)
                .fuelLevelPct(newFuelLevel)
                .engineTempC(newEngineTemp)
                .odometerKm(newOdometer)
                .batteryVoltage(newBatteryVoltage)
                .build();
    }

    private Telemetry initialTelemetry(Instant now) {
        double odo = INITIAL_ODOMETER_MIN + random.nextDouble()
                * (INITIAL_ODOMETER_MAX - INITIAL_ODOMETER_MIN);
        return new Telemetry.Builder()
                .timestamp(now)
                .engineRpm(IDLE_RPM)
                .fuelLevelPct(MAX_FUEL_PCT)
                .engineTempC(BASE_TEMP_C)
                .odometerKm(odo)
                .batteryVoltage(NOMINAL_VOLTAGE)
                .build();
    }

    private int calcEngineRpm(int lastRpm, double speedKmh) {
        int targetRpm = IDLE_RPM + (int) Math.round(speedKmh * RPM_PER_KMH);
        targetRpm += (int) randomGaussian(0, 50);
        targetRpm = Math.max(IDLE_RPM, Math.min(MAX_RPM, targetRpm));

        int newRpm = (int) Math.round(RPM_EMA_ALPHA * targetRpm + (1 - RPM_EMA_ALPHA) * lastRpm);
        return Math.max(IDLE_RPM, Math.min(MAX_RPM, newRpm));
    }

    private double calcEngineTempC(double lastTemp, int engineRpm) {
        double rpmRatio = (double) (engineRpm - IDLE_RPM) / (MAX_RPM - IDLE_RPM);
        double targetTemp = BASE_TEMP_C + rpmRatio * MAX_TEMP_DELTA_C;
        targetTemp += randomGaussian(0, 1);

        double newTemp = TEMP_EMA_ALPHA * targetTemp + (1 - TEMP_EMA_ALPHA) * lastTemp;

        if (engineRpm <= IDLE_RPM + 50) {
            newTemp -= COOLDOWN_RATE_C;
        }

        return Math.max(BASE_TEMP_C - 5, newTemp);
    }

    private double calcFuelLevelPct(double lastFuel, int engineRpm, double hoursDelta) {
        double consumption = engineRpm * FUEL_CONSUMPTION_FACTOR * hoursDelta * 3600;
        double newFuel = lastFuel - consumption;
        return clamp(newFuel, 0, MAX_FUEL_PCT);
    }

    private double calcOdometer(double lastOdometer, double speedKmh, double hoursDelta) {
        double distance = speedKmh * hoursDelta;
        return lastOdometer + distance;
    }

    private double calcBatteryVoltage(double lastVoltage, int engineRpm, double hoursDelta) {
        double target;
        if (engineRpm < 500) {
            target = lastVoltage - VOLTAGE_DRAIN_RATE * hoursDelta * 3600;
        } else {
            target = NOMINAL_VOLTAGE + randomGaussian(0, 0.1);
        }
        return clamp(target, MIN_VOLTAGE, MAX_VOLTAGE);
    }

    private double deltaHours(Instant from, Instant to) {
        return Duration.between(from, to).toMillis() / (1000.0 * 3600.0);
    }

    private double randomGaussian(double mean, double stddev) {
        return mean + random.nextGaussian() * stddev;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

}
