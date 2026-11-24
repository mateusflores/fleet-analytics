package com.unesp.fleetanalyticsapi.model.repositories;

import com.unesp.fleetanalyticsapi.model.entities.crud.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, UUID>, JpaSpecificationExecutor<Vehicle> {

    boolean existsBySerialNumber(String serialNumber);

    boolean existsByLicensePlate(String licensePlate);

    boolean existsBySerialNumberAndIdNot(String serialNumber, UUID id);

    boolean existsByLicensePlateAndIdNot(String licensePlate, UUID id);
}
