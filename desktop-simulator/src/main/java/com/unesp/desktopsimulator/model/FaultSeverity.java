package com.unesp.desktopsimulator.model;

/** Intensidade de uma falha; os mesmos níveis (0,25 / 0,5 / 1,0) usados na avaliação do serviço de ML. */
public enum FaultSeverity {
    LIGHT("Leve", 0.25),
    MODERATE("Média", 0.5),
    SEVERE("Grave", 1.0);

    private final String label;
    private final double factor;

    FaultSeverity(String label, double factor) {
        this.label = label;
        this.factor = factor;
    }

    public String label() {
        return label;
    }

    public double factor() {
        return factor;
    }
}
