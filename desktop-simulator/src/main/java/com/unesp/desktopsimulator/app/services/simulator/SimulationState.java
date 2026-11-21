package com.unesp.desktopsimulator.app.services.simulator;

import com.unesp.desktopsimulator.app.services.simulation.FaultSet;
import com.unesp.desktopsimulator.app.services.simulation.SimulationOptions;
import com.unesp.desktopsimulator.app.services.simulation.VehicleSnapshot;
import com.unesp.desktopsimulator.model.Driver;
import com.unesp.desktopsimulator.model.FaultSeverity;
import com.unesp.desktopsimulator.model.FaultType;
import com.unesp.desktopsimulator.model.Vehicle;
import com.unesp.desktopsimulator.model.telemetry.Position;
import com.unesp.desktopsimulator.model.telemetry.Telemetry;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SimulationState {

    private final Vehicle vehicle;
    private final Driver driver;
    private volatile UUID tripId;
    private volatile Position lastPosition;
    private volatile Telemetry lastTelemetry;
    private final FaultSet faults = new FaultSet();
    private volatile SimulationOptions options;
    private volatile String routeName;
    private volatile VehicleSnapshot lastSnapshot;
    private volatile boolean running;
    private volatile Instant startedAt;
    private volatile Instant lastTickAt;

    public SimulationState(Vehicle vehicle, Driver driver) {
        this.vehicle = vehicle;
        this.driver = driver;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public Driver getDriver() {
        return driver;
    }

    public UUID getVehicleId() {
        return vehicle.getId();
    }

    public UUID getTripId() {
        return tripId;
    }

    public void setTripId(UUID tripId) {
        this.tripId = tripId;
    }

    public Position getLastPosition() {
        return lastPosition;
    }

    public void setLastPosition(Position lastPosition) {
        this.lastPosition = lastPosition;
    }

    public Telemetry getLastTelemetry() {
        return lastTelemetry;
    }

    public void setLastTelemetry(Telemetry lastTelemetry) {
        this.lastTelemetry = lastTelemetry;
    }

    public FaultSet getFaults() {
        return faults;
    }

    public Set<FaultType> getActiveFaults() {
        return faults.types();
    }

    public Map<FaultType, FaultSeverity> getFaultSeverities() {
        return faults.severities();
    }

    /** Ativa a falha com intensidade média. */
    public void addFault(FaultType fault) {
        addFault(fault, FaultSeverity.MODERATE);
    }

    public void addFault(FaultType fault, FaultSeverity severity) {
        faults.add(fault, severity);
    }

    public void removeFault(FaultType fault) {
        faults.remove(fault);
    }

    public void clearFaults() {
        faults.clear();
    }

    public boolean hasFaults() {
        return !faults.isEmpty();
    }

    public boolean hasFault(FaultType fault) {
        return faults.contains(fault);
    }

    public SimulationOptions getOptions() {
        return options;
    }

    public void setOptions(SimulationOptions options) {
        this.options = options;
    }

    public String getRouteName() {
        return routeName;
    }

    public void setRouteName(String routeName) {
        this.routeName = routeName;
    }

    public VehicleSnapshot getLastSnapshot() {
        return lastSnapshot;
    }

    public void setLastSnapshot(VehicleSnapshot lastSnapshot) {
        this.lastSnapshot = lastSnapshot;
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getLastTickAt() {
        return lastTickAt;
    }

    public void setLastTickAt(Instant lastTickAt) {
        this.lastTickAt = lastTickAt;
    }

}
