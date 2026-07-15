package com.unesp.fleetanalyticsapi.model.repositories;

import com.unesp.fleetanalyticsapi.model.entities.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TripRepository extends JpaRepository<Trip, UUID> {

    Optional<Trip> findByVehicleIdAndEndTimeIsNull(UUID vehicleId);

    boolean existsByVehicleIdAndEndTimeIsNull(UUID vehicleId);

    List<Trip> findByDriverIdOrderByStartTimeDesc(UUID driverId);

    List<Trip> findByVehicleIdOrderByStartTimeDesc(UUID vehicleId);
}
