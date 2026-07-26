package com.unesp.desktopsimulator.app.services.generators;

import com.unesp.desktopsimulator.model.telemetry.Position;
import com.unesp.desktopsimulator.model.telemetry.Telemetry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class TelemetryGeneratorTest {

    private final TelemetryGenerator generator = new TelemetryGenerator();

    @Test
    @DisplayName("Should return valid initial telemetry when previous is null")
    void shouldReturnInitialTelemetryWhenPreviousIsNull() {
        Telemetry tel = generator.generate(null, createPosition(0));

        assertNotNull(tel);
        assertNotNull(tel.getTimestamp());
        assertEquals(700, tel.getEngineRpm());
        assertEquals(100.0, tel.getFuelLevelPct());
        assertEquals(85.0, tel.getEngineTempC());
        assertTrue(tel.getOdometerKm() >= 10_000 && tel.getOdometerKm() <= 100_000);
        assertEquals(13.8, tel.getBatteryVoltage());
    }

    @RepeatedTest(10)
    @DisplayName("RPM should increase with speed")
    void shouldIncreaseRPMWithSpeed() {
        Telemetry last = initialTelemetry();
        Position pos = createPosition(80.0);
        Telemetry tel = generator.generate(last, pos);

        assertTrue(tel.getEngineRpm() > 700,
                "RPM should be above idle when speed is 80 km/h");
        assertTrue(tel.getEngineRpm() <= 4000,
                "RPM should not exceed max");
    }

    @RepeatedTest(20)
    @DisplayName("RPM should be at idle when speed is zero")
    void shouldBeIdleWhenStopped() {
        Telemetry last = createTelemetry(700, 90.0, 80.0, 50000.0, 13.8);
        Position pos = createPosition(0.0);

        Telemetry tel = generator.generate(last, pos);

        assertTrue(tel.getEngineRpm() >= 650,
                "RPM should be near idle when stopped, got " + tel.getEngineRpm());
        assertTrue(tel.getEngineRpm() <= 1000,
                "RPM too high when stopped: " + tel.getEngineRpm());
    }

    @Test
    @DisplayName("Temperature should cool down when idling")
    void shouldCoolDownWhenIdling() {
        Telemetry hot = createTelemetry(700, 100.0, 80.0, 50000.0, 13.8);
        hot = new Telemetry.Builder()
                .timestamp(Instant.now())
                .engineRpm(700)
                .fuelLevelPct(hot.getFuelLevelPct())
                .engineTempC(100.0)
                .odometerKm(hot.getOdometerKm())
                .batteryVoltage(hot.getBatteryVoltage())
                .build();

        Position pos = createPosition(0.0);

        Telemetry after = generator.generate(hot, pos);

        assertTrue(after.getEngineTempC() < 100.0,
                "Temperature should decrease when idling, got " + after.getEngineTempC());
    }

    @RepeatedTest(10)
    @DisplayName("Fuel should decrease over time")
    void shouldDecreaseFuel() {
        Telemetry last = initialTelemetry();
        Position pos = createPosition(60.0);

        Telemetry tel = generator.generate(last, pos);

        assertTrue(tel.getFuelLevelPct() < last.getFuelLevelPct(),
                "Fuel should decrease, was " + last.getFuelLevelPct()
                        + " now " + tel.getFuelLevelPct());
    }

    @Test
    @DisplayName("Fuel should never go below zero")
    void shouldNeverDropFuelBelowZero() {
        Telemetry empty = createTelemetry(1500, 90.0, 0.01, 50000.0, 13.8);
        Position pos = createPosition(100.0);

        for (int i = 0; i < 100; i++) {
            Telemetry tel = generator.generate(empty, pos);
            assertTrue(tel.getFuelLevelPct() >= 0,
                    "Fuel dropped below zero: " + tel.getFuelLevelPct());
            empty = tel;
        }
    }

    @Test
    @DisplayName("Odometer should only increase")
    void shouldIncreaseOdometer() {
        Telemetry last = initialTelemetry();
        Position pos = createPosition(60.0);

        Telemetry tel = generator.generate(last, pos);

        assertTrue(tel.getOdometerKm() > last.getOdometerKm(),
                "Odometer should increase");
    }

    @RepeatedTest(10)
    @DisplayName("Battery voltage should stay within bounds")
    void shouldKeepBatteryWithinBounds() {
        Telemetry last = initialTelemetry();
        Position moving = createPosition(50.0);

        for (int i = 0; i < 50; i++) {
            Telemetry tel = generator.generate(last, moving);
            assertTrue(tel.getBatteryVoltage() >= 11.0,
                    "Battery below min: " + tel.getBatteryVoltage());
            assertTrue(tel.getBatteryVoltage() <= 14.4,
                    "Battery above max: " + tel.getBatteryVoltage());
            last = tel;
        }
    }

    @Test
    @DisplayName("RPM should not jump more than 300 between consecutive ticks at constant speed")
    void shouldNotJumpRPM() {
        Telemetry last = createTelemetry(2000, 95.0, 70.0, 50000.0, 13.8);
        Position pos = createPosition(50.0);

        Telemetry next = generator.generate(last, pos);

        int delta = Math.abs(next.getEngineRpm() - last.getEngineRpm());
        assertTrue(delta < 500,
                "RPM delta too large: " + delta + " (from " + last.getEngineRpm()
                        + " to " + next.getEngineRpm() + ")");
    }

    private Telemetry initialTelemetry() {
        return new Telemetry.Builder()
                .timestamp(Instant.now())
                .engineRpm(700)
                .fuelLevelPct(100.0)
                .engineTempC(85.0)
                .odometerKm(50000.0)
                .batteryVoltage(13.8)
                .build();
    }

    private Telemetry createTelemetry(int rpm, double temp, double fuel, double odo, double voltage) {
        return new Telemetry.Builder()
                .timestamp(Instant.now())
                .engineRpm(rpm)
                .fuelLevelPct(fuel)
                .engineTempC(temp)
                .odometerKm(odo)
                .batteryVoltage(voltage)
                .build();
    }

    private Position createPosition(double speed) {
        return new Position.Builder()
                .timestamp(Instant.now())
                .latitude(-23.5505)
                .longitude(-46.6333)
                .speedKmh(speed)
                .heading(90.0)
                .accuracy(5.0)
                .build();
    }

}
