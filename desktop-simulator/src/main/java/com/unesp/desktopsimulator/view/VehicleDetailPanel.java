package com.unesp.desktopsimulator.view;

import com.unesp.desktopsimulator.app.services.simulation.DriverStyle;
import com.unesp.desktopsimulator.app.services.simulation.VehicleProfile;
import com.unesp.desktopsimulator.app.services.simulation.VehicleSnapshot;
import com.unesp.desktopsimulator.app.services.simulation.route.Route;
import com.unesp.desktopsimulator.app.services.simulator.SimulationState;
import com.unesp.desktopsimulator.model.Driver;
import com.unesp.desktopsimulator.model.Vehicle;
import com.unesp.desktopsimulator.model.telemetry.Message;
import com.unesp.desktopsimulator.view.components.Chip;
import com.unesp.desktopsimulator.view.components.MetricTile;
import com.unesp.desktopsimulator.view.components.RoundedPanel;
import com.unesp.desktopsimulator.view.components.Theme;

import javax.swing.*;
import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * Painel lateral do veículo selecionado: identificação, controle da viagem (iniciar/encerrar) e abas com os
 * dados ao vivo, a injeção de falhas e os alertas emitidos.
 */
class VehicleDetailPanel extends JPanel {

    interface Actions {
        void startTrip(Vehicle vehicle, Driver driver, Route route, DriverStyle style, boolean warmStart);

