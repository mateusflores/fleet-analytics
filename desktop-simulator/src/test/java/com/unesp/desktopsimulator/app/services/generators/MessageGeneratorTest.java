package com.unesp.desktopsimulator.app.services.generators;

import com.unesp.desktopsimulator.model.telemetry.Message;
import com.unesp.desktopsimulator.model.telemetry.Position;
import com.unesp.desktopsimulator.model.telemetry.Telemetry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class MessageGeneratorTest {

    private final MessageGenerator generator = new MessageGenerator();

    @Test
    @DisplayName("Should return null when current telemetry is null")
    void shouldReturnNullWhenCurrentTelemetryIsNull() {
        Position pos = createPosition();
        Message msg = generator.generate(null, null, pos);
        assertNull(msg);
    }

    @Test
    @DisplayName("Should not generate message when all values are normal")
    void shouldNotGenerateMessageWhenNormal() {
        Telemetry prev = createTelemetry(1500, 95.0, 50.0, 13.8);
        Telemetry current = createTelemetry(1600, 96.0, 49.5, 13.7);
        Position pos = createPosition();

        Message msg = generator.generate(prev, current, pos);

        assertNull(msg);
    }

    @Test
    @DisplayName("Should generate WARNING on crossing 200°C threshold")
    void shouldGenerateWarningOnHighTemperature() {
        Telemetry prev = createTelemetry(2000, 195.0, 50.0, 13.8);
        Telemetry current = createTelemetry(2500, 205.0, 49.5, 13.7);
        Position pos = createPosition();

        Message msg = generator.generate(prev, current, pos);

        assertNotNull(msg);
        assertEquals("ENGINE_OVERHEAT", msg.getEventType());
        assertEquals("WARNING", msg.getSeverity());
        assertTrue(msg.getMessage().contains("205"));
    }

    @Test
    @DisplayName("Should generate CRITICAL on crossing 220°C threshold")
    void shouldGenerateCriticalOnVeryHighTemperature() {
        Telemetry prev = createTelemetry(3000, 210.0, 50.0, 13.8);
        Telemetry current = createTelemetry(3500, 225.0, 49.5, 13.7);
        Position pos = createPosition();

        Message msg = generator.generate(prev, current, pos);

        assertNotNull(msg);
        assertEquals("ENGINE_OVERHEAT", msg.getEventType());
        assertEquals("CRITICAL", msg.getSeverity());
    }

    @Test
    @DisplayName("Should not generate message when already above threshold in previous")
    void shouldNotGenerateDuplicateMessageWhenSustained() {
        Telemetry prev = createTelemetry(2500, 205.0, 50.0, 13.8);
        Telemetry current = createTelemetry(2600, 208.0, 49.5, 13.7);
        Position pos = createPosition();

        Message msg = generator.generate(prev, current, pos);

        assertNull(msg, "Should not generate message when threshold was already crossed");
    }

    @Test
    @DisplayName("Should generate WARNING on crossing low fuel threshold (10%)")
    void shouldGenerateWarningOnLowFuel() {
        Telemetry prev = createTelemetry(1500, 95.0, 12.0, 13.8);
        Telemetry current = createTelemetry(1500, 95.0, 8.0, 13.8);
        Position pos = createPosition();

        Message msg = generator.generate(prev, current, pos);

        assertNotNull(msg);
        assertEquals("LOW_FUEL", msg.getEventType());
        assertEquals("WARNING", msg.getSeverity());
    }

    @Test
    @DisplayName("Should generate CRITICAL on crossing very low fuel threshold (5%)")
    void shouldGenerateCriticalOnVeryLowFuel() {
        Telemetry prev = createTelemetry(1500, 95.0, 6.0, 13.8);
        Telemetry current = createTelemetry(1500, 95.0, 3.0, 13.8);
        Position pos = createPosition();

        Message msg = generator.generate(prev, current, pos);

        assertNotNull(msg);
        assertEquals("LOW_FUEL", msg.getEventType());
        assertEquals("CRITICAL", msg.getSeverity());
    }

    @Test
    @DisplayName("Should generate WARNING on low battery voltage")
    void shouldGenerateWarningOnLowBattery() {
        Telemetry prev = createTelemetry(1500, 95.0, 50.0, 12.0);
        Telemetry current = createTelemetry(1500, 95.0, 49.5, 11.0);
        Position pos = createPosition();

        Message msg = generator.generate(prev, current, pos);

        assertNotNull(msg);
        assertEquals("LOW_BATTERY", msg.getEventType());
        assertEquals("WARNING", msg.getSeverity());
    }

    @Test
    @DisplayName("Should not report low battery when voltage was already low")
    void shouldNotReportSustainedLowBattery() {
        Telemetry prev = createTelemetry(1500, 95.0, 50.0, 11.2);
        Telemetry current = createTelemetry(1500, 95.0, 49.5, 11.1);
        Position pos = createPosition();

        Message msg = generator.generate(prev, current, pos);

        assertNull(msg);
    }

    @Test
    @DisplayName("Should generate WARNING on high RPM")
    void shouldGenerateWarningOnHighRpm() {
        Telemetry prev = createTelemetry(3400, 95.0, 50.0, 13.8);
        Telemetry current = createTelemetry(3600, 95.0, 49.5, 13.7);
        Position pos = createPosition();

        Message msg = generator.generate(prev, current, pos);

        assertNotNull(msg);
        assertEquals("RPM_LIMIT", msg.getEventType());
        assertEquals("WARNING", msg.getSeverity());
    }

    @Test
    @DisplayName("Should generate CRITICAL overheat even when warning was already triggered")
    void shouldGenerateCriticalEvenAfterWarning() {
        Telemetry prev = createTelemetry(2500, 210.0, 50.0, 13.8);
        Telemetry current = createTelemetry(3000, 225.0, 49.5, 13.7);
        Position pos = createPosition();

        Message msg = generator.generate(prev, current, pos);

        assertNotNull(msg);
        assertEquals("CRITICAL", msg.getSeverity(),
                "Should escalate to CRITICAL when crossing 220 even if already above 200");
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

    private Position createPosition() {
        return new Position.Builder()
                .timestamp(Instant.now())
                .latitude(-23.5505)
                .longitude(-46.6333)
                .speedKmh(60.0)
                .heading(90.0)
                .accuracy(5.0)
                .build();
    }

}
