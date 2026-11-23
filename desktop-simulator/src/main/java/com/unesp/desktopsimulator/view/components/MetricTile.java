package com.unesp.desktopsimulator.view.components;

import javax.swing.*;
import java.awt.*;

/** Bloco de uma métrica: nome, valor atual com unidade, indicador de nível e minigráfico. */
public class MetricTile extends RoundedPanel {

    private final JLabel value = new JLabel("–");
    private final JLabel unit = new JLabel();
    private final JLabel dot = new JLabel("●");
    private final Sparkline spark = new Sparkline();

    public MetricTile(String name, String unitText, double minSpan) {
        super(new BorderLayout(0, 2), 12);
        setBorder(BorderFactory.createEmptyBorder(8, 10, 6, 10));

        JLabel title = new JLabel(name);
        title.setFont(Theme.font(Font.PLAIN, 11.5f));
        title.setForeground(Theme.muted());
        dot.setFont(Theme.font(Font.PLAIN, 10f));
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(title, BorderLayout.WEST);
        top.add(dot, BorderLayout.EAST);

        value.setFont(Theme.font(Font.BOLD, 19f));
        unit.setText(unitText);
        unit.setFont(Theme.font(Font.PLAIN, 11.5f));
        unit.setForeground(Theme.muted());
        JPanel mid = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        mid.setOpaque(false);
        mid.add(value);
        mid.add(unit);
        ((FlowLayout) mid.getLayout()).setAlignOnBaseline(true);

        spark.setMinSpan(minSpan);
        spark.setPreferredSize(new Dimension(100, 26));

        add(top, BorderLayout.NORTH);
        add(mid, BorderLayout.CENTER);
        add(spark, BorderLayout.SOUTH);
        setPreferredSize(new Dimension(160, 92));
    }

    /** @param level -1 = sem nível, 0 normal, 1 atenção, 2 crítico */
    public void update(String text, int level, double[] series) {
        value.setText(text);
        Color c = level < 0 ? Theme.ACCENT : Theme.levelColor(level);
        dot.setForeground(level < 0 ? Theme.withAlpha(Theme.muted(), 0) : c);
        value.setForeground(level >= 1 ? c : UIManager.getColor("Label.foreground"));
        spark.setData(series, level >= 1 ? c : Theme.ACCENT);
    }

    public void clear() {
        update("–", -1, new double[0]);
    }
}
