package com.unesp.desktopsimulator.app.services.simulator;

import com.unesp.desktopsimulator.app.services.mqtt.MqttPublisher;
import com.unesp.desktopsimulator.app.services.simulation.AlertMonitor;
import com.unesp.desktopsimulator.app.services.simulation.SnapshotMapper;
import com.unesp.desktopsimulator.app.services.simulation.VehicleModel;
import com.unesp.desktopsimulator.app.services.simulation.VehicleSnapshot;
import com.unesp.desktopsimulator.model.telemetry.Message;
import com.unesp.desktopsimulator.model.telemetry.Position;
import com.unesp.desktopsimulator.model.telemetry.RawData;
import com.unesp.desktopsimulator.model.telemetry.Telemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;

/** Laço de tempo real de um veículo: avança o modelo pelo tempo decorrido e publica posição, telemetria e alertas. */
public class VehicleSimulator implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(VehicleSimulator.class);
    /** Limite do passo após uma pausa longa (ex.: computador suspenso), para o modelo não "saltar" quilômetros. */
    private static final double MAX_STEP_S = 5.0;

    private final SimulationState state;
    private final VehicleModel model;
    private final AlertMonitor alerts;
    private final MqttPublisher mqttPublisher;
    private final long intervalMs;
    private volatile boolean running;

    public VehicleSimulator(SimulationState state, VehicleModel model, MqttPublisher mqttPublisher, long intervalMs) {
        this.state = state;
        this.model = model;
        this.alerts = new AlertMonitor(model.profile());
        this.mqttPublisher = mqttPublisher;
        this.intervalMs = intervalMs;
    }

    @Override
    public void run() {
        state.setRunning(true);
        state.setStartedAt(Instant.now());
        running = true;
        log.info("Simulation started for vehicle {} on route {}", state.getVehicle().getNickname(), model.route().id());

        Instant last = Instant.now();
        while (running) {
            try {
                Instant now = Instant.now();
                double dt = Math.min(MAX_STEP_S, Duration.between(last, now).toNanos() / 1e9);
                last = now;
                tick(now, dt);
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
            } catch (Exception e) {
                log.error("Error in simulation tick for vehicle {}: {}", state.getVehicleId(), e.getMessage(), e);
            }
        }

        state.setRunning(false);
        log.info("Simulation stopped for vehicle {}", state.getVehicle().getNickname());
    }

    void tick(Instant now, double dtS) {
        VehicleSnapshot snap = model.step(dtS);
        Position pos = SnapshotMapper.toPosition(snap, now);
        Telemetry tel = SnapshotMapper.toTelemetry(snap, now);
        assignIds(pos);
        assignIds(tel);

        state.setLastSnapshot(snap);
        state.setLastPosition(pos);
        state.setLastTelemetry(tel);
        state.setLastTickAt(now);

        mqttPublisher.publish(pos);
        mqttPublisher.publish(tel);
        for (Message msg : alerts.check(snap, now)) {
            assignIds(msg);
            mqttPublisher.publish(msg);
        }
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
