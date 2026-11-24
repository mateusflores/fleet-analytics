package com.unesp.desktopsimulator.view.components;

import javax.swing.*;
import java.awt.*;

/** Painel com fundo arredondado e borda opcional (usado em cartões e blocos). */
public class RoundedPanel extends JPanel {

    private final int arc;
    private Color fill;
    private Color stroke;
    private float strokeWidth = 1f;

    public RoundedPanel(LayoutManager layout, int arc) {
        super(layout);
        this.arc = arc;
        setOpaque(false);
        fill = Theme.card();
        stroke = Theme.border();
    }

    public void setFill(Color fill) {
        this.fill = fill;
        repaint();
    }

    public void setStroke(Color stroke, float width) {
        this.stroke = stroke;
        this.strokeWidth = width;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int inset = (int) Math.ceil(strokeWidth / 2);
        g2.setColor(fill);
        g2.fillRoundRect(inset, inset, getWidth() - 2 * inset - 1, getHeight() - 2 * inset - 1, arc, arc);
        if (stroke != null) {
            g2.setColor(stroke);
            g2.setStroke(new BasicStroke(strokeWidth));
            g2.drawRoundRect(inset, inset, getWidth() - 2 * inset - 1, getHeight() - 2 * inset - 1, arc, arc);
        }
        g2.dispose();
        super.paintComponent(g);
    }
}
