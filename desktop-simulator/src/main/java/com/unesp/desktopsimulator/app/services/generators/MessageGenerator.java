package com.unesp.desktopsimulator.app.services.generators;

import com.unesp.desktopsimulator.model.telemetry.Message;
import com.unesp.desktopsimulator.model.telemetry.Position;
import com.unesp.desktopsimulator.model.telemetry.Telemetry;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class MessageGenerator {

    private static final double TEMP_WARNING_C = 200.0;
    private static final double TEMP_CRITICAL_C = 220.0;
    private static final double FUEL_WARNING_PCT = 10.0;
    private static final double FUEL_CRITICAL_PCT = 5.0;
    private static final double MAX_FUEL_PCT = 100.0;
    private static final double BATTERY_WARNING_V = 11.5;
    private static final double NOMINAL_BATTERY_V = 13.8;
    private static final int RPM_WARNING = 3500;

    public Message generate(Telemetry previousTelemetry, Telemetry currentTelemetry,
                            Position currentPosition) {
        if (currentTelemetry == null) {
            return null;
        }

        Instant now = Instant.now();

        Message tempMsg = checkTemperature(previousTelemetry, currentTelemetry, now);
        if (tempMsg != null) return tempMsg;

        Message fuelMsg = checkFuel(previousTelemetry, currentTelemetry, now);
        if (fuelMsg != null) return fuelMsg;

        Message batteryMsg = checkBattery(previousTelemetry, currentTelemetry, now);
        if (batteryMsg != null) return batteryMsg;

        Message rpmMsg = checkRpm(previousTelemetry, currentTelemetry, now);
        if (rpmMsg != null) return rpmMsg;

        return null;
    }

    private Message checkTemperature(Telemetry prev, Telemetry current, Instant now) {
        double temp = current.getEngineTempC();
        double prevTemp = prev != null ? prev.getEngineTempC() : 0;

        if (temp > TEMP_CRITICAL_C && prevTemp <= TEMP_CRITICAL_C) {
            return new Message.Builder()
                    .timestamp(now)
                    .eventType("ENGINE_OVERHEAT")
                    .severity("CRITICAL")
                    .message(String.format("Engine temperature critical at %.1f\u00b0C", temp))
                    .build();
        }
        if (temp > TEMP_WARNING_C && prevTemp <= TEMP_WARNING_C) {
            return new Message.Builder()
                    .timestamp(now)
                    .eventType("ENGINE_OVERHEAT")
                    .severity("WARNING")
                    .message(String.format("Engine overheating at %.1f\u00b0C", temp))
                    .build();
        }
        return null;
    }

    private Message checkFuel(Telemetry prev, Telemetry current, Instant now) {
        double fuel = current.getFuelLevelPct();
        double prevFuel = prev != null ? prev.getFuelLevelPct() : MAX_FUEL_PCT;

        if (fuel < FUEL_CRITICAL_PCT && prevFuel >= FUEL_CRITICAL_PCT) {
            return new Message.Builder()
                    .timestamp(now)
                    .eventType("LOW_FUEL")
                    .severity("CRITICAL")
                    .message(String.format("Fuel level critical at %.1f%%", fuel))
                    .build();
        }
        if (fuel < FUEL_WARNING_PCT && prevFuel >= FUEL_WARNING_PCT) {
            return new Message.Builder()
                    .timestamp(now)
                    .eventType("LOW_FUEL")
                    .severity("WARNING")
                    .message(String.format("Fuel level low at %.1f%%", fuel))
                    .build();
        }
        return null;
    }

    private Message checkBattery(Telemetry prev, Telemetry current, Instant now) {
        double voltage = current.getBatteryVoltage();
        double prevVoltage = prev != null ? prev.getBatteryVoltage() : NOMINAL_BATTERY_V;

        if (voltage < BATTERY_WARNING_V && prevVoltage >= BATTERY_WARNING_V) {
            return new Message.Builder()
                    .timestamp(now)
                    .eventType("LOW_BATTERY")
                    .severity("WARNING")
                    .message(String.format("Battery voltage low at %.1fV", voltage))
                    .build();
        }
        return null;
    }

    private Message checkRpm(Telemetry prev, Telemetry current, Instant now) {
        int rpm = current.getEngineRpm();
        int prevRpm = prev != null ? prev.getEngineRpm() : 0;

        if (rpm > RPM_WARNING && prevRpm <= RPM_WARNING) {
            return new Message.Builder()
                    .timestamp(now)
                    .eventType("RPM_LIMIT")
                    .severity("WARNING")
                    .message(String.format("Engine RPM at %d exceeds limit", rpm))
                    .build();
        }
        return null;
    }

}
