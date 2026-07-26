package com.unesp.desktopsimulator.app.services.simulator;

import com.unesp.desktopsimulator.app.services.generators.FaultInjector;
import com.unesp.desktopsimulator.app.services.generators.MessageGenerator;
import com.unesp.desktopsimulator.app.services.generators.PositionGenerator;
import com.unesp.desktopsimulator.app.services.generators.TelemetryGenerator;
import com.unesp.desktopsimulator.app.services.mqtt.MqttPublisher;
import com.unesp.desktopsimulator.model.Driver;
import com.unesp.desktopsimulator.model.FaultType;
import com.unesp.desktopsimulator.model.Vehicle;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SimulatorRegistry {

    private final Map<UUID, VehicleSimulator> simulators = new ConcurrentHashMap<>();
    private final Map<UUID, Thread> threads = new ConcurrentHashMap<>();

    private final long intervalMs;
    private final PositionGenerator positionGenerator;
    private final TelemetryGenerator telemetryGenerator;
    private final MessageGenerator messageGenerator;
    private final FaultInjector faultInjector;
    private final MqttPublisher mqttPublisher;

    public SimulatorRegistry(
            @Value("${simulation.interval-ms:1000}") long intervalMs,
            PositionGenerator positionGenerator,
            TelemetryGenerator telemetryGenerator,
            MessageGenerator messageGenerator,
            FaultInjector faultInjector,
            MqttPublisher mqttPublisher) {
        this.intervalMs = intervalMs;
        this.positionGenerator = positionGenerator;
        this.telemetryGenerator = telemetryGenerator;
        this.messageGenerator = messageGenerator;
        this.faultInjector = faultInjector;
        this.mqttPublisher = mqttPublisher;
    }

    public SimulationState start(Vehicle vehicle, Driver driver, UUID tripId) {
        SimulationState state = new SimulationState(vehicle, driver);
        state.setTripId(tripId);

        VehicleSimulator simulator = new VehicleSimulator(
                state, positionGenerator, telemetryGenerator,
                messageGenerator, faultInjector, mqttPublisher, intervalMs
        );

        Thread thread = new Thread(simulator, "sim-" + vehicle.getNickname());
        thread.setDaemon(true);

        simulators.put(vehicle.getId(), simulator);
        threads.put(vehicle.getId(), thread);

        thread.start();
        return state;
    }

    public void stop(UUID vehicleId) {
        Optional.ofNullable(simulators.remove(vehicleId))
                .ifPresent(VehicleSimulator::stop);
        Optional.ofNullable(threads.remove(vehicleId))
                .ifPresent(Thread::interrupt);
    }

    public void stopAll() {
        new ArrayList<>(simulators.keySet()).forEach(this::stop);
    }

    public boolean isRunning(UUID vehicleId) {
        SimulationState state = getState(vehicleId);
        return state != null && state.isRunning();
    }

    public SimulationState getState(UUID vehicleId) {
        VehicleSimulator simulator = simulators.get(vehicleId);
        return simulator != null ? simulator.getState() : null;
    }

    public List<SimulationState> getAllStates() {
        return simulators.values().stream()
                .map(VehicleSimulator::getState)
                .toList();
    }

    public int getActiveCount() {
        return (int) simulators.values().stream()
                .filter(s -> s.getState().isRunning())
                .count();
    }

    public void addFault(UUID vehicleId, FaultType fault) {
        Optional.ofNullable(getState(vehicleId))
                .ifPresent(state -> state.addFault(fault));
    }

    public void removeFault(UUID vehicleId, FaultType fault) {
        Optional.ofNullable(getState(vehicleId))
                .ifPresent(state -> state.removeFault(fault));
    }

}
