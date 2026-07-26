package com.unesp.desktopsimulator.app.services.generators;

import com.unesp.desktopsimulator.model.telemetry.Telemetry;
import org.springframework.stereotype.Service;

@Service
public class TelemetryGenerator {

    public Telemetry generate(Telemetry previousData) {
        return null;
    }

    public Double calcEngineRpm() {
        return 1000D;
    }

    public Double calcFuelLevelPct() {
        return 100D;
    }

    public Double calcEngineTempC() {
        return 90D;
    }

    public Double calcOdometer() {
        return 0D;
    }

    public Double calcBatteryVoltage() {
        return 0D;
    }

}
