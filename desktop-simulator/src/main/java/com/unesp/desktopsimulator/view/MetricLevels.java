package com.unesp.desktopsimulator.view;

import com.unesp.desktopsimulator.app.services.simulation.VehicleProfile;
import com.unesp.desktopsimulator.app.services.simulation.VehicleSnapshot;

/**
 * Nível de cada indicador para colorir a interface (0 normal, 1 atenção, 2 crítico, -1 sem nível).
 * Os limites são os mesmos do {@code AlertMonitor}, para que a cor bata com os alertas publicados.
 */
final class MetricLevels {

    private MetricLevels() {
    }

    static int coolant(VehicleSnapshot s) {
        return s.coolantC() >= 110 ? 2 : s.coolantC() >= 105 ? 1 : 0;
    }

    static int oil(VehicleSnapshot s) {
        if (s.engineRpm() < 300) return -1;
        return s.oilKpa() <= 90 ? 2 : s.oilKpa() <= 150 ? 1 : 0;
    }

    static int battery(VehicleSnapshot s, VehicleProfile p) {
        double k = p != null ? p.voltageScale() : 1;
        return s.batteryV() <= 12.2 * k ? 2 : s.batteryV() <= 13.0 * k ? 1 : 0;
    }

    static int fuel(VehicleSnapshot s) {
        return s.fuelLevelPct() <= 8 ? 2 : s.fuelLevelPct() <= 15 ? 1 : 0;
    }

    static int rpm(VehicleSnapshot s, VehicleProfile p) {
        return p != null && s.engineRpm() >= p.maxRpm() - 20 ? 1 : 0;
    }

    /** Pior nível entre os indicadores com alarme — usado na faixa de cor do card. */
    static int worst(VehicleSnapshot s, VehicleProfile p) {
        return Math.max(Math.max(coolant(s), oil(s)), Math.max(battery(s, p), fuel(s)));
    }
}
