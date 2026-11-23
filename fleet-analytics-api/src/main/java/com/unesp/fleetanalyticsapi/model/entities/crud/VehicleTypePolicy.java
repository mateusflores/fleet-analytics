package com.unesp.fleetanalyticsapi.model.entities.crud;

import com.unesp.fleetanalyticsapi.model.enums.VehicleType;
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

import java.time.ZonedDateTime;

/** Faixa econômica de rotação por tipo de veículo (acima dela o tempo conta como rotação excessiva). */
@Entity
@Table(name = "vehicle_type_policy", schema = "fleet")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleTypePolicy {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type")
    private VehicleType vehicleType;

    @Column(name = "economy_rpm_max", nullable = false)
    private Integer economyRpmMax;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;
}
