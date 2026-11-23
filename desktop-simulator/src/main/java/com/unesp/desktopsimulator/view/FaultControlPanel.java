package com.unesp.desktopsimulator.view;

import com.unesp.desktopsimulator.app.services.simulation.VehicleSnapshot;
import com.unesp.desktopsimulator.app.services.simulator.SimulationState;
import com.unesp.desktopsimulator.model.FaultSeverity;
import com.unesp.desktopsimulator.model.FaultType;
import com.unesp.desktopsimulator.view.components.RoundedPanel;
import com.unesp.desktopsimulator.view.components.ScrollablePanel;
import com.unesp.desktopsimulator.view.components.Theme;

import javax.swing.*;
import java.awt.*;
import java.util.EnumMap;
import java.util.Map;

/**
 * Injeção de falhas do veículo selecionado: uma linha por falha com intensidade
 * Desligada / Leve / Média / Grave, aplicada imediatamente. Mostra o progresso da rampa de agravamento.
 */
class FaultControlPanel extends ScrollablePanel {

    private static final Map<FaultType, String> HINTS = Map.of(
            FaultType.OVERHEATING, "Radiador perde eficiência; a temperatura sobe sob carga.",
            FaultType.BATTERY_DRAIN, "Alternador deixa de carregar; a tensão cai.",
            FaultType.FUEL_LEAK, "Tanque perde combustível além do consumo do motor.",
            FaultType.ERRATIC_RPM, "Falha de injeção: rotação oscila e perde potência.",
            FaultType.GPS_DRIFT, "Posição ruidosa e baixa precisão do GPS.",
            FaultType.OIL_PRESSURE_DROP, "Bomba de óleo desgastada: pressão abaixo do normal.",
            FaultType.BOOST_LEAK, "Vazamento na admissão: menos turbo e potência.",
            FaultType.FUEL_OVERCONSUMPTION, "Bico desregulado: mais combustível para a mesma potência.");

    private final Map<FaultType, ButtonGroup> groups = new EnumMap<>(FaultType.class);
    private final Map<FaultType, JToggleButton[]> buttons = new EnumMap<>(FaultType.class);
    private final Map<FaultType, JLabel> progress = new EnumMap<>(FaultType.class);
    private final JButton clearAll = new JButton("Remover todas as falhas");
    private SimulationState state;
    private boolean syncing;

    FaultControlPanel() {
        super(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));
        setOpaque(false);
        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));

        JLabel intro = new JLabel("<html><body style='width:300px'>A falha se agrava gradualmente em 2 min. Na intensidade leve, só o modelo de ML "
                + "deve perceber; na grave, os alertas fixos também disparam.</html>");
        intro.setFont(Theme.font(Font.PLAIN, 11.5f));
        intro.setForeground(Theme.muted());
        intro.setAlignmentX(LEFT_ALIGNMENT);
        list.add(intro);
        list.add(Box.createVerticalStrut(8));

        for (FaultType type : FaultType.values()) {
            list.add(row(type));
            list.add(Box.createVerticalStrut(6));
        }
        add(list, BorderLayout.CENTER);
        clearAll.addActionListener(e -> {
            if (state != null) state.clearFaults();
            sync();
        });
        add(clearAll, BorderLayout.SOUTH);
    }

    private JComponent row(FaultType type) {
        RoundedPanel row = new RoundedPanel(new BorderLayout(8, 4), 12);
        row.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));

        JLabel name = new JLabel(type.label());
        name.setFont(Theme.font(Font.BOLD, 12.5f));
        JLabel hint = new JLabel(HINTS.getOrDefault(type, ""));
        hint.setFont(Theme.font(Font.PLAIN, 11f));
        hint.setForeground(Theme.muted());
        hint.setMinimumSize(new Dimension(0, 0));
        JLabel prog = new JLabel();
        prog.setFont(Theme.font(Font.PLAIN, 11f));
        progress.put(type, prog);
        JPanel titles = new JPanel(new BorderLayout());
        titles.setOpaque(false);
        titles.add(name, BorderLayout.WEST);
        titles.add(prog, BorderLayout.EAST);
        titles.add(hint, BorderLayout.SOUTH);

        String[] labels = {"Desligada", "Leve", "Média", "Grave"};
        JToggleButton[] bs = new JToggleButton[labels.length];
        ButtonGroup g = new ButtonGroup();
        JPanel seg = new JPanel(new GridLayout(1, labels.length, 4, 0));
        seg.setMinimumSize(new Dimension(0, 0));
        seg.setOpaque(false);
        for (int i = 0; i < labels.length; i++) {
            JToggleButton b = new JToggleButton(labels[i]);
            b.setFont(Theme.font(Font.PLAIN, 11.5f));
            b.setFocusable(false);
            final int level = i;
            b.addActionListener(e -> apply(type, level));
            g.add(b);
            seg.add(b);
            bs[i] = b;
        }
        bs[0].setSelected(true);
        groups.put(type, g);
        buttons.put(type, bs);

        row.add(titles, BorderLayout.NORTH);
        row.add(seg, BorderLayout.CENTER);
        return row;
    }

    private void apply(FaultType type, int level) {
        if (syncing || state == null) return;
        if (level == 0) state.removeFault(type);
        else state.addFault(type, FaultSeverity.values()[level - 1]);
        refresh();
    }

    /** Vincula ao veículo selecionado (null = nenhum em simulação). */
    void bind(SimulationState state) {
        this.state = state;
        sync();
    }

    /** Copia o estado das falhas para os botões. */
    void sync() {
        syncing = true;
        Map<FaultType, FaultSeverity> active = state != null ? state.getFaultSeverities() : Map.of();
        for (FaultType type : FaultType.values()) {
            FaultSeverity sev = active.get(type);
            buttons.get(type)[sev == null ? 0 : sev.ordinal() + 1].setSelected(true);
            for (JToggleButton b : buttons.get(type)) b.setEnabled(state != null);
        }
        clearAll.setEnabled(state != null && !active.isEmpty());
        syncing = false;
        refresh();
    }

    /** Atualiza o progresso da rampa de cada falha ativa. */
    void refresh() {
        VehicleSnapshot snap = state != null ? state.getLastSnapshot() : null;
        Map<FaultType, FaultSeverity> active = state != null ? state.getFaultSeverities() : Map.of();
        for (FaultType type : FaultType.values()) {
            JLabel l = progress.get(type);
            FaultSeverity sev = active.get(type);
            if (sev == null || snap == null) {
                l.setText("");
                continue;
            }
            double p = state.getFaults().progress(type, snap.elapsedS());
            l.setText(p >= 1 ? "● ativa (" + sev.label().toLowerCase() + ")" : String.format("● agravando %.0f%%", p * 100));
            l.setForeground(sev == FaultSeverity.SEVERE ? Theme.CRITICAL : Theme.WARN);
        }
        clearAll.setEnabled(state != null && !active.isEmpty());
    }
}
