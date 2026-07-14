package com.unesp.fleetanalyticsapi.model.entities;

import com.unesp.fleetanalyticsapi.model.enums.VehicleType;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

//@Entity
//@Table(name = "vehicles", schema = "fleet")
public class Vehicle {
/*
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "serial_number")
    private String serialNumber;

    private String nickname;

    private String code;

    private Integer year;

    @ManyToMany(mappedBy = "vehicles")
    private List<Driver> authorizedDrivers;

    @Column(name = "mileage_km", columnDefinition = "NUMERIC(16, 3)")
    private BigDecimal mileageKm;

    @Column(name = "is_active", nullable = false)
    private Boolean active;

    @CreationTimestamp
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    private ZonedDateTime updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false)
    private VehicleType vehicleType;

 */
}
