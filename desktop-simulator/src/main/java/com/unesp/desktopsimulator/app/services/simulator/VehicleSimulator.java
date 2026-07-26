package com.unesp.desktopsimulator.app.services.simulator;

import com.unesp.desktopsimulator.app.services.generators.FaultInjector;
import com.unesp.desktopsimulator.app.services.generators.MessageGenerator;
import com.unesp.desktopsimulator.app.services.generators.PositionGenerator;
import com.unesp.desktopsimulator.app.services.generators.TelemetryGenerator;
import com.unesp.desktopsimulator.app.services.mqtt.MqttPublisher;
import com.unesp.desktopsimulator.model.telemetry.Message;
import com.unesp.desktopsimulator.model.telemetry.Position;
import com.unesp.desktopsimulator.model.telemetry.RawData;
import com.unesp.desktopsimulator.model.telemetry.Telemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

public class VehicleSimulator implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(VehicleSimulator.class);

    private final SimulationState state;
    private final PositionGenerator positionGenerator;
    private final TelemetryGenerator telemetryGenerator;
    private final MessageGenerator messageGenerator;
    private final FaultInjector faultInjector;
    private final MqttPublisher mqttPublisher;
    private final long intervalMs;
    private volatile boolean running;

    public VehicleSimulator(SimulationState state,
                            PositionGenerator positionGenerator,
                            TelemetryGenerator telemetryGenerator,
                            MessageGenerator messageGenerator,
                            FaultInjector faultInjector,
                            MqttPublisher mqttPublisher,
                            long intervalMs) {
        this.state = state;
        this.positionGenerator = positionGenerator;
        this.telemetryGenerator = telemetryGenerator;
        this.messageGenerator = messageGenerator;
        this.faultInjector = faultInjector;
        this.mqttPublisher = mqttPublisher;
        this.intervalMs = intervalMs;
    }

    @Override
    public void run() {
        state.setRunning(true);
        state.setStartedAt(Instant.now());
        running = true;
        log.info("Simulation started for vehicle {}", state.getVehicle().getNickname());

        while (running) {
            try {
                tick();
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
            } catch (Exception e) {
                log.error("Error in simulation tick for vehicle {}: {}",
                        state.getVehicleId(), e.getMessage(), e);
            }
        }

        state.setRunning(false);
        log.info("Simulation stopped for vehicle {}", state.getVehicle().getNickname());
    }

    private void tick() {
        Instant now = Instant.now();

        Position pos = positionGenerator.generate(
                state.getLastPosition(), state.getLastTelemetry());
        assignIds(pos);

        Telemetry prevTel = state.getLastTelemetry();
        Telemetry tel = telemetryGenerator.generate(prevTel, pos);
        assignIds(tel);

        if (state.hasFaults()) {
            pos = faultInjector.applyToPosition(pos, state.getActiveFaults());
            tel = faultInjector.applyToTelemetry(tel, state.getActiveFaults());
        }

        Message msg = messageGenerator.generate(prevTel, tel, pos);
        if (msg != null) {
            assignIds(msg);
            mqttPublisher.publish(msg);
        }

        state.setLastPosition(pos);
        state.setLastTelemetry(tel);
        state.setLastTickAt(now);

        mqttPublisher.publish(pos);
        mqttPublisher.publish(tel);
    }

    private void assignIds(RawData data) {
        data.setVehicleId(state.getVehicleId());
        data.setTripId(state.getTripId());
        if (state.getDriver() != null) {
            data.setDriverId(state.getDriver().getId());
        }
    }

    public void stop() {
        running = false;
    }

    public SimulationState getState() {
        return state;
    }

}
