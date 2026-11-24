package com.unesp.desktopsimulator.view.components;

import javax.swing.*;
import java.awt.*;

/** Etiqueta arredondada colorida (status, falhas). */
public class Chip extends JLabel {

    private Color color = Theme.IDLE;

    public Chip() {
        setFont(Theme.font(Font.BOLD, 11f));
        setBorder(BorderFactory.createEmptyBorder(3, 9, 3, 9));
        setOpaque(false);
    }

    public void set(String text, Color color) {
        setText(text);
        this.color = color;
        setForeground(Theme.blend(color, Color.WHITE, 0.35));
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(Theme.withAlpha(color, 60));
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
        g2.dispose();
        super.paintComponent(g);
    }
}
