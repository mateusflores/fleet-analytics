package com.unesp.fleetanalyticsapi.model.entities.telemetry;

import com.unesp.fleetanalyticsapi.model.enums.InsightScope;
import com.unesp.fleetanalyticsapi.model.enums.InsightSeverity;
import com.unesp.fleetanalyticsapi.model.enums.InsightStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Insight histórico gerado pelo motor de insights do ml-service (V13). A API só lê e muda o status
 * (visto/descartado); criação, atualização dos números e resolução são feitas pelo motor.
 */
@Entity
@Table(name = "insights", schema = "telemetry")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Insight {

    @Id
    private UUID id;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InsightScope scope;

    @Column(name = "subject_id")
    private UUID subjectId;

    @Column(nullable = false)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InsightSeverity severity;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "period_start")
    private ZonedDateTime periodStart;

    @Column(name = "period_end")
    private ZonedDateTime periodEnd;

    /** JSON gravado pelo motor (números, linha de base, teste); repassado sem conversão. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String evidence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InsightStatus status;

    @Column(name = "acknowledged_by")
    private UUID acknowledgedBy;

    @Column(name = "acknowledged_at")
    private ZonedDateTime acknowledgedAt;

    @Column(name = "dedup_key", nullable = false)
    private String dedupKey;
}
