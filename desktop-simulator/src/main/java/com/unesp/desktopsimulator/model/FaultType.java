package com.unesp.desktopsimulator.model;

/**
 * Falhas que o simulador sabe reproduzir. Cada uma altera um componente do modelo físico do veículo
 * (não a saída diretamente), de modo que os sinais afetados continuam coerentes entre si.
 */
public enum FaultType {
    /** Radiador obstruído / bomba d'água fraca: menor capacidade de rejeitar calor. */
    OVERHEATING("Superaquecimento"),
    /** Alternador falhando: a bateria passa a fornecer a corrente da carga elétrica. */
    BATTERY_DRAIN("Falha no alternador"),
    /** Vazamento no tanque/linha: o nível cai mais do que o consumo do motor explica. */
    FUEL_LEAK("Vazamento de combustível"),
    /** Falha de injeção/ignição: rotação irregular e perda de potência. */
    ERRATIC_RPM("Rotação irregular"),
    /** Receptor GPS degradado: posição ruidosa e baixa precisão. */
    GPS_DRIFT("GPS impreciso"),
    /** Bomba de óleo desgastada / nível baixo: pressão abaixo do normal para a rotação. */
    OIL_PRESSURE_DROP("Queda de pressão do óleo"),
    /** Vazamento no circuito de admissão: menos pressão de turbo e potência. */
    BOOST_LEAK("Vazamento no turbo"),
    /** Bico injetor desregulado: mais combustível para a mesma potência. */
    FUEL_OVERCONSUMPTION("Consumo excessivo");

    private final String label;

    FaultType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
