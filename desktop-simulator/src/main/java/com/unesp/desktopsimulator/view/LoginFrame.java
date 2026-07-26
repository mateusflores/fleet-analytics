package com.unesp.desktopsimulator.view;

import javax.swing.*;
import java.awt.*;

public class LoginFrame extends JFrame {

    private final JTextField usernameField;
    private final JPasswordField passwordField;
    private final JLabel errorLabel;
    private volatile boolean success;
    private volatile String errorMessage;

    public LoginFrame() {
        setTitle("Fleet Analytics - Login");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createEmptyBorder(40, 50, 40, 50));

        JLabel title = new JLabel("Fleet Analytics");
        title.setFont(new Font("SansSerif", Font.BOLD, 28));
        title.setHorizontalAlignment(SwingConstants.CENTER);
        title.putClientProperty("FlatLaf.styleClass", "h1");

        JLabel subtitle = new JLabel("Desktop Simulator");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 14));
        subtitle.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel header = new JPanel(new GridLayout(2, 1, 0, 5));
        header.add(title);
        header.add(subtitle);
        content.add(header, BorderLayout.NORTH);

        JPanel fields = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 0, 8, 0);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridwidth = 1;

        gbc.gridx = 0;
        gbc.gridy = 0;
        fields.add(new JLabel("Username"), gbc);

        usernameField = new JTextField(20);
        usernameField.putClientProperty("JTextField.placeholderText", "Enter your username");
        gbc.gridy = 1;
        fields.add(usernameField, gbc);

        gbc.gridy = 2;
        fields.add(new JLabel("Password"), gbc);

        passwordField = new JPasswordField(20);
        passwordField.putClientProperty("JTextField.placeholderText", "Enter your password");
        gbc.gridy = 3;
        fields.add(passwordField, gbc);

        errorLabel = new JLabel(" ");
        errorLabel.setForeground(new Color(0xC0392B));
        errorLabel.setHorizontalAlignment(SwingConstants.CENTER);
        gbc.gridy = 4;
        gbc.insets = new Insets(4, 0, 4, 0);
        fields.add(errorLabel, gbc);

        content.add(fields, BorderLayout.CENTER);

        JButton loginBtn = new JButton("Connect");
        loginBtn.setVerticalAlignment(SwingConstants.CENTER);
        loginBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
        loginBtn.addActionListener(e -> fireLogin());
        loginBtn.putClientProperty("JButton.buttonType", "default");

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.add(loginBtn);
        content.add(buttonPanel, BorderLayout.SOUTH);

        add(content);
        getRootPane().setDefaultButton(loginBtn);
        pack();
        setLocationRelativeTo(null);
    }

    public String getUsername() {
        return usernameField.getText().trim();
    }

    public String getPassword() {
        return new String(passwordField.getPassword());
    }

    public void setError(String message) {
        errorLabel.setText(message);
        usernameField.requestFocus();
    }

    public void clearError() {
        errorLabel.setText(" ");
    }

    public void markLoginSuccess() {
        success = true;
    }

    public boolean isLoginSuccess() {
        return success;
    }

    private void fireLogin() {
        clearError();
        SwingUtilities.invokeLater(() -> {
            firePropertyChange("login", false, true);
        });
    }

}
