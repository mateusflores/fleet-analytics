package com.unesp.desktopsimulator.app.services.simulator;

import com.unesp.desktopsimulator.model.Driver;
import com.unesp.desktopsimulator.model.FaultType;
import com.unesp.desktopsimulator.model.Vehicle;
import com.unesp.desktopsimulator.model.telemetry.Position;
import com.unesp.desktopsimulator.model.telemetry.Telemetry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SimulationStateTest {

    @Test
    @DisplayName("Should initialize with vehicle and driver")
    void shouldInitializeWithVehicleAndDriver() {
        Vehicle vehicle = createVehicle();
        Driver driver = createDriver();

        SimulationState state = new SimulationState(vehicle, driver);

        assertSame(vehicle, state.getVehicle());
        assertSame(driver, state.getDriver());
        assertEquals(vehicle.getId(), state.getVehicleId());
    }

    @Test
    @DisplayName("Should initialize with null driver")
    void shouldInitializeWithNullDriver() {
        Vehicle vehicle = createVehicle();

        SimulationState state = new SimulationState(vehicle, null);

        assertSame(vehicle, state.getVehicle());
        assertNull(state.getDriver());
    }

    @Test
    @DisplayName("Should set and get tripId")
    void shouldSetAndGetTripId() {
        SimulationState state = new SimulationState(createVehicle(), null);
        UUID tripId = UUID.randomUUID();

        state.setTripId(tripId);

        assertEquals(tripId, state.getTripId());
    }

    @Test
    @DisplayName("Should set and get running state")
    void shouldSetAndGetRunningState() {
        SimulationState state = new SimulationState(createVehicle(), null);

        assertFalse(state.isRunning());

        state.setRunning(true);
        assertTrue(state.isRunning());

        state.setRunning(false);
        assertFalse(state.isRunning());
    }

    @Test
    @DisplayName("Should add, check and remove faults")
    void shouldManageFaults() {
        SimulationState state = new SimulationState(createVehicle(), null);

        assertFalse(state.hasFaults());

        state.addFault(FaultType.OVERHEATING);
        assertTrue(state.hasFaults());
        assertTrue(state.hasFault(FaultType.OVERHEATING));
        assertFalse(state.hasFault(FaultType.FUEL_LEAK));

        state.addFault(FaultType.FUEL_LEAK);
        assertEquals(2, state.getActiveFaults().size());

        state.removeFault(FaultType.OVERHEATING);
        assertFalse(state.hasFault(FaultType.OVERHEATING));
        assertTrue(state.hasFault(FaultType.FUEL_LEAK));

        state.clearFaults();
        assertFalse(state.hasFaults());
    }

    @Test
    @DisplayName("Should set and get last position")
    void shouldSetAndGetLastPosition() {
        SimulationState state = new SimulationState(createVehicle(), null);
        Position pos = createPosition();

        assertNull(state.getLastPosition());

        state.setLastPosition(pos);
        assertSame(pos, state.getLastPosition());
    }

    @Test
    @DisplayName("Should set and get last telemetry")
    void shouldSetAndGetLastTelemetry() {
        SimulationState state = new SimulationState(createVehicle(), null);
        Telemetry tel = createTelemetry();

        assertNull(state.getLastTelemetry());

        state.setLastTelemetry(tel);
        assertSame(tel, state.getLastTelemetry());
    }

    @Test
    @DisplayName("Should set and get timestamps")
    void shouldSetAndGetTimestamps() {
        SimulationState state = new SimulationState(createVehicle(), null);
        Instant started = Instant.now();
        Instant lastTick = started.plusSeconds(10);

        state.setStartedAt(started);
        state.setLastTickAt(lastTick);

        assertEquals(started, state.getStartedAt());
        assertEquals(lastTick, state.getLastTickAt());
    }

    private Vehicle createVehicle() {
        return Vehicle.Builder.builder()
                .withId(UUID.randomUUID())
                .withNickname("Test Truck")
                .withLicensePlate("ABC-1234")
                .withBrand("Volvo")
                .withModel("FH 540")
                .withYear(2024)
                .withColor("White")
                .withVehicleType("Truck")
                .build();
    }

    private Driver createDriver() {
        return new Driver.Builder(UUID.randomUUID(), "John", "Doe").build();
    }

    private Position createPosition() {
        return new Position.Builder()
                .timestamp(Instant.now())
                .latitude(-23.5505)
                .longitude(-46.6333)
                .speedKmh(60.0)
                .heading(90.0)
                .accuracy(5.0)
                .build();
    }

    private Telemetry createTelemetry() {
        return new Telemetry.Builder()
                .timestamp(Instant.now())
                .engineRpm(1500)
                .fuelLevelPct(80.0)
                .engineTempC(95.0)
                .odometerKm(50000.0)
                .batteryVoltage(13.8)
                .build();
    }

}
