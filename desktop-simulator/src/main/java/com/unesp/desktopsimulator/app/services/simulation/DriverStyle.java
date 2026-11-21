package com.unesp.desktopsimulator.app.services.simulation;

/** Estilo de condução: limites de aceleração/frenagem, quanto do limite de velocidade o motorista usa e quando troca de marcha. */
public enum DriverStyle {
    ECONOMIC("Econômico", 0.35, 0.8, 0.5, 0.95, 0, true),
    AGGRESSIVE("Agressivo", 0.8, 1.8, 1.2, 1.06, 250, false);

    private final String label;
    /** aceleração máxima desejada (m/s²) */
    final double maxAccel;
    /** desaceleração de conforto usada para antecipar curvas e paradas (m/s²) */
    final double comfortDecel;
    /** taxa máxima de variação da aceleração (m/s³) */
    final double jerk;
    /** fração do limite de velocidade que o motorista mira */
    final double speedFactor;
    /** rpm adicionais antes de trocar para cima */
    final int upshiftOffsetRpm;
    /** usa piloto automático em trechos estáveis de rodovia */
    final boolean usesCruiseControl;

    DriverStyle(String label, double maxAccel, double comfortDecel, double jerk, double speedFactor,
                int upshiftOffsetRpm, boolean usesCruiseControl) {
        this.label = label;
        this.maxAccel = maxAccel;
        this.comfortDecel = comfortDecel;
        this.jerk = jerk;
        this.speedFactor = speedFactor;
        this.upshiftOffsetRpm = upshiftOffsetRpm;
        this.usesCruiseControl = usesCruiseControl;
    }

    public String label() {
        return label;
    }
}
