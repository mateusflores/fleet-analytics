package com.unesp.fleetanalyticsapi.app.services;

import com.unesp.fleetanalyticsapi.app.dtos.CreateTripRequest;
import com.unesp.fleetanalyticsapi.app.dtos.TripSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.UUID;

public interface TripService {

    TripSummary startTrip(CreateTripRequest request);

    TripSummary endTrip(UUID tripId, UUID currentUserId);

    TripSummary findById(UUID id);

    Page<TripSummary> search(UUID vehicleId, UUID driverId, Instant startAt,
                              Instant endAt, Boolean ongoingTrip, Pageable pageable);

    Page<TripSummary> findAll(Pageable pageable);

    TripSummary findActiveByVehicle(UUID vehicleId);

    TripSummary findActiveByDriver(UUID driverId);

}
