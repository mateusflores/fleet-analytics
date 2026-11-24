package com.unesp.desktopsimulator.view.components;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;

/** Minigráfico da série recente de um sinal (escala automática, com área preenchida). */
public class Sparkline extends JComponent {

    private double[] values = new double[0];
    private Color color = Theme.ACCENT;
    private double minSpan = 1e-6;

    public Sparkline() {
        setPreferredSize(new Dimension(120, 28));
        setOpaque(false);
    }

    /** Amplitude mínima do eixo, para que ruído pequeno não pareça uma grande oscilação. */
    public void setMinSpan(double minSpan) {
        this.minSpan = minSpan;
    }

    public void setData(double[] values, Color color) {
        this.values = values;
        this.color = color;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        int n = values.length;
        if (n < 2) return;
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (double v : values) {
            if (Double.isNaN(v)) continue;
            lo = Math.min(lo, v);
            hi = Math.max(hi, v);
        }
        if (lo > hi) return;
        if (hi - lo < minSpan) {
            double mid = (hi + lo) / 2;
            lo = mid - minSpan / 2;
            hi = mid + minSpan / 2;
        }
        int w = getWidth(), h = getHeight();
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Path2D line = new Path2D.Double();
        for (int i = 0; i < n; i++) {
            double x = (double) i / (n - 1) * (w - 2) + 1;
            double y = h - 2 - (values[i] - lo) / (hi - lo) * (h - 4);
            if (i == 0) line.moveTo(x, y);
            else line.lineTo(x, y);
        }
        Path2D area = new Path2D.Double(line);
        area.lineTo(w - 1, h);
        area.lineTo(1, h);
        area.closePath();
        g2.setColor(Theme.withAlpha(color, 40));
        g2.fill(area);
        g2.setColor(color);
        g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(line);
        g2.dispose();
    }
}
