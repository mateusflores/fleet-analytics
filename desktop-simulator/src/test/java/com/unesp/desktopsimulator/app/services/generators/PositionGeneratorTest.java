package com.unesp.desktopsimulator.app.services.generators;

import com.unesp.desktopsimulator.model.telemetry.Position;
import com.unesp.desktopsimulator.model.telemetry.Telemetry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class PositionGeneratorTest {

    private final PositionGenerator generator = new PositionGenerator();

    @Test
    @DisplayName("Should return valid initial position when previous is null")
    void shouldReturnInitialPositionWhenPreviousIsNull() {
        Position pos = generator.generate(null, null);

        assertNotNull(pos);
        assertNotNull(pos.getTimestamp());
        assertNotNull(pos.getLatitude());
        assertNotNull(pos.getLongitude());
        assertEquals(0.0, pos.getSpeedKmh());
        assertTrue(pos.getHeading() >= 0 && pos.getHeading() < 360);
        assertTrue(pos.getAccuracy() >= 1.0);
    }

    @Test
    @DisplayName("Should not move when speed stays zero")
    void shouldNotMoveWhenSpeedStaysZero() {
        for (int i = 0; i < 30; i++) {
            Position last = createPosition(0.0, 90.0, -23.5505, -46.6333);
            Telemetry tel = createTelemetry();
            Position pos = generator.generate(last, tel);

            if (pos.getSpeedKmh() > 0.5) {
                continue;
            }

            assertEquals(last.getLatitude(), pos.getLatitude(), 0.000001,
                    "Latitude changed when speed=0");
            assertEquals(last.getLongitude(), pos.getLongitude(), 0.000001,
                    "Longitude changed when speed=0");
        }
    }

    @RepeatedTest(20)
    @DisplayName("Speed should never exceed maximum")
    void shouldNeverExceedMaxSpeed() {
        Position last = createPosition(110.0, 90.0, -23.5505, -46.6333);
        Telemetry tel = createTelemetry();

        Position pos = generator.generate(last, tel);

        assertTrue(pos.getSpeedKmh() <= 120.0, "Speed " + pos.getSpeedKmh() + " exceeds 120");
        assertTrue(pos.getSpeedKmh() >= 0);
    }

    @Test
    @DisplayName("Heading should not change when speed stays zero")
    void shouldNotChangeHeadingWhenStopped() {
        int headingChangesWhenStopped = 0;
        int total = 50;

        for (int i = 0; i < total; i++) {
            Position last = createPosition(0.0, 180.0, -23.5505, -46.6333);
            Telemetry tel = createTelemetry();
            Position pos = generator.generate(last, tel);

            if (pos.getSpeedKmh() > 0.5) {
                continue;
            }

            if (Math.abs(pos.getHeading() - 180.0) > 0.01) {
                headingChangesWhenStopped++;
            }
        }

        assertEquals(0, headingChangesWhenStopped,
                "Heading should never change when speed=0, changed "
                        + headingChangesWhenStopped + " times");
    }

    @RepeatedTest(10)
    @DisplayName("Position should change when moving")
    void shouldMovePositionWhenSpeedPositive() {
        Position last = createPosition(60.0, 0.0, -23.5505, -46.6333);
        Telemetry tel = createTelemetry();

        Position pos = generator.generate(last, tel);

        assertNotEquals(last.getLatitude(), pos.getLatitude(), 0.00001,
                "Latitude did not change");
    }

    @Test
    @DisplayName("Accuracy should always be positive")
    void shouldHavePositiveAccuracy() {
        for (int i = 0; i < 100; i++) {
            Position last = createPosition(30.0, 45.0, -23.5505, -46.6333);
            Telemetry tel = createTelemetry();
            Position pos = generator.generate(last, tel);
            assertTrue(pos.getAccuracy() > 0, "Accuracy was " + pos.getAccuracy());
        }
    }

    @RepeatedTest(10)
    @DisplayName("Heading should stay in [0, 360) after many iterations")
    void shouldKeepHeadingNormalized() {
        Position last = createPosition(50.0, 0.0, -23.5505, -46.6333);
        Position pos = last;
        Telemetry tel = createTelemetry();

        for (int i = 0; i < 100; i++) {
            pos = generator.generate(pos, tel);
            assertTrue(pos.getHeading() >= 0 && pos.getHeading() < 360,
                    "Heading out of bounds: " + pos.getHeading());
        }
    }

    @Test
    @DisplayName("Initial positions should vary between calls")
    void shouldVaryInitialPositions() {
        Position p1 = generator.generate(null, null);
        Position p2 = generator.generate(null, null);
        assertNotEquals(p1.getLatitude(), p2.getLatitude(), 0.0001,
                "Two initial positions should differ");
    }

    private Position createPosition(double speed, double heading, double lat, double lon) {
        return new Position.Builder()
                .timestamp(Instant.now())
                .latitude(lat)
                .longitude(lon)
                .speedKmh(speed)
                .heading(heading)
                .accuracy(5.0)
                .build();
    }

    private Telemetry createTelemetry() {
        return new Telemetry.Builder()
                .timestamp(Instant.now())
                .engineRpm(1500)
                .fuelLevelPct(80.0)
                .engineTempC(90.0)
                .odometerKm(50000.0)
                .batteryVoltage(13.8)
                .build();
    }

}
