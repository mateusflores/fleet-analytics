package com.unesp.desktopsimulator.app.services.generators;

import com.unesp.desktopsimulator.model.FaultType;
import com.unesp.desktopsimulator.model.telemetry.Position;
import com.unesp.desktopsimulator.model.telemetry.Telemetry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FaultInjectorTest {

    private final FaultInjector injector = new FaultInjector();

    @Test
    @DisplayName("Should return original position when no GPS_DRIFT fault")
    void shouldNotModifyPositionWithoutGpsDrift() {
        Position original = createPosition(-23.5505, -46.6333, 5.0);
        Set<FaultType> faults = Collections.emptySet();

        Position result = injector.applyToPosition(original, faults);

        assertEquals(original.getLatitude(), result.getLatitude());
        assertEquals(original.getLongitude(), result.getLongitude());
        assertEquals(original.getAccuracy(), result.getAccuracy());
    }

    @Test
    @DisplayName("Should return original telemetry when no faults")
    void shouldNotModifyTelemetryWithoutFaults() {
        Telemetry original = createTelemetry(1500, 95.0, 50.0, 13.8);
        Set<FaultType> faults = Collections.emptySet();

        Telemetry result = injector.applyToTelemetry(original, faults);

        assertEquals(original.getEngineRpm(), result.getEngineRpm());
        assertEquals(original.getEngineTempC(), result.getEngineTempC());
        assertEquals(original.getFuelLevelPct(), result.getFuelLevelPct());
        assertEquals(original.getBatteryVoltage(), result.getBatteryVoltage());
    }

    @Test
    @DisplayName("Should shift position and degrade accuracy with GPS_DRIFT")
    void shouldShiftPositionWithGpsDrift() {
        Position original = createPosition(-23.5505, -46.6333, 5.0);
        Set<FaultType> faults = EnumSet.of(FaultType.GPS_DRIFT);

        Position result = injector.applyToPosition(original, faults);

        assertTrue(result.getAccuracy() > original.getAccuracy(),
                "Accuracy should be degraded, was " + original.getAccuracy()
                        + " now " + result.getAccuracy());
    }

    @RepeatedTest(10)
    @DisplayName("Should increase temperature with OVERHEATING fault")
    void shouldIncreaseTemperatureWithOverheating() {
        Telemetry original = createTelemetry(2000, 95.0, 50.0, 13.8);
        Set<FaultType> faults = EnumSet.of(FaultType.OVERHEATING);

        Telemetry result = injector.applyToTelemetry(original, faults);

        assertTrue(result.getEngineTempC() > original.getEngineTempC(),
                "Temperature should increase with OVERHEATING");
        assertEquals(original.getEngineTempC() + 0.3, result.getEngineTempC(), 0.0001);
    }

    @Test
    @DisplayName("Should decrease battery voltage with BATTERY_DRAIN")
    void shouldDecreaseBatteryWithBatteryDrain() {
        Telemetry original = createTelemetry(1500, 95.0, 50.0, 12.0);
        Set<FaultType> faults = EnumSet.of(FaultType.BATTERY_DRAIN);

        Telemetry result = injector.applyToTelemetry(original, faults);

        assertTrue(result.getBatteryVoltage() < original.getBatteryVoltage(),
                "Battery should drain");
        assertEquals(original.getBatteryVoltage() - 0.01, result.getBatteryVoltage(), 0.0001);
    }

    @Test
    @DisplayName("Should decrease fuel with FUEL_LEAK")
    void shouldDecreaseFuelWithFuelLeak() {
        Telemetry original = createTelemetry(1500, 95.0, 50.0, 13.8);
        Set<FaultType> faults = EnumSet.of(FaultType.FUEL_LEAK);

        Telemetry result = injector.applyToTelemetry(original, faults);

        assertTrue(result.getFuelLevelPct() < original.getFuelLevelPct(),
                "Fuel should leak");
        assertEquals(original.getFuelLevelPct() - 0.02, result.getFuelLevelPct(), 0.0001);
    }

    @Test
    @DisplayName("Battery should not go below zero with BATTERY_DRAIN")
    void shouldClampBatteryAtZero() {
        Telemetry original = createTelemetry(1500, 95.0, 50.0, 0.005);
        Set<FaultType> faults = EnumSet.of(FaultType.BATTERY_DRAIN);

        Telemetry result = injector.applyToTelemetry(original, faults);

        assertTrue(result.getBatteryVoltage() >= 0,
                "Battery should not go negative");
    }

    @Test
    @DisplayName("Fuel should not go below zero with FUEL_LEAK")
    void shouldClampFuelAtZero() {
        Telemetry original = createTelemetry(1500, 95.0, 0.01, 13.8);
        Set<FaultType> faults = EnumSet.of(FaultType.FUEL_LEAK);

        Telemetry result = injector.applyToTelemetry(original, faults);

        assertTrue(result.getFuelLevelPct() >= 0,
                "Fuel should not go negative");
    }

    @RepeatedTest(20)
    @DisplayName("ERRATIC_RPM should produce RPM different from original")
    void shouldProduceErraticRpm() {
        Telemetry original = createTelemetry(2000, 95.0, 50.0, 13.8);
        Set<FaultType> faults = EnumSet.of(FaultType.ERRATIC_RPM);

        Telemetry result = injector.applyToTelemetry(original, faults);

        assertTrue(result.getEngineRpm() >= 700, "RPM should be >= 700");
        assertTrue(result.getEngineRpm() <= 4000, "RPM should be <= 4000");
    }

    @Test
    @DisplayName("Multiple faults should apply cumulatively")
    void shouldApplyMultipleFaultsCumulatively() {
        Telemetry original = createTelemetry(2000, 100.0, 50.0, 13.0);
        Set<FaultType> faults = EnumSet.of(
                FaultType.OVERHEATING,
                FaultType.BATTERY_DRAIN,
                FaultType.FUEL_LEAK
        );

        Telemetry result = injector.applyToTelemetry(original, faults);

        assertTrue(result.getEngineTempC() > original.getEngineTempC());
        assertTrue(result.getBatteryVoltage() < original.getBatteryVoltage());
        assertTrue(result.getFuelLevelPct() < original.getFuelLevelPct());
    }

    @Test
    @DisplayName("Other faults should not affect position")
    void shouldNotAffectPositionWithTelemetryFaults() {
        Position original = createPosition(-23.5505, -46.6333, 5.0);
        Set<FaultType> faults = EnumSet.of(FaultType.OVERHEATING, FaultType.BATTERY_DRAIN);

        Position result = injector.applyToPosition(original, faults);

        assertEquals(original.getLatitude(), result.getLatitude());
        assertEquals(original.getLongitude(), result.getLongitude());
        assertEquals(original.getAccuracy(), result.getAccuracy());
    }

    private Position createPosition(double lat, double lon, double accuracy) {
        return new Position.Builder()
                .timestamp(Instant.now())
                .latitude(lat)
                .longitude(lon)
                .speedKmh(60.0)
                .heading(90.0)
                .accuracy(accuracy)
                .build();
    }

    private Telemetry createTelemetry(int rpm, double temp, double fuel, double voltage) {
        return new Telemetry.Builder()
                .timestamp(Instant.now())
                .engineRpm(rpm)
                .fuelLevelPct(fuel)
                .engineTempC(temp)
                .odometerKm(50000.0)
                .batteryVoltage(voltage)
                .build();
    }

}
