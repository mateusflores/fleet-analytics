package com.unesp.desktopsimulator.view;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.swing.*;
import java.awt.*;

public class ErrorDialog extends JDialog {

    private static final ObjectMapper mapper = new ObjectMapper();

    public ErrorDialog(Frame owner, String title, String rawMessage) {
        super(owner, title, true);
        setLayout(new BorderLayout(10, 10));

        String cleanMessage = parseMessage(rawMessage);

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(20, 25, 15, 25));

        JLabel iconLabel = new JLabel(UIManager.getIcon("OptionPane.errorIcon"));
        iconLabel.setVerticalAlignment(SwingConstants.TOP);
        content.add(iconLabel, BorderLayout.WEST);

        JPanel textPanel = new JPanel(new BorderLayout(0, 10));

        JLabel messageLabel = new JLabel("<html><body style='width:280px'>" +
                escapeHtml(cleanMessage) + "</body></html>");
        textPanel.add(messageLabel, BorderLayout.CENTER);

        if (!rawMessage.equals(cleanMessage)) {
            JTextArea details = new JTextArea(rawMessage);
            details.setEditable(false);
            details.setFont(new Font("Monospaced", Font.PLAIN, 11));
            details.setBackground(UIManager.getColor("Panel.background"));
            JScrollPane detailsScroll = new JScrollPane(details);
            detailsScroll.setPreferredSize(new Dimension(350, 80));
            textPanel.add(detailsScroll, BorderLayout.SOUTH);
        }

        content.add(textPanel, BorderLayout.CENTER);
        add(content, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okBtn = new JButton("OK");
        okBtn.addActionListener(e -> dispose());
        buttonPanel.add(okBtn);
        add(buttonPanel, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(okBtn);
        pack();
        setLocationRelativeTo(owner);
        setResizable(false);
    }

    private static String parseMessage(String raw) {
        if (raw == null || raw.isBlank()) return "An unexpected error occurred.";
        try {
            JsonNode root = mapper.readTree(raw);
            if (root.has("message")) return root.get("message").asText();
            if (root.has("error")) return root.get("error").asText();
            if (root.has("detail")) return root.get("detail").asText();
            return raw;
        } catch (Exception e) {
            return raw;
        }
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\n", "<br>");
    }

    public static void show(Frame owner, String title, String rawMessage) {
        SwingUtilities.invokeLater(() -> {
            ErrorDialog dialog = new ErrorDialog(owner, title, rawMessage);
            dialog.setVisible(true);
        });
    }

}
