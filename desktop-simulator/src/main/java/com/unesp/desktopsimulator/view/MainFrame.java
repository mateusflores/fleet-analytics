package com.unesp.desktopsimulator.view;

import com.unesp.desktopsimulator.app.services.client.RestApiClient;
import com.unesp.desktopsimulator.app.services.client.SessionExpiredException;
import com.unesp.desktopsimulator.app.services.client.dto.ApiTripSummary;
import com.unesp.desktopsimulator.app.services.simulation.DriverStyle;
import com.unesp.desktopsimulator.app.services.simulation.SimulationOptions;
import com.unesp.desktopsimulator.app.services.simulation.route.Route;
import com.unesp.desktopsimulator.app.services.simulator.SimulatorRegistry;
import com.unesp.desktopsimulator.model.Driver;
import com.unesp.desktopsimulator.model.Vehicle;
import com.unesp.desktopsimulator.view.components.Theme;
import com.unesp.desktopsimulator.view.components.WrapLayout;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Janela principal: grade de cards dos veículos (centro) e painel do veículo selecionado (direita),
 * com início/encerramento de viagem e injeção de falhas.
 */
public class MainFrame extends JFrame implements VehicleDetailPanel.Actions {

    private enum Filter {
        ALL("Todos"), RUNNING("Em simulação"), AVAILABLE("Disponíveis"), EXTERNAL("Em viagem (outro terminal)");

        final String label;

        Filter(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final SimulatorRegistry registry;
    private final RestApiClient apiClient;
    private List<Vehicle> vehicles;
    private List<Driver> drivers;

    private final Map<UUID, Driver> driverAssignments = new HashMap<>();
    private final Map<UUID, UUID> tripIds = new HashMap<>();
    private Set<UUID> busyVehicleIds;
    private Set<UUID> busyDriverIds;

    private final Map<UUID, VehicleCard> cards = new LinkedHashMap<>();
    private final JPanel grid = new JPanel(new WrapLayout(FlowLayout.LEFT, 14, 14));
    private final JTextField search = new JTextField(24);
    private final JComboBox<Filter> filter = new JComboBox<>(Filter.values());
    private final JButton refreshButton = new JButton("↻  Atualizar");
    private final JLabel counters = new JLabel();
    private final JLabel connection = new JLabel();
    private final JLabel emptyGrid = new JLabel("Nenhum veículo encontrado.", SwingConstants.CENTER);
    private final VehicleDetailPanel detail;
    private final Timer refreshTimer;
    private final Random random = new Random();
    private Vehicle selected;

    public MainFrame(SimulatorRegistry registry, RestApiClient apiClient,
                     List<Vehicle> vehicles, List<Driver> drivers,
                     Set<UUID> busyVehicleIds, Set<UUID> busyDriverIds) {
        this.registry = registry;
        this.apiClient = apiClient;
        this.vehicles = vehicles;
        this.drivers = drivers;
        this.busyVehicleIds = new HashSet<>(busyVehicleIds);
        this.busyDriverIds = new HashSet<>(busyDriverIds);

        setTitle("Fleet Analytics — Simulador");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1360, 820);
        setMinimumSize(new Dimension(980, 640));
        setLayout(new BorderLayout());

        add(buildHeader(), BorderLayout.NORTH);

        grid.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        JScrollPane gridScroll = new JScrollPane(grid);
        gridScroll.setBorder(BorderFactory.createEmptyBorder());
        gridScroll.getVerticalScrollBar().setUnitIncrement(18);
        gridScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        emptyGrid.setForeground(Theme.muted());
        add(gridScroll, BorderLayout.CENTER);

        detail = new VehicleDetailPanel(this, registry.getRoutes().all());
        add(detail, BorderLayout.EAST);
        add(buildStatusBar(), BorderLayout.SOUTH);

        rebuildCards();
        refreshTimer = new Timer(1000, e -> refresh());
        refreshTimer.start();
        refresh();
        setLocationRelativeTo(null);
    }

