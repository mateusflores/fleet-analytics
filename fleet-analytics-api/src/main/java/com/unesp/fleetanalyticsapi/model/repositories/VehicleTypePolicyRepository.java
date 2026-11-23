package com.unesp.fleetanalyticsapi.model.repositories;

import com.unesp.fleetanalyticsapi.model.entities.crud.VehicleTypePolicy;
import com.unesp.fleetanalyticsapi.model.enums.VehicleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VehicleTypePolicyRepository extends JpaRepository<VehicleTypePolicy, VehicleType> {
}
