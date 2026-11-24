package com.unesp.fleetanalyticsapi.app.dtos;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.unesp.fleetanalyticsapi.model.enums.InsightScope;
import com.unesp.fleetanalyticsapi.model.enums.InsightSeverity;
import com.unesp.fleetanalyticsapi.model.enums.InsightStatus;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Insight para o frontend. {@code subjectName} é o apelido/placa do veículo ou o nome do motorista
 * ({@code null} para insights da frota); {@code evidence} é o objeto JSON gravado pelo motor.
 */
public record InsightSummary(
        UUID id,
        InsightScope scope,
        UUID subjectId,
        String subjectName,
        String category,
        InsightSeverity severity,
        String title,
        String description,
        ZonedDateTime periodStart,
        ZonedDateTime periodEnd,
        @JsonRawValue String evidence,
        InsightStatus status,
        UUID acknowledgedBy,
        ZonedDateTime acknowledgedAt,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
) {
}