        void stopTrip(Vehicle vehicle);
    }

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Actions actions;
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);

    // cabeçalho
    private final JLabel name = new JLabel();
    private final JLabel subtitle = new JLabel();
    private final Chip status = new Chip();

    // viagem
    private final CardLayout tripCards = new CardLayout();
    private final JPanel trip = new JPanel(tripCards);
    private final JComboBox<Driver> driverBox = new JComboBox<>();
    private final JComboBox<Route> routeBox = new JComboBox<>();
    private final JToggleButton economic = new JToggleButton(DriverStyle.ECONOMIC.label());
    private final JToggleButton aggressive = new JToggleButton(DriverStyle.AGGRESSIVE.label());
    private final JCheckBox warmStart = new JCheckBox("Motor já aquecido (pula os ~20 min de aquecimento)", true);
    private final JButton startButton = new JButton("▶  Iniciar viagem");
    private final JButton stopButton = new JButton("■  Encerrar viagem");
    private final JButton stopExternalButton = new JButton("■  Encerrar viagem");
    private final JLabel tripDriver = new JLabel();
    private final JLabel tripRoute = new JLabel();
    private final JLabel tripPhase = new JLabel();
    private final JLabel tripTime = new JLabel();
    private final JLabel tripDistance = new JLabel();
    private final JProgressBar legProgress = new JProgressBar(0, 1000);

    // abas
    private final JTabbedPane tabs = new JTabbedPane();
    private final Map<String, MetricTile> tiles = new LinkedHashMap<>();
    private final JLabel positionInfo = new JLabel();
    private final FaultControlPanel faultPanel = new FaultControlPanel();
    private final DefaultListModel<Message> alertModel = new DefaultListModel<>();
    private final JLabel liveEmpty = emptyLabel("Inicie uma viagem para ver os dados gerados.");

    private Vehicle vehicle;
    private SimulationState state;
    private VehicleStatus currentStatus;
    private int alertCount = -1;

    VehicleDetailPanel(Actions actions, List<Route> routes) {
        this.actions = actions;
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(440, 600));
        setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Theme.border()));

        root.add(emptyLabel("<html><center>Selecione um veículo para ver os dados<br>e controlar a simulação.</center></html>"), "empty");
        root.add(buildContent(routes), "vehicle");
        add(root, BorderLayout.CENTER);
        cards.show(root, "empty");
    }

    private static JLabel emptyLabel(String text) {
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setForeground(Theme.muted());
        l.setFont(Theme.font(Font.PLAIN, 13f));
        return l;
    }

    // =====================================================================================================
    // construção

    private JComponent buildContent(List<Route> routes) {
        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));

        name.setFont(Theme.font(Font.BOLD, 20f));
        subtitle.setFont(Theme.font(Font.PLAIN, 12f));
        subtitle.setForeground(Theme.muted());
        JPanel titles = new JPanel(new GridLayout(2, 1, 0, 2));
        titles.add(name);
        titles.add(subtitle);
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.add(titles, BorderLayout.CENTER);
        JPanel chipBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 6));
        chipBox.add(status);
        header.add(chipBox, BorderLayout.EAST);

        JPanel top = new JPanel(new BorderLayout(0, 12));
        top.add(header, BorderLayout.NORTH);
        top.add(buildTrip(routes), BorderLayout.CENTER);
        content.add(top, BorderLayout.NORTH);

        tabs.addTab("Dados ao vivo", buildLive());
        JScrollPane faultScroll = new JScrollPane(faultPanel);
        faultScroll.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        faultScroll.getVerticalScrollBar().setUnitIncrement(16);
        faultScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        tabs.addTab("Falhas", faultScroll);
        tabs.addTab("Alertas", buildAlerts());
        content.add(tabs, BorderLayout.CENTER);
        return content;
    }

    private JComponent buildTrip(List<Route> routes) {
        // --- formulário de início
        RoundedPanel form = new RoundedPanel(new GridBagLayout(), 14);
        form.setBorder(BorderFactory.createEmptyBorder(12, 14, 14, 14));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.anchor = GridBagConstraints.WEST;
        form.add(Theme.sectionTitle("Nova viagem"), c);

        driverBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean sel, boolean focus) {
                super.getListCellRendererComponent(list, value, index, sel, focus);
                setText(value instanceof Driver d ? d.getFirstName() + " " + d.getLastName() : "Selecione o motorista");
                return this;
            }
        });
        routes.forEach(routeBox::addItem);
        routeBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean sel, boolean focus) {
                super.getListCellRendererComponent(list, value, index, sel, focus);
                if (value instanceof Route r) setText(String.format("%s  (%.0f km)", r.name(), r.lengthM() / 1000));
                return this;
            }
        });
        ButtonGroup styles = new ButtonGroup();
        styles.add(economic);
        styles.add(aggressive);
        economic.setSelected(true);
        JPanel styleSeg = new JPanel(new GridLayout(1, 2, 6, 0));
        styleSeg.setOpaque(false);
        styleSeg.add(economic);
        styleSeg.add(aggressive);
        warmStart.setOpaque(false);
        warmStart.setFont(Theme.font(Font.PLAIN, 12f));

        addField(form, c, 1, "Motorista", driverBox);
        addField(form, c, 2, "Rota", routeBox);
        addField(form, c, 3, "Condução", styleSeg);
        c.gridx = 0;
        c.gridy = 4;
        c.gridwidth = 2;
        c.insets = new Insets(6, 0, 0, 0);
        form.add(warmStart, c);
        startButton.putClientProperty("JButton.buttonType", "default");
        startButton.setFont(Theme.font(Font.BOLD, 13.5f));
        startButton.setBackground(Theme.ACCENT);
        startButton.setForeground(Color.WHITE);
        startButton.addActionListener(e -> onStart());
        c.gridy = 5;
        c.insets = new Insets(10, 0, 0, 0);
        c.ipady = 8;
        form.add(startButton, c);

        // --- viagem em andamento
        RoundedPanel running = new RoundedPanel(new GridBagLayout(), 14);
        running.setBorder(BorderFactory.createEmptyBorder(12, 14, 14, 14));
        GridBagConstraints r = new GridBagConstraints();
        r.gridx = 0;
        r.gridy = 0;
        r.gridwidth = 2;
        r.fill = GridBagConstraints.HORIZONTAL;
        r.weightx = 1;
        running.add(Theme.sectionTitle("Viagem em andamento"), r);
        addInfo(running, r, 1, "Motorista", tripDriver);
        addInfo(running, r, 2, "Rota", tripRoute);
        addInfo(running, r, 3, "Situação", tripPhase);
        addInfo(running, r, 4, "Duração", tripTime);
        addInfo(running, r, 5, "Distância", tripDistance);
        legProgress.setStringPainted(true);
        legProgress.setFont(Theme.font(Font.PLAIN, 11f));
        r.gridx = 0;
        r.gridy = 6;
        r.gridwidth = 2;
        r.insets = new Insets(8, 0, 0, 0);
        running.add(legProgress, r);
        styleDanger(stopButton);
        stopButton.addActionListener(e -> onStop());
        r.gridy = 7;
        r.insets = new Insets(10, 0, 0, 0);
        r.ipady = 8;
        running.add(stopButton, r);

        // --- viagem de outro terminal
        RoundedPanel external = new RoundedPanel(new BorderLayout(0, 10), 14);
        external.setBorder(BorderFactory.createEmptyBorder(12, 14, 14, 14));
        JLabel extText = new JLabel("<html>Este veículo está em uma viagem iniciada em outro terminal (ou que não foi "
                + "encerrada). Encerre para liberá-lo.</html>");
        extText.setFont(Theme.font(Font.PLAIN, 12f));
        external.add(Theme.sectionTitle("Viagem externa"), BorderLayout.NORTH);
        external.add(extText, BorderLayout.CENTER);
        styleDanger(stopExternalButton);
        stopExternalButton.addActionListener(e -> onStop());
        external.add(stopExternalButton, BorderLayout.SOUTH);

        trip.add(form, VehicleStatus.AVAILABLE.name());
        trip.add(running, VehicleStatus.RUNNING.name());
        trip.add(external, VehicleStatus.EXTERNAL.name());
        return trip;
    }

    private static void styleDanger(JButton b) {
        b.setFont(Theme.font(Font.BOLD, 13.5f));
        b.setBackground(Theme.CRITICAL);
        b.setForeground(Color.WHITE);
    }

    private static void addField(JPanel p, GridBagConstraints c, int row, String label, JComponent field) {
        JLabel l = new JLabel(label);
        l.setFont(Theme.font(Font.PLAIN, 12f));
        l.setForeground(Theme.muted());
        c.gridy = row;
        c.gridwidth = 1;
        c.gridx = 0;
        c.weightx = 0;
        c.insets = new Insets(8, 0, 0, 10);
        p.add(l, c);
        c.gridx = 1;
        c.weightx = 1;
        c.insets = new Insets(8, 0, 0, 0);
        p.add(field, c);
    }

    private static void addInfo(JPanel p, GridBagConstraints c, int row, String label, JLabel value) {
        JLabel l = new JLabel(label);
        l.setFont(Theme.font(Font.PLAIN, 12f));
        l.setForeground(Theme.muted());
        value.setFont(Theme.font(Font.PLAIN, 12.5f));
        c.gridy = row;
        c.gridwidth = 1;
        c.gridx = 0;
        c.weightx = 0;
        c.insets = new Insets(6, 0, 0, 12);
        p.add(l, c);
        c.gridx = 1;
        c.weightx = 1;
        c.insets = new Insets(6, 0, 0, 0);
        p.add(value, c);
    }

    private JComponent buildLive() {
        String[][] groups = {
                {"Movimento", "speed", "rpm", "gear", "pedal", "load", "torque"},
                {"Motor", "coolant", "oil", "boost", "intake"},
                {"Combustível e elétrica", "fuelRate", "fuel", "battery", "grade"},
        };
        tiles.put("speed", new MetricTile("Velocidade", "km/h", 10));
        tiles.put("rpm", new MetricTile("Rotação", "rpm", 200));
        tiles.put("gear", new MetricTile("Marcha", "", 1));
        tiles.put("pedal", new MetricTile("Acelerador", "%", 10));
        tiles.put("load", new MetricTile("Carga do motor", "%", 10));
        tiles.put("torque", new MetricTile("Torque", "%", 10));
        tiles.put("coolant", new MetricTile("Temperatura do motor", "°C", 4));
        tiles.put("oil", new MetricTile("Pressão do óleo", "kPa", 40));
        tiles.put("boost", new MetricTile("Pressão do turbo", "kPa", 20));
        tiles.put("intake", new MetricTile("Temp. de admissão", "°C", 4));
        tiles.put("fuelRate", new MetricTile("Consumo", "L/h", 4));
        tiles.put("fuel", new MetricTile("Nível de combustível", "%", 2));
        tiles.put("battery", new MetricTile("Bateria", "V", 0.4));
        tiles.put("grade", new MetricTile("Rampa da via", "%", 2));

        JPanel list = new com.unesp.desktopsimulator.view.components.ScrollablePanel(null);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBorder(BorderFactory.createEmptyBorder(10, 0, 6, 6));
        for (String[] g : groups) {
            JLabel title = Theme.sectionTitle(g[0]);
            title.setAlignmentX(LEFT_ALIGNMENT);
            list.add(title);
            list.add(Box.createVerticalStrut(6));
            JPanel grid = new JPanel(new GridLayout(0, 2, 8, 8));
            grid.setAlignmentX(LEFT_ALIGNMENT);
            for (int i = 1; i < g.length; i++) grid.add(tiles.get(g[i]));
            list.add(grid);
            list.add(Box.createVerticalStrut(14));
        }
        JLabel posTitle = Theme.sectionTitle("Posição");
        posTitle.setAlignmentX(LEFT_ALIGNMENT);
        list.add(posTitle);
        list.add(Box.createVerticalStrut(6));
        positionInfo.setFont(Theme.font(Font.PLAIN, 12f));
        positionInfo.setAlignmentX(LEFT_ALIGNMENT);
        list.add(positionInfo);

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        JPanel live = new JPanel(new CardLayout());
        live.add(liveEmpty, "empty");
        live.add(scroll, "data");
        return live;
    }

    private JComponent buildAlerts() {
        JList<Message> list = new JList<>(alertModel);
        list.setCellRenderer((l, m, index, sel, focus) -> {
            JPanel p = new JPanel(new BorderLayout(8, 2));
            p.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
            boolean critical = "CRITICAL".equals(m.getSeverity());
            JLabel title = new JLabel((critical ? "● Crítico · " : "● Atenção · ") + AlertLabels.of(m.getEventType()));
            title.setFont(Theme.font(Font.BOLD, 12.5f));
            title.setForeground(critical ? Theme.CRITICAL : Theme.WARN);
            JLabel time = new JLabel(m.getTimestamp() != null ? CLOCK.format(m.getTimestamp()) : "");
            time.setForeground(Theme.muted());
            JLabel msg = new JLabel(m.getMessage());
            msg.setFont(Theme.font(Font.PLAIN, 12f));
            p.add(title, BorderLayout.CENTER);
            p.add(time, BorderLayout.EAST);
            p.add(msg, BorderLayout.SOUTH);
            p.setBackground(sel ? l.getSelectionBackground() : l.getBackground());
            return p;
        });
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        JLabel hint = new JLabel("<html>Alertas por limite fixo gerados pelo simulador (os insights do ML chegam pela "
                + "plataforma web).</html>");
        hint.setFont(Theme.font(Font.PLAIN, 11.5f));
        hint.setForeground(Theme.muted());
        hint.setBorder(BorderFactory.createEmptyBorder(8, 2, 0, 2));
        panel.add(hint, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    // =====================================================================================================
    // dados

    /** Mostra um veículo (ou nenhum, se null). Chamado na seleção e a cada atualização. */
    void show(Vehicle vehicle, VehicleStatus st, SimulationState state, Driver driver, List<Driver> availableDrivers) {
        boolean changed = this.vehicle != vehicle || this.state != state || currentStatus != st;
        this.vehicle = vehicle;
        if (vehicle == null) {
            cards.show(root, "empty");
            return;
        }
        cards.show(root, "vehicle");
        name.setText(VehicleViews.displayName(vehicle));
        String type = VehicleViews.typeLabel(vehicle);
        subtitle.setText(VehicleViews.subtitle(vehicle) + (type.isEmpty() ? "" : " · " + type));
        status.set(st.label, st.color);

        if (changed) {
            this.state = state;
            this.currentStatus = st;
            alertCount = -1;
            tripCards.show(trip, st.name());
            faultPanel.bind(st == VehicleStatus.RUNNING ? state : null);
            if (st == VehicleStatus.AVAILABLE) fillDrivers(availableDrivers);
            tabs.setEnabledAt(1, st == VehicleStatus.RUNNING);
            tabs.setEnabledAt(2, st == VehicleStatus.RUNNING);
            if (st != VehicleStatus.RUNNING) tabs.setSelectedIndex(0);
        }
        if (st == VehicleStatus.RUNNING && state != null) {
            refreshRunning(state, driver);
            faultPanel.refresh();
        } else {
            showLive(false);
        }
    }

    private void fillDrivers(List<Driver> drivers) {
        Object selected = driverBox.getSelectedItem();
        driverBox.removeAllItems();
        driverBox.addItem(null);
        drivers.stream()
                .sorted(Comparator.comparing((Driver d) -> d.getFirstName() + " " + d.getLastName()))
                .forEach(driverBox::addItem);
        if (selected instanceof Driver d && drivers.contains(d)) driverBox.setSelectedItem(d);
        else driverBox.setSelectedIndex(0);
        startButton.setEnabled(true);
    }

    private void showLive(boolean data) {
        JPanel live = (JPanel) tabs.getComponentAt(0);
        ((CardLayout) live.getLayout()).show(live, data ? "data" : "empty");
    }

    private void refreshRunning(SimulationState state, Driver driver) {
        VehicleSnapshot s = state.getLastSnapshot();
        tripDriver.setText(driver != null ? driver.getFirstName() + " " + driver.getLastName() : "–");
        tripRoute.setText(state.getRouteName() != null ? state.getRouteName() : "–");
        DriverStyle style = state.getOptions() != null ? state.getOptions().style() : null;
        if (style != null) tripRoute.setToolTipText("Condução " + style.label().toLowerCase());
        Instant started = state.getStartedAt();
        tripTime.setText(started != null ? formatDuration(Duration.between(started, Instant.now())) : "–");
        if (s == null) {
            showLive(false);
            return;
        }
        showLive(true);
        tripPhase.setText(s.phase());
        double start = state.getOptions() != null ? state.getOptions().initialOdometerKm() : s.odometerKm();
        tripDistance.setText(String.format("%.1f km", s.odometerKm() - start));
        legProgress.setValue((int) Math.round(s.legProgress() * 1000));
        legProgress.setString(String.format("%s · %.0f%% do trajeto", s.returning() ? "Volta" : "Ida", s.legProgress() * 100));

        List<VehicleSnapshot> h = state.getHistory();
        VehicleProfile p = state.getProfile();
        set("speed", fmt("%.0f", s.speedKmh()), -1, h, VehicleSnapshot::speedKmh);
        set("rpm", String.valueOf(s.engineRpm()), MetricLevels.rpm(s, p), h, x -> x.engineRpm());
        set("gear", s.speedKmh() < 0.5 ? "N/" + s.gear() : s.gear() + "ª", -1, h, x -> x.gear());
        set("pedal", fmt("%.0f", s.pedalPct()), -1, h, VehicleSnapshot::pedalPct);
        set("load", fmt("%.0f", s.loadPct()), -1, h, VehicleSnapshot::loadPct);
        set("torque", fmt("%.0f", s.engineTorquePct()), -1, h, VehicleSnapshot::engineTorquePct);
        set("coolant", fmt("%.0f", s.coolantC()), MetricLevels.coolant(s), h, VehicleSnapshot::coolantC);
        set("oil", fmt("%.0f", s.oilKpa()), MetricLevels.oil(s), h, VehicleSnapshot::oilKpa);
        set("boost", fmt("%.0f", s.boostKpa()), -1, h, VehicleSnapshot::boostKpa);
        set("intake", fmt("%.0f", s.intakeC()), -1, h, VehicleSnapshot::intakeC);
        set("fuelRate", fmt("%.1f", s.fuelRateLph()), -1, h, VehicleSnapshot::fuelRateLph);
        set("fuel", fmt("%.1f", s.fuelLevelPct()), MetricLevels.fuel(s), h, VehicleSnapshot::fuelLevelPct);
        set("battery", fmt("%.2f", s.batteryV()), MetricLevels.battery(s, p), h, VehicleSnapshot::batteryV);
        set("grade", fmt("%+.1f", s.gradePct()), -1, h, VehicleSnapshot::gradePct);
        positionInfo.setText(String.format("<html>%.5f, %.5f · rumo %.0f°<br>Precisão do GPS: %.0f m · limite da via: %.0f km/h"
                        + "<br>Odômetro: %,.1f km</html>",
                s.latitude(), s.longitude(), s.headingDeg(), s.gpsAccuracyM(), s.speedLimitKmh(), s.odometerKm()));

        List<Message> alerts = state.getRecentAlerts();
        if (alerts.size() != alertCount) {
            alertCount = alerts.size();
            alertModel.clear();
            alerts.forEach(alertModel::addElement);
            tabs.setTitleAt(2, alerts.isEmpty() ? "Alertas" : "Alertas (" + alerts.size() + ")");
        }
        int nFaults = state.getActiveFaults().size();
        tabs.setTitleAt(1, nFaults == 0 ? "Falhas" : "Falhas (" + nFaults + ")");
    }

    private void set(String key, String text, int level, List<VehicleSnapshot> history, ToDoubleFunction<VehicleSnapshot> f) {
        double[] series = new double[history.size()];
        for (int i = 0; i < series.length; i++) series[i] = f.applyAsDouble(history.get(i));
        tiles.get(key).update(text, level, series);
    }

    private static String fmt(String pattern, double v) {
        return String.format(pattern, v);
    }

    private static String formatDuration(Duration d) {
        long s = Math.max(0, d.getSeconds());
        return String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

    // =====================================================================================================
    // ações

    private void onStart() {
        if (vehicle == null) return;
        if (!(driverBox.getSelectedItem() instanceof Driver driver)) {
            JOptionPane.showMessageDialog(this, "Selecione um motorista disponível.", "Motorista",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        Route route = (Route) routeBox.getSelectedItem();
        DriverStyle style = aggressive.isSelected() ? DriverStyle.AGGRESSIVE : DriverStyle.ECONOMIC;
        startButton.setEnabled(false);
        actions.startTrip(vehicle, driver, route, style, warmStart.isSelected());
    }

    /** Reabilita o botão de início depois de uma falha ao iniciar. */
    void startFailed() {
        startButton.setEnabled(true);
    }

    private void onStop() {
        if (vehicle == null) return;
        int ok = JOptionPane.showConfirmDialog(this,
                "Encerrar a viagem de " + VehicleViews.displayName(vehicle) + "?", "Encerrar viagem",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (ok == JOptionPane.OK_OPTION) actions.stopTrip(vehicle);
    }

    /** Força a reconstrução na próxima atualização (ex.: depois de iniciar/encerrar). */
    void invalidateSelection() {
        this.currentStatus = null;
    }

    static List<Driver> available(List<Driver> all, java.util.Set<java.util.UUID> busy) {
        List<Driver> out = new ArrayList<>();
        for (Driver d : all) if (!busy.contains(d.getId())) out.add(d);
        return out;
    }
}
