package com.unesp.desktopsimulator.view;

import com.unesp.desktopsimulator.app.services.simulator.SimulationState;
import com.unesp.desktopsimulator.model.Driver;
import com.unesp.desktopsimulator.model.FaultType;
import com.unesp.desktopsimulator.model.Vehicle;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class VehicleTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {
            "Plate", "Nickname", "Driver", "Speed km/h",
            "RPM", "Temp °C", "Fuel %", "Battery V", "Faults", "Status"
    };

    private List<Vehicle> vehicles = new ArrayList<>();
    private List<SimulationState> states = new ArrayList<>();
    private Map<UUID, Driver> driverAssignments = Map.of();

    public void update(List<Vehicle> vehicles, List<SimulationState> states,
                       Map<UUID, Driver> driverAssignments) {
        this.vehicles = new ArrayList<>(vehicles);
        this.states = new ArrayList<>(states);
        this.driverAssignments = driverAssignments;
        fireTableDataChanged();
    }

    public Vehicle getVehicleAt(int row) {
        if (row < 0 || row >= vehicles.size()) return null;
        return vehicles.get(row);
    }

    private SimulationState getState(Vehicle vehicle) {
        return states.stream()
                .filter(s -> s.getVehicleId().equals(vehicle.getId()))
                .findFirst()
                .orElse(null);
    }

    private Driver getDriver(Vehicle vehicle) {
        return driverAssignments.get(vehicle.getId());
    }

    @Override
    public int getRowCount() {
        return vehicles.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Object getValueAt(int row, int col) {
        Vehicle vehicle = vehicles.get(row);
        SimulationState state = getState(vehicle);
        Driver driver = getDriver(vehicle);

        return switch (col) {
            case 0 -> vehicle.getLicensePlate();
            case 1 -> vehicle.getNickname() != null ? vehicle.getNickname() : "-";
            case 2 -> driver != null ? driver.getFirstName() + " " + driver.getLastName() : "-";
            case 3 -> state != null && state.getLastPosition() != null
                    ? String.format("%.0f", state.getLastPosition().getSpeedKmh()) : "-";
            case 4 -> state != null && state.getLastTelemetry() != null
                    ? String.valueOf(state.getLastTelemetry().getEngineRpm()) : "-";
            case 5 -> state != null && state.getLastTelemetry() != null
                    ? String.format("%.1f", state.getLastTelemetry().getEngineTempC()) : "-";
            case 6 -> state != null && state.getLastTelemetry() != null
                    ? String.format("%.1f", state.getLastTelemetry().getFuelLevelPct()) : "-";
            case 7 -> state != null && state.getLastTelemetry() != null
                    ? String.format("%.2f", state.getLastTelemetry().getBatteryVoltage()) : "-";
            case 8 -> state != null && state.hasFaults()
                    ? state.getActiveFaults().stream()
                    .map(Enum::name)
                    .collect(Collectors.joining(", ")) : "-";
            case 9 -> state != null && state.isRunning() ? "Running" : "Stopped";
            default -> "";
        };
    }

}
