package com.unesp.fleetanalyticsapi.model.enums;

/** Ciclo de vida do insight: ativo, visto pelo usuário, descartado pelo usuário ou resolvido pelo motor (a condição deixou de existir). */
public enum InsightStatus {
    ACTIVE,
    ACKNOWLEDGED,
    DISMISSED,
    RESOLVED
}
