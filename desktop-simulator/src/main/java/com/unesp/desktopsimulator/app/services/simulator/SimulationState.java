package com.unesp.desktopsimulator.app.services.simulator;

import com.unesp.desktopsimulator.model.Driver;
import com.unesp.desktopsimulator.model.FaultType;
import com.unesp.desktopsimulator.model.Vehicle;
import com.unesp.desktopsimulator.model.telemetry.Position;
import com.unesp.desktopsimulator.model.telemetry.Telemetry;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

public class SimulationState {

    private final Vehicle vehicle;
    private final Driver driver;
    private volatile UUID tripId;
    private volatile Position lastPosition;
    private volatile Telemetry lastTelemetry;
    private final Set<FaultType> activeFaults = new CopyOnWriteArraySet<>();
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

    public Set<FaultType> getActiveFaults() {
        return activeFaults;
    }

    public void addFault(FaultType fault) {
        activeFaults.add(fault);
    }

    public void removeFault(FaultType fault) {
        activeFaults.remove(fault);
    }

    public void clearFaults() {
        activeFaults.clear();
    }

    public boolean hasFaults() {
        return !activeFaults.isEmpty();
    }

    public boolean hasFault(FaultType fault) {
        return activeFaults.contains(fault);
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
