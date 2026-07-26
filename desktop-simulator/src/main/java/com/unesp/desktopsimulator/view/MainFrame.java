package com.unesp.desktopsimulator.view;

import com.unesp.desktopsimulator.app.services.client.VehicleApiClient;
import com.unesp.desktopsimulator.app.services.simulator.SimulationState;
import com.unesp.desktopsimulator.app.services.simulator.SimulatorRegistry;
import com.unesp.desktopsimulator.model.Driver;
import com.unesp.desktopsimulator.model.FaultType;
import com.unesp.desktopsimulator.model.Vehicle;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MainFrame extends JFrame {

    private static final Color COLOR_STATUS = new Color(0x1A1A2E);
    private static final Color COLOR_RUNNING = new Color(0x27AE60);
    private static final Color COLOR_STOPPED = new Color(0x7F8C8D);

    private final SimulatorRegistry registry;
    private final VehicleApiClient apiClient;
    private List<Vehicle> vehicles;
    private List<Driver> drivers;

    private final VehicleTableModel tableModel;
    private final JTable table;

    private final JButton refreshButton;
    private final JButton startButton;
    private final JButton stopButton;
    private final JButton faultsButton;

    private final JLabel statusLabel;
    private final Timer refreshTimer;

    private final Map<UUID, Driver> driverAssignments = new HashMap<>();
    private final Map<UUID, UUID> tripIds = new HashMap<>();

    public MainFrame(SimulatorRegistry registry, VehicleApiClient apiClient,
                     List<Vehicle> vehicles, List<Driver> drivers) {
        this.registry = registry;
        this.apiClient = apiClient;
        this.vehicles = vehicles;
        this.drivers = drivers;

        setTitle("Fleet Analytics - Desktop Simulator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1120, 620);
        setLayout(new BorderLayout());

        tableModel = new VehicleTableModel();
        table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.setRowHeight(28);
        table.setShowGrid(true);
        table.setGridColor(new Color(0x3E3E3E));

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) updateButtonStates();
        });

        DefaultTableCellRenderer headerRenderer = (DefaultTableCellRenderer) table.getTableHeader().getDefaultRenderer();
        headerRenderer.setHorizontalAlignment(SwingConstants.CENTER);

        setColumnWidths();

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, BorderLayout.CENTER);

        refreshButton = createToolbarButton("Refresh", '\u21BB');
        startButton = createToolbarButton("Start", '\u25B6');
        stopButton = createToolbarButton("Stop", '\u25A0');
        faultsButton = createToolbarButton("Inject Fault", '\u26A0');

        refreshButton.addActionListener(e -> handleRefresh());
        startButton.addActionListener(e -> handleStartSelected());
        stopButton.addActionListener(e -> handleStopSelected());
        faultsButton.addActionListener(e -> handleInjectFault());

        stopButton.setEnabled(false);
        faultsButton.setEnabled(false);

        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        toolbar.add(refreshButton);
        toolbar.addSeparator();
        toolbar.add(startButton);
        toolbar.add(stopButton);
        toolbar.add(faultsButton);
        add(toolbar, BorderLayout.NORTH);

        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        statusBar.setBackground(COLOR_STATUS);
        statusLabel = new JLabel();
        statusLabel.setForeground(Color.WHITE);
        statusBar.add(statusLabel, BorderLayout.WEST);
        add(statusBar, BorderLayout.SOUTH);

        refreshTimer = new Timer(1000, e -> updateTable());
        refreshTimer.start();

        updateStatus();
        updateTable();
        setLocationRelativeTo(null);
    }

    private JButton createToolbarButton(String text, char icon) {
        JButton btn = new JButton(icon + "  " + text);
        btn.setFont(new Font("SansSerif", Font.PLAIN, 13));
        btn.setFocusPainted(false);
        return btn;
    }

    private void setColumnWidths() {
        table.getColumnModel().getColumn(0).setPreferredWidth(100);
        table.getColumnModel().getColumn(1).setPreferredWidth(110);
        table.getColumnModel().getColumn(2).setPreferredWidth(155);
        table.getColumnModel().getColumn(3).setPreferredWidth(85);
        table.getColumnModel().getColumn(4).setPreferredWidth(70);
        table.getColumnModel().getColumn(5).setPreferredWidth(75);
        table.getColumnModel().getColumn(6).setPreferredWidth(65);
        table.getColumnModel().getColumn(7).setPreferredWidth(80);
        table.getColumnModel().getColumn(8).setPreferredWidth(140);
        table.getColumnModel().getColumn(9).setPreferredWidth(80);
    }

    private void handleRefresh() {
        setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                vehicles = apiClient.fetchVehicles();
                drivers = apiClient.fetchDrivers();
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    updateStatus();
                } catch (Exception e) {
                    String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                    ErrorDialog.show(MainFrame.this, "Refresh failed", msg);
                } finally {
                    setEnabled(true);
                }
            }
        }.execute();
    }

    private void handleStartSelected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a vehicle first", "No Selection",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        Vehicle vehicle = tableModel.getVehicleAt(row);
        UUID vehicleId = vehicle.getId();

        if (registry.isRunning(vehicleId)) {
            JOptionPane.showMessageDialog(this, "Vehicle is already running",
                    "Already Running", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        Driver driver = chooseDriver(drivers);
        if (driver == null) return;

        setEnabled(false);
        new SwingWorker<UUID, Void>() {
            @Override
            protected UUID doInBackground() throws Exception {
                return apiClient.startTrip(driver.getId(), vehicleId);
            }

            @Override
            protected void done() {
                try {
                    UUID tripId = get();
                    driverAssignments.put(vehicleId, driver);
                    tripIds.put(vehicleId, tripId);
                    registry.start(vehicle, driver, tripId);
                    updateStatus();
                } catch (Exception e) {
                    String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                    ErrorDialog.show(MainFrame.this, "Failed to start trip", msg);
                } finally {
                    setEnabled(true);
                }
            }
        }.execute();
    }

    private void handleStopSelected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a vehicle first", "No Selection",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        Vehicle vehicle = tableModel.getVehicleAt(row);
        UUID vehicleId = vehicle.getId();

        if (!registry.isRunning(vehicleId)) return;

        UUID tripId = tripIds.remove(vehicleId);
        driverAssignments.remove(vehicleId);
        registry.stop(vehicleId);
        updateStatus();

        if (tripId != null) {
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    apiClient.endTrip(tripId);
                    return null;
                }

                @Override
                protected void done() {
                    try {
                        get();
                    } catch (Exception e) {
                        String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                        ErrorDialog.show(MainFrame.this, "Failed to end trip", msg);
                    }
                }
            }.execute();
        }
    }

    private void handleInjectFault() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a vehicle first", "No Selection",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        Vehicle vehicle = tableModel.getVehicleAt(row);
        UUID vehicleId = vehicle.getId();

        if (!registry.isRunning(vehicleId)) {
            JOptionPane.showMessageDialog(this, "Vehicle must be running to inject faults",
                    "Not Running", JOptionPane.WARNING_MESSAGE);
            return;
        }

        SimulationState state = registry.getState(vehicleId);
        Set<FaultType> currentFaults = state != null
                ? state.getActiveFaults() : Set.of();

        FaultDialog faultDialog = new FaultDialog(this, currentFaults);
        Set<FaultType> selected = faultDialog.showDialog();

        if (state == null) return;

        for (FaultType fault : FaultType.values()) {
            if (selected.contains(fault)) {
                state.addFault(fault);
            } else {
                state.removeFault(fault);
            }
        }
    }

    private Driver chooseDriver(List<Driver> driverList) {
        if (driverList.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No drivers available. Refresh the list.",
                    "No Drivers", JOptionPane.WARNING_MESSAGE);
            return null;
        }

        JComboBox<Driver> combo = new JComboBox<>(driverList.toArray(new Driver[0]));
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Driver d) {
                    setText(d.getFirstName() + " " + d.getLastName());
                }
                return this;
            }
        });

        int result = JOptionPane.showConfirmDialog(this, combo,
                "Select Driver", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        return result == JOptionPane.OK_OPTION ? (Driver) combo.getSelectedItem() : null;
    }

    private void updateTable() {
        int sel = table.getSelectedRow();
        UUID selId = sel >= 0 && sel < table.getRowCount()
                ? tableModel.getVehicleAt(sel).getId() : null;

        List<SimulationState> states = registry.getAllStates();
        tableModel.update(vehicles, states, Map.copyOf(driverAssignments));

        if (selId != null) {
            for (int i = 0; i < table.getRowCount(); i++) {
                Vehicle v = tableModel.getVehicleAt(i);
                if (v != null && v.getId().equals(selId)) {
                    table.setRowSelectionInterval(i, i);
                    break;
                }
            }
        }

        updateButtonStates();
        updateStatus();
    }

    private void updateButtonStates() {
        int row = table.getSelectedRow();
        if (row < 0) {
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
            faultsButton.setEnabled(false);
            return;
        }

        Vehicle vehicle = tableModel.getVehicleAt(row);
        if (vehicle == null) {
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
            faultsButton.setEnabled(false);
            return;
        }

        boolean running = registry.isRunning(vehicle.getId());
        startButton.setEnabled(!running);
        stopButton.setEnabled(running);
        faultsButton.setEnabled(running);
    }

    private void updateStatus() {
        int runningCount = registry.getActiveCount();
        statusLabel.setText("Vehicles: " + vehicles.size() + "  |  Running: " + runningCount);
    }

}
