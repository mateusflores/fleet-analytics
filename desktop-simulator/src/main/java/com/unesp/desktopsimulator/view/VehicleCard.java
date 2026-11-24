package com.unesp.desktopsimulator.view;

import com.unesp.desktopsimulator.app.services.simulation.VehicleSnapshot;
import com.unesp.desktopsimulator.app.services.simulator.SimulationState;
import com.unesp.desktopsimulator.model.Driver;
import com.unesp.desktopsimulator.model.Vehicle;
import com.unesp.desktopsimulator.view.components.Chip;
import com.unesp.desktopsimulator.view.components.RoundedPanel;
import com.unesp.desktopsimulator.view.components.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/** Card de um veículo na grade: identificação, status e as leituras instantâneas principais. */
class VehicleCard extends RoundedPanel {

    static final Dimension SIZE = new Dimension(280, 178);

    private final Vehicle vehicle;
    private final JLabel name = new JLabel();
    private final JLabel subtitle = new JLabel();
    private final Chip status = new Chip();
    private final Chip faults = new Chip();
    private final JLabel speed = new JLabel();
    private final JLabel speedUnit = new JLabel("km/h");
    private final JLabel line1 = new JLabel();
    private final JLabel line2 = new JLabel();
    private final JProgressBar fuel = new JProgressBar(0, 100);
    private final JLabel footer = new JLabel();
    private final JPanel live = new JPanel(new GridBagLayout());
    private final JLabel idleText = new JLabel();
    private boolean selected;
    private boolean hover;
    private int alarmLevel;

    VehicleCard(Vehicle vehicle, Consumer<Vehicle> onClick) {
        super(new BorderLayout(0, 6), 16);
        this.vehicle = vehicle;
        setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        setPreferredSize(SIZE);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        name.setFont(Theme.font(Font.BOLD, 15f));
        subtitle.setFont(Theme.font(Font.PLAIN, 11.5f));
        subtitle.setForeground(Theme.muted());
        JPanel titles = new JPanel(new GridLayout(2, 1, 0, 1));
        titles.setOpaque(false);
        titles.add(name);
        titles.add(subtitle);
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        header.add(titles, BorderLayout.CENTER);
        JPanel chips = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        chips.setOpaque(false);
        chips.add(status);
        header.add(chips, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        live.setOpaque(false);
        speed.setFont(Theme.font(Font.BOLD, 30f));
        speedUnit.setFont(Theme.font(Font.PLAIN, 12f));
        speedUnit.setForeground(Theme.muted());
        line1.setFont(Theme.font(Font.PLAIN, 12f));
        line2.setFont(Theme.font(Font.PLAIN, 12f));
        fuel.setStringPainted(false);
        fuel.setPreferredSize(new Dimension(100, 6));
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        c.gridy = 0;
        c.gridheight = 2;
        live.add(speed, c);
        c.gridx = 1;
        c.insets = new Insets(10, 4, 0, 14);
        c.anchor = GridBagConstraints.SOUTHWEST;
        live.add(speedUnit, c);
        c.gridheight = 1;
        c.gridx = 2;
        c.insets = new Insets(0, 0, 0, 0);
        c.weightx = 1;
        c.anchor = GridBagConstraints.WEST;
        live.add(line1, c);
        c.gridy = 1;
        live.add(line2, c);
        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 3;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(8, 0, 0, 0);
        live.add(fuel, c);

        idleText.setFont(Theme.font(Font.PLAIN, 12f));
        idleText.setForeground(Theme.muted());
        idleText.setVerticalAlignment(SwingConstants.TOP);

        footer.setFont(Theme.font(Font.PLAIN, 11.5f));
        footer.setForeground(Theme.muted());
        JPanel bottom = new JPanel(new BorderLayout(6, 0));
        bottom.setOpaque(false);
        bottom.add(footer, BorderLayout.CENTER);
        bottom.add(faults, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                onClick.accept(vehicle);
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                hover = true;
                restyle();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (!contains(SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), VehicleCard.this))) {
                    hover = false;
                    restyle();
                }
            }
        };
        addMouseListenerRecursively(this, mouse);
        addMouseListenerRecursively(live, mouse);   // trocados dinamicamente no centro do card
        addMouseListenerRecursively(idleText, mouse);
        name.setText(VehicleViews.displayName(vehicle));
        subtitle.setText(VehicleViews.subtitle(vehicle));
        restyle();
    }

    private static void addMouseListenerRecursively(Component comp, MouseAdapter l) {
        comp.addMouseListener(l);
        if (comp instanceof Container container) {
            for (Component child : container.getComponents()) addMouseListenerRecursively(child, l);
        }
    }

    Vehicle vehicle() {
        return vehicle;
    }

    void setSelected(boolean selected) {
        this.selected = selected;
        restyle();
    }

    private void restyle() {
        setFill(hover ? Theme.cardHover() : Theme.card());
        if (selected) setStroke(Theme.ACCENT, 2f);
        else if (alarmLevel > 0) setStroke(Theme.levelColor(alarmLevel), 1.5f);
        else setStroke(Theme.border(), 1f);
    }

    void update(VehicleStatus st, SimulationState state, Driver driver) {
        status.set(st.label, st.color);
        VehicleSnapshot s = state != null ? state.getLastSnapshot() : null;
        Component center = ((BorderLayout) getLayout()).getLayoutComponent(BorderLayout.CENTER);
        Component wanted = s != null ? live : idleText;
        if (center != wanted) {
            if (center != null) remove(center);
            add(wanted, BorderLayout.CENTER);
            revalidate();
        }
        if (s != null) {
            speed.setText(String.format("%.0f", s.speedKmh()));
            line1.setText(String.format("%d rpm · %dª marcha", s.engineRpm(), s.gear()));
            line2.setText(String.format("%.0f °C · %.1f V", s.coolantC(), s.batteryV()));
            line1.setForeground(UIManager.getColor("Label.foreground"));
            int coolant = MetricLevels.coolant(s);
            int battery = MetricLevels.battery(s, state.getProfile());
            line2.setForeground(Math.max(coolant, battery) > 0
                    ? Theme.levelColor(Math.max(coolant, battery)) : UIManager.getColor("Label.foreground"));
            fuel.setValue((int) Math.round(s.fuelLevelPct()));
            fuel.setForeground(Theme.levelColor(MetricLevels.fuel(s)));
            fuel.setToolTipText(String.format("Combustível %.1f%%", s.fuelLevelPct()));
            footer.setText((driver != null ? driver.getFirstName() + " · " : "") + s.phase());
            alarmLevel = MetricLevels.worst(s, state.getProfile());
        } else {
            idleText.setText(st == VehicleStatus.EXTERNAL
                    ? "<html>Viagem iniciada em outro terminal.<br>Selecione para encerrar.</html>"
                    : "<html>Pronto para uma nova viagem.<br>Selecione para iniciar.</html>");
            footer.setText(VehicleViews.typeLabel(vehicle));
            alarmLevel = 0;
        }
        int nFaults = state != null ? state.getActiveFaults().size() : 0;
        faults.setVisible(nFaults > 0);
        if (nFaults > 0) faults.set("⚠ " + nFaults + (nFaults == 1 ? " falha" : " falhas"), Theme.WARN);
        restyle();
    }
}