    // =====================================================================================================
    // layout

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.border()),
                BorderFactory.createEmptyBorder(12, 18, 12, 18)));

        JLabel title = new JLabel("Frota");
        title.setFont(Theme.font(Font.BOLD, 20f));
        JLabel subtitle = new JLabel("Selecione um veículo para iniciar uma viagem, ver os dados ao vivo ou injetar falhas");
        subtitle.setForeground(Theme.muted());
        subtitle.setFont(Theme.font(Font.PLAIN, 12f));
        JPanel titles = new JPanel(new GridLayout(2, 1, 0, 2));
        titles.add(title);
        titles.add(subtitle);
        header.add(titles, BorderLayout.WEST);

        search.putClientProperty("JTextField.placeholderText", "Buscar por apelido, placa ou modelo");
        search.putClientProperty("JTextField.showClearButton", true);
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
        });
        filter.addActionListener(e -> applyFilter());
        refreshButton.addActionListener(e -> handleRefresh());

        JPanel tools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        tools.add(search);
        tools.add(filter);
        tools.add(refreshButton);
        header.add(tools, BorderLayout.EAST);
        return header;
    }

    private JComponent buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.border()),
                BorderFactory.createEmptyBorder(6, 18, 6, 18)));
        counters.setFont(Theme.font(Font.PLAIN, 12f));
        connection.setFont(Theme.font(Font.PLAIN, 12f));
        bar.add(counters, BorderLayout.WEST);
        bar.add(connection, BorderLayout.EAST);
        return bar;
    }

    // =====================================================================================================
    // cards

    private VehicleStatus statusOf(Vehicle v) {
        if (registry.isRunning(v.getId())) return VehicleStatus.RUNNING;
        if (busyVehicleIds.contains(v.getId())) return VehicleStatus.EXTERNAL;
        return VehicleStatus.AVAILABLE;
    }

    private void rebuildCards() {
        cards.clear();
        for (Vehicle v : vehicles) {
            cards.put(v.getId(), new VehicleCard(v, this::select));
        }
        if (selected != null) {
            VehicleCard card = cards.get(selected.getId());
            selected = card != null ? card.vehicle() : null;
            if (card != null) card.setSelected(true);
        }
        applyFilter();
    }

    private void applyFilter() {
        grid.removeAll();
        Filter f = (Filter) filter.getSelectedItem();
        int shown = 0;
        for (VehicleCard card : cards.values()) {
            Vehicle v = card.vehicle();
            VehicleStatus st = statusOf(v);
            boolean statusOk = switch (f) {
                case ALL -> true;
                case RUNNING -> st == VehicleStatus.RUNNING;
                case AVAILABLE -> st == VehicleStatus.AVAILABLE;
                case EXTERNAL -> st == VehicleStatus.EXTERNAL;
            };
            if (statusOk && VehicleViews.matches(v, search.getText())) {
                grid.add(card);
                shown++;
            }
        }
        if (shown == 0) grid.add(emptyGrid);
        grid.revalidate();
        grid.repaint();
    }

    private void select(Vehicle v) {
        if (selected != null && cards.containsKey(selected.getId())) cards.get(selected.getId()).setSelected(false);
        selected = v;
        if (v != null) cards.get(v.getId()).setSelected(true);
        refresh();
    }

    /** Atualização periódica (1 s): cards, painel lateral e barra de status. */
    private void refresh() {
        int running = 0, external = 0;
        for (VehicleCard card : cards.values()) {
            Vehicle v = card.vehicle();
            VehicleStatus st = statusOf(v);
            if (st == VehicleStatus.RUNNING) running++;
            if (st == VehicleStatus.EXTERNAL) external++;
            card.update(st, registry.getState(v.getId()), driverOf(v));
        }
        List<Driver> available = VehicleDetailPanel.available(drivers, busyDriverIds);
        if (selected != null) {
            detail.show(selected, statusOf(selected), registry.getState(selected.getId()),
                    driverOf(selected), available);
        } else {
            detail.show(null, null, null, null, List.of());
        }
        counters.setText(String.format("%d veículos   ·   %d em simulação   ·   %d em viagem em outro terminal   ·   %d motoristas disponíveis",
                vehicles.size(), running, external, available.size()));
        boolean mqtt = registry.isBrokerConnected();
        boolean api = apiClient.isAuthenticated();
        connection.setText(String.format("<html>API <font color='%s'>●</font> %s &nbsp;&nbsp; MQTT <font color='%s'>●</font> %s</html>",
                hex(api ? Theme.OK : Theme.CRITICAL), api ? "sessão ativa" : "sessão expirada",
                hex(mqtt ? Theme.OK : Theme.CRITICAL), mqtt ? "conectado" : "desconectado"));
    }

    private Driver driverOf(Vehicle v) {
        var state = registry.getState(v.getId());
        if (state != null && state.getDriver() != null) return state.getDriver();
        return driverAssignments.get(v.getId());
    }

    private static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    // =====================================================================================================
    // ações

    private void handleRefresh() {
        refreshButton.setEnabled(false);
        new SwingWorker<Void, Void>() {
            List<Vehicle> v;
            List<Driver> d;
            Set<UUID> bv;
            Set<UUID> bd;

            @Override
            protected Void doInBackground() {
                v = apiClient.fetchVehicles();
                d = apiClient.fetchDrivers();
                bv = apiClient.fetchBusyVehicleIds();
                bd = apiClient.fetchBusyDriverIds();
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    vehicles = v;
                    drivers = d;
                    busyVehicleIds = new HashSet<>(bv);
                    busyDriverIds = new HashSet<>(bd);
                    rebuildCards();
                    detail.invalidateSelection();
                    refresh();
                } catch (Exception e) {
                    showError("Falha ao atualizar", e);
                } finally {
                    refreshButton.setEnabled(true);
                }
            }
        }.execute();
    }

    @Override
    public void startTrip(Vehicle vehicle, Driver driver, Route route, DriverStyle style, boolean warmStart) {
        UUID vehicleId = vehicle.getId();
        if (registry.isRunning(vehicleId) || busyVehicleIds.contains(vehicleId)) {
            detail.startFailed();
            return;
        }
        new SwingWorker<UUID, Void>() {
            @Override
            protected UUID doInBackground() {
                return apiClient.startTrip(driver.getId(), vehicleId);
            }

            @Override
            protected void done() {
                try {
                    UUID tripId = get();
                    driverAssignments.put(vehicleId, driver);
                    tripIds.put(vehicleId, tripId);
                    busyVehicleIds.add(vehicleId);
                    busyDriverIds.add(driver.getId());
                    SimulationOptions options = new SimulationOptions(style, warmStart, 25.0,
                            0.4 + 0.5 * random.nextDouble(), 0.6 + 0.35 * random.nextDouble(),
                            50_000 + Math.floorMod(vehicleId.hashCode(), 250_000), random.nextLong());
                    registry.start(vehicle, driver, tripId, route, options);
                    detail.invalidateSelection();
                    applyFilter();
                    refresh();
                } catch (Exception e) {
                    detail.startFailed();
                    showError("Não foi possível iniciar a viagem", e);
                }
            }
        }.execute();
    }

    @Override
    public void stopTrip(Vehicle vehicle) {
        UUID vehicleId = vehicle.getId();
        if (registry.isRunning(vehicleId)) {
            UUID tripId = tripIds.remove(vehicleId);
            Driver driver = driverAssignments.remove(vehicleId);
            registry.stop(vehicleId);
            busyVehicleIds.remove(vehicleId);
            if (driver != null) busyDriverIds.remove(driver.getId());
            if (tripId != null) endTripAsync(tripId);
            detail.invalidateSelection();
            applyFilter();
            refresh();
        } else if (busyVehicleIds.contains(vehicleId)) {
            new SwingWorker<ApiTripSummary, Void>() {
                @Override
                protected ApiTripSummary doInBackground() {
                    ApiTripSummary trip = apiClient.findActiveTripByVehicle(vehicleId);
                    apiClient.endTrip(trip.id());
                    return trip;
                }

                @Override
                protected void done() {
                    try {
                        ApiTripSummary trip = get();
                        busyVehicleIds.remove(vehicleId);
                        busyDriverIds.remove(trip.driverId());
                        detail.invalidateSelection();
                        applyFilter();
                        refresh();
                    } catch (Exception e) {
                        showError("Não foi possível encerrar a viagem", e);
                    }
                }
            }.execute();
        }
    }

    private void endTripAsync(UUID tripId) {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                apiClient.endTrip(tripId);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (Exception e) {
                    showError("Não foi possível encerrar a viagem na API", e);
                }
            }
        }.execute();
    }

    private void showError(String title, Exception e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        if (cause instanceof SessionExpiredException) {
            ErrorDialog.show(this, "Sessão expirada",
                    "Sua sessão expirou. As simulações em andamento continuam publicando via MQTT, "
                            + "mas reinicie o aplicativo e faça login novamente para gerenciar viagens.");
            return;
        }
        ErrorDialog.show(this, title, cause.getMessage());
    }
}
