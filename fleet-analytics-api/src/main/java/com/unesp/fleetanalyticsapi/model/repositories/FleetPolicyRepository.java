package com.unesp.fleetanalyticsapi.model.repositories;

import com.unesp.fleetanalyticsapi.model.entities.crud.FleetPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FleetPolicyRepository extends JpaRepository<FleetPolicy, Short> {
}
