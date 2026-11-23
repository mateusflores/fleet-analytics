package com.unesp.desktopsimulator.app.services.simulator;

import com.unesp.desktopsimulator.app.services.simulation.SimulationOptions;
import com.unesp.desktopsimulator.app.services.simulation.VehicleModel;
import com.unesp.desktopsimulator.app.services.simulation.VehicleProfile;
import com.unesp.desktopsimulator.app.services.simulation.route.Route;
import com.unesp.desktopsimulator.app.services.simulation.route.RouteCatalog;
import com.unesp.desktopsimulator.model.FaultSeverity;
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
    private final RouteCatalog routes;
    private final MqttPublisher mqttPublisher;

    public SimulatorRegistry(
            @Value("${simulation.interval-ms:1000}") long intervalMs,
            RouteCatalog routes,
            MqttPublisher mqttPublisher) {
        this.intervalMs = intervalMs;
        this.routes = routes;
        this.mqttPublisher = mqttPublisher;
    }

    public boolean isBrokerConnected() {
        return mqttPublisher.isConnected();
    }

    public RouteCatalog getRoutes() {
        return routes;
    }

    /** Inicia com a rota padrão do veículo, condução econômica e motor aquecido. */
    public SimulationState start(Vehicle vehicle, Driver driver, UUID tripId) {
        long seed = vehicle.getId().getMostSignificantBits() ^ System.nanoTime();
        return start(vehicle, driver, tripId, routes.defaultFor(vehicle.getId()), SimulationOptions.defaults(seed));
    }

    public SimulationState start(Vehicle vehicle, Driver driver, UUID tripId, Route route, SimulationOptions options) {
        SimulationState state = new SimulationState(vehicle, driver);
        state.setTripId(tripId);
        state.setOptions(options);
        state.setRouteName(route.name());

        VehicleProfile profile = VehicleProfile.forVehicleType(vehicle.getVehicleType());
        state.setProfile(profile);
        VehicleModel model = new VehicleModel(profile, route, options, state.getFaults());
        VehicleSimulator simulator = new VehicleSimulator(state, model, mqttPublisher, intervalMs);

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
        addFault(vehicleId, fault, FaultSeverity.MODERATE);
    }

    public void addFault(UUID vehicleId, FaultType fault, FaultSeverity severity) {
        Optional.ofNullable(getState(vehicleId))
                .ifPresent(state -> state.addFault(fault, severity));
    }

    public void removeFault(UUID vehicleId, FaultType fault) {
        Optional.ofNullable(getState(vehicleId))
                .ifPresent(state -> state.removeFault(fault));
    }

}
