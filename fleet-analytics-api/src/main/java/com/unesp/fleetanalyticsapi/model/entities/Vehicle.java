package com.unesp.fleetanalyticsapi.model.entities;

import com.unesp.fleetanalyticsapi.model.enums.FuelType;
import com.unesp.fleetanalyticsapi.model.enums.VehicleType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "vehicles", schema = "fleet")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "serial_number", unique = true)
    private String serialNumber;

    @Column(name = "license_plate", unique = true)
    private String licensePlate;

    private String brand;

    private String model;

    private Integer year;

    private String color;

    @Column(name = "mileage_km", columnDefinition = "NUMERIC(16, 3)")
    private BigDecimal mileageKm;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false)
    private VehicleType vehicleType;

    @Column(name = "max_load_kg")
    private BigDecimal maxLoadKg;

    @Enumerated(EnumType.STRING)
    @Column(name = "fuel_type")
    private FuelType fuelType;

    private String nickname;

    private String renavam;

    @Column(name = "is_active", nullable = false)
    private Boolean active;

    @CreationTimestamp
    @Column(name = "created_at")
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;
}
