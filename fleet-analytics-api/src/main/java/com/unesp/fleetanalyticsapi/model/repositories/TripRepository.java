package com.unesp.fleetanalyticsapi.model.repositories;

import com.unesp.fleetanalyticsapi.model.entities.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TripRepository extends JpaRepository<Trip, UUID> {

    @Query("""
            SELECT t
            FROM Trip t
            LEFT JOIN FETCH t.vehicle v
            LEFT JOIN FETCH t.driver
            WHERE v.id = :vehicleId AND t.endTime IS NULL
            """)
    Optional<Trip> findOngoingTripByVehicle(@Param("vehicleId") UUID vehicleId);

    boolean existsByVehicleIdAndEndTimeIsNull(UUID vehicleId);

    boolean existsByDriverIdAndEndTimeIsNull(UUID driverId);

    @Query("""
            SELECT t
            FROM Trip t
            LEFT JOIN FETCH t.vehicle
            LEFT JOIN FETCH t.driver d
            WHERE d.id = :driverId
            ORDER BY t.startTime DESC
            """)
    List<Trip> findByDriverIdOrderByStartTimeDesc(@Param("driverId") UUID driverId);

    @Query("""
            SELECT t
            FROM Trip t
            LEFT JOIN FETCH t.vehicle v
            LEFT JOIN FETCH t.driver
            WHERE v.id = :vehicleId
            ORDER BY t.startTime DESC
            """)
    List<Trip> findByVehicleIdOrderByStartTimeDesc(@Param("vehicleId") UUID vehicleId);
}
