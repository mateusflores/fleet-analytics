package com.unesp.fleetanalyticsapi.model.entities.crud;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Limites da frota usados no cálculo das métricas de viagem e dos insights (V13).
 * Há uma única linha ({@link #SINGLETON_ID}): a plataforma atende uma empresa por instalação.
 */
@Entity
@Table(name = "fleet_policy", schema = "fleet")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FleetPolicy {

    public static final short SINGLETON_ID = 1;

    @Id
    private Short id;

    @Column(name = "speed_limit_kmh", nullable = false)
    private Double speedLimitKmh;

    @Column(name = "harsh_accel_ms2", nullable = false)
    private Double harshAccelMs2;

    @Column(name = "harsh_brake_ms2", nullable = false)
    private Double harshBrakeMs2;

    @Column(name = "idle_threshold_s", nullable = false)
    private Integer idleThresholdS;

    @Column(name = "fuel_price_per_liter", nullable = false, precision = 10, scale = 2)
    private BigDecimal fuelPricePerLiter;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;
}
