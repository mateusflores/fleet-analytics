package com.unesp.desktopsimulator.view;

import com.unesp.desktopsimulator.model.Driver;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class DriverSelectionDialog extends JDialog {

    private final List<Driver> allDrivers;
    private final List<Driver> filteredDrivers = new ArrayList<>();
    private final Set<UUID> busyDriverIds;
    private final DriverTableModel driverModel;
    private final JTable driverTable;
    private final JTextField searchField;
    private final JCheckBox availableOnlyBox;
    private Driver selectedDriver;

    public DriverSelectionDialog(Frame owner, List<Driver> drivers, Set<UUID> busyDriverIds) {
        super(owner, "Select Driver", true);
        this.allDrivers = drivers;
        this.busyDriverIds = busyDriverIds;
        this.filteredDrivers.addAll(drivers);

        setLayout(new BorderLayout(10, 10));
        setSize(550, 420);
        setLocationRelativeTo(owner);

        JPanel searchPanel = new JPanel(new BorderLayout(6, 0));
        searchPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 6, 12));

        searchField = new JTextField();
        searchField.putClientProperty("JTextField.placeholderText", "Search by name...");
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
        });
        searchPanel.add(searchField, BorderLayout.CENTER);

        availableOnlyBox = new JCheckBox("Show available only");
        availableOnlyBox.addActionListener(e -> applyFilter());
        searchPanel.add(availableOnlyBox, BorderLayout.EAST);

        add(searchPanel, BorderLayout.NORTH);

        driverModel = new DriverTableModel();
        driverTable = new JTable(driverModel);
        driverTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        driverTable.setRowHeight(28);
        driverTable.setShowGrid(true);
        driverTable.setGridColor(new Color(0x3E3E3E));
        driverTable.getColumnModel().getColumn(0).setPreferredWidth(200);
        driverTable.getColumnModel().getColumn(1).setPreferredWidth(110);

        driverTable.setDefaultRenderer(Object.class, new DriverTableCellRenderer());

        JScrollPane scrollPane = new JScrollPane(driverTable);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
        add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(6, 12, 12, 12));

        JButton okBtn = new JButton("Select");
        okBtn.addActionListener(e -> {
            int row = driverTable.getSelectedRow();
            if (row >= 0) {
                selectedDriver = filteredDrivers.get(row);
            }
            dispose();
        });

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());

        buttonPanel.add(okBtn);
        buttonPanel.add(cancelBtn);
        add(buttonPanel, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(okBtn);

        applyFilter();
    }

    public Driver showDialog() {
        setVisible(true);
        return selectedDriver;
    }

    private void applyFilter() {
        String query = searchField.getText().toLowerCase().trim();
        boolean availableOnly = availableOnlyBox.isSelected();

        filteredDrivers.clear();
        for (Driver d : allDrivers) {
            boolean busy = busyDriverIds.contains(d.getId());
            if (availableOnly && busy) continue;

            String name = (d.getFirstName() + " " + d.getLastName()).toLowerCase();
            if (!query.isEmpty() && !name.contains(query)) continue;

            filteredDrivers.add(d);
        }
        driverModel.fireTableDataChanged();
    }

    private boolean isBusy(Driver driver) {
        return busyDriverIds.contains(driver.getId());
    }

    private class DriverTableModel extends AbstractTableModel {
        @Override public int getRowCount() { return filteredDrivers.size(); }
        @Override public int getColumnCount() { return 2; }

        @Override
        public String getColumnName(int col) {
            return col == 0 ? "Name" : "Status";
        }

        @Override
        public Object getValueAt(int row, int col) {
            Driver d = filteredDrivers.get(row);
            if (col == 0) return d.getFirstName() + " " + d.getLastName();
            return isBusy(d) ? "In Trip" : "Available";
        }
    }

    private class DriverTableCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int col) {
            Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, col);
            if (!isSelected && row >= 0 && row < filteredDrivers.size()) {
                Driver d = filteredDrivers.get(row);
                if (isBusy(d)) {
                    c.setForeground(Color.GRAY);
                    c.setFont(c.getFont().deriveFont(Font.ITALIC));
                } else {
                    c.setForeground(null);
                    c.setFont(c.getFont().deriveFont(Font.PLAIN));
                }
            }
            return c;
        }
    }

}
