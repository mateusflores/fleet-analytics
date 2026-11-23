package com.unesp.desktopsimulator.view;

import java.util.Map;

/** Rótulos em português dos tipos de evento (os mesmos do frontend: alert-type-label.pipe.ts). */
final class AlertLabels {

    private static final Map<String, String> LABELS = Map.of(
            "RPM_LIMIT", "Limite de RPM atingido",
            "ENGINE_OVERHEAT", "Superaquecimento do motor",
            "LOW_FUEL", "Nível de combustível baixo",
            "LOW_BATTERY", "Bateria fraca",
            "LOW_OIL_PRESSURE", "Pressão do óleo baixa");

    private AlertLabels() {
    }

    static String of(String eventType) {
        return LABELS.getOrDefault(eventType, eventType);
    }
}
