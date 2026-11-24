package com.unesp.desktopsimulator.view.components;

import javax.swing.*;
import java.awt.*;

/** Cores e fontes compartilhadas. Acento = azul da plataforma web (#1F63F2). */
public final class Theme {

    public static final Color ACCENT = new Color(0x1F63F2);
    public static final Color OK = new Color(0x2FB36D);
    public static final Color WARN = new Color(0xF2A93B);
    public static final Color CRITICAL = new Color(0xE5484D);
    public static final Color IDLE = new Color(0x8A9099);
    public static final Color EXTERNAL = new Color(0x9B6CF0);

    private Theme() {
    }

    public static Color surface() {
        return UIManager.getColor("Panel.background");
    }

    /** Fundo de cartão: um pouco mais claro que o painel. */
    public static Color card() {
        return blend(surface(), Color.WHITE, 0.05);
    }

    public static Color cardHover() {
        return blend(surface(), Color.WHITE, 0.09);
    }

    public static Color border() {
        return blend(surface(), Color.WHITE, 0.14);
    }

    public static Color muted() {
        Color c = UIManager.getColor("Label.disabledForeground");
        return c != null ? c : IDLE;
    }

    public static Color levelColor(int level) {
        return switch (level) {
            case 2 -> CRITICAL;
            case 1 -> WARN;
            default -> OK;
        };
    }

    public static Font font(int style, float size) {
        return UIManager.getFont("Label.font").deriveFont(style, size);
    }

    public static Color blend(Color a, Color b, double t) {
        return new Color(
                (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    public static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    /** Rótulo pequeno em caixa alta para títulos de seção. */
    public static JLabel sectionTitle(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(font(Font.BOLD, 11f));
        l.setForeground(muted());
        return l;
    }
}
