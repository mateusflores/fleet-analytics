package com.unesp.desktopsimulator.view;

import com.unesp.desktopsimulator.model.FaultType;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class FaultDialog extends JDialog {

    private final Map<FaultType, JCheckBox> checkboxes = new EnumMap<>(FaultType.class);
    private boolean confirmed;

    public FaultDialog(Frame owner, Set<FaultType> currentFaults) {
        super(owner, "Inject Faults", true);
        setLayout(new BorderLayout(10, 10));

        JPanel checks = new JPanel(new GridLayout(0, 1, 5, 5));
        checks.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        for (FaultType fault : FaultType.values()) {
            JCheckBox cb = new JCheckBox(formatFaultName(fault));
            cb.setSelected(currentFaults.contains(fault));
            checkboxes.put(fault, cb);
            checks.add(cb);
        }

        add(checks, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okBtn = new JButton("Apply");
        okBtn.addActionListener(e -> {
            confirmed = true;
            dispose();
        });
        buttons.add(okBtn);

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());
        buttons.add(cancelBtn);

        add(buttons, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(owner);
        setResizable(false);
    }

    public Set<FaultType> showDialog() {
        setVisible(true);
        if (!confirmed) return Collections.emptySet();
        return checkboxes.entrySet().stream()
                .filter(e -> e.getValue().isSelected())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private static String formatFaultName(FaultType fault) {
        String name = fault.name().replace('_', ' ').toLowerCase();
        if (name.length() > 0) {
            name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }
        return name;
    }

}
