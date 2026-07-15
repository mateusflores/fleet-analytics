package com.unesp.fleetanalyticsapi.app.services;

import com.unesp.fleetanalyticsapi.app.dtos.CreateTripRequest;
import com.unesp.fleetanalyticsapi.app.dtos.TripSummary;

import java.util.List;
import java.util.UUID;

public interface TripService {

    TripSummary startTrip(CreateTripRequest request);

    TripSummary endTrip(UUID tripId, UUID currentUserId);

    TripSummary findById(UUID id);

    List<TripSummary> findAll();

    TripSummary findActiveByVehicle(UUID vehicleId);
}
