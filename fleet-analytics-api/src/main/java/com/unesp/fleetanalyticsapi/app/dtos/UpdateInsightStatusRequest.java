package com.unesp.fleetanalyticsapi.app.dtos;

import com.unesp.fleetanalyticsapi.model.enums.InsightStatus;
import jakarta.validation.constraints.NotNull;

/** Mudança de status feita pelo usuário: {@code ACKNOWLEDGED} (visto) ou {@code DISMISSED} (descartado). */
public record UpdateInsightStatusRequest(@NotNull(message = "status is required") InsightStatus status) {
}
