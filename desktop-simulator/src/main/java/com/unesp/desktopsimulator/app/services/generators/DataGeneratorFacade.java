package com.unesp.desktopsimulator.app.services.generators;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DataGeneratorFacade {

    private final MessageGenerator messageGenerator;
    private final PositionGenerator positionGenerator;
    private final TelemetryGenerator telemetryGenerator;

    @Autowired
    public DataGeneratorFacade(MessageGenerator messageGenerator, PositionGenerator positionGenerator,
                               TelemetryGenerator telemetryGenerator) {
        this.messageGenerator = messageGenerator;
        this.positionGenerator = positionGenerator;
        this.telemetryGenerator = telemetryGenerator;
    }

    public void generate(UUID vehicleId, UUID tripId, UUID driverId) {

    }

}
