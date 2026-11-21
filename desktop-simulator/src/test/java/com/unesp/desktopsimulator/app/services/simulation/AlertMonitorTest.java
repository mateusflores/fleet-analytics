package com.unesp.desktopsimulator.app.services.simulation;

import com.unesp.desktopsimulator.model.telemetry.Message;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AlertMonitorTest {

    private static final Instant NOW = Instant.parse("2026-11-21T12:00:00Z");

    private static VehicleSnapshot snap(double t, int rpm, double coolant, double oil, double battery, double fuel) {
        return new VehicleSnapshot(t, -23, -47, 0, 4, 60, rpm, 4, 40, 30, 20, coolant, oil, 15, fuel, 60, 45,
                battery, 1000, "Em movimento", 0, 80, 0.5, false);
    }

    private static List<Message> feed(AlertMonitor m, double... coolant) {
        List<Message> out = new ArrayList<>();
        double t = 100;
        for (double c : coolant) out.addAll(m.check(snap(t++, 1500, c, 330, 14.0, 70), NOW));
        return out;
    }

    @Test
    void overheatFiresOnceWhileHoveringAroundTheLimit() {
        AlertMonitor m = new AlertMonitor(VehicleProfile.MEDIUM_RIGID);
        List<Message> events = feed(m, 95, 104, 105, 104, 106, 104, 105, 103);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventType()).isEqualTo("ENGINE_OVERHEAT");
        assertThat(events.get(0).getSeverity()).isEqualTo("WARNING");
    }

    @Test
    void overheatEscalatesAndRearmsAfterCoolingDown() {
        AlertMonitor m = new AlertMonitor(VehicleProfile.MEDIUM_RIGID);
        List<Message> events = feed(m, 105, 110, 108, 99, 105);
        assertThat(events).extracting(Message::getSeverity).containsExactly("WARNING", "CRITICAL", "WARNING");
    }

    @Test
    void batteryThresholdsScaleWith24VoltSystems() {
        AlertMonitor m = new AlertMonitor(VehicleProfile.HEAVY_TRACTOR);
        List<Message> events = new ArrayList<>();
        for (int t = 0; t < 40; t++) events.addAll(m.check(snap(t, 1200, 90, 330, 27.8, 70), NOW));
        assertThat(events).isEmpty();
        events.addAll(m.check(snap(41, 1200, 90, 330, 25.5, 70), NOW));
        assertThat(events).extracting(Message::getEventType).containsExactly("LOW_BATTERY");
    }

    @Test
    void oilAndBatteryAreIgnoredRightAfterEngineStart() {
        AlertMonitor m = new AlertMonitor(VehicleProfile.MEDIUM_RIGID);
        List<Message> events = new ArrayList<>();
        for (int t = 0; t < 10; t++) events.addAll(m.check(snap(t, 700, 30, 60, 12.4, 70), NOW));
        assertThat(events).isEmpty();
    }

    @Test
    void lowFuelWarnsThenCritical() {
        AlertMonitor m = new AlertMonitor(VehicleProfile.MEDIUM_RIGID);
        List<Message> events = new ArrayList<>();
        double t = 0;
        for (double f : new double[]{20, 15, 14.6, 15.2, 8}) events.addAll(m.check(snap(t++, 1500, 90, 330, 14, f), NOW));
        assertThat(events).extracting(Message::getSeverity).containsExactly("WARNING", "CRITICAL");
    }
}
