package com.unesp.fleetanalyticsapi.app.services;

import com.unesp.fleetanalyticsapi.app.dtos.CreateTripRequest;
import com.unesp.fleetanalyticsapi.app.dtos.TripSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface TripService {

    TripSummary startTrip(CreateTripRequest request);

    TripSummary endTrip(UUID tripId, UUID currentUserId);

    TripSummary findById(UUID id);

    Page<TripSummary> findAll(Pageable pageable);

    TripSummary findActiveByVehicle(UUID vehicleId);
}
