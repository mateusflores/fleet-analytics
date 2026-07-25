package com.unesp.fleetanalyticsapi.app.services;

import com.unesp.fleetanalyticsapi.app.dtos.CreateTripRequest;
import com.unesp.fleetanalyticsapi.app.dtos.TripSummary;
import com.unesp.fleetanalyticsapi.model.entities.Trip;
import com.unesp.fleetanalyticsapi.model.entities.User;
import com.unesp.fleetanalyticsapi.model.entities.Vehicle;
import com.unesp.fleetanalyticsapi.model.repositories.TripRepository;
import com.unesp.fleetanalyticsapi.model.repositories.UserRepository;
import com.unesp.fleetanalyticsapi.model.repositories.VehicleRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TripServiceImpl implements TripService {

    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;

    @Override
    @Transactional      
    public TripSummary startTrip(CreateTripRequest request) {
        User driver = userRepository.findById(request.driverId())
                .orElseThrow(() -> new EntityNotFoundException("Driver not found: " + request.driverId()));

        Vehicle vehicle = vehicleRepository.findById(request.vehicleId())
                .orElseThrow(() -> new EntityNotFoundException("Vehicle not found: " + request.vehicleId()));

        if (tripRepository.existsByVehicleIdAndEndTimeIsNull(request.vehicleId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Vehicle already has an active trip");
        }

        if (tripRepository.existsByDriverIdAndEndTimeIsNull(request.driverId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Driver already has an active trip");
        }

        Trip trip = Trip.builder()
                .driver(driver)
                .vehicle(vehicle)
                .startTime(ZonedDateTime.now())
                .build();

        trip = tripRepository.save(trip);
        return toSummary(trip);
    }

    @Override
    @Transactional
    public TripSummary endTrip(UUID tripId, UUID currentUserId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new EntityNotFoundException("Trip not found: " + tripId));

        if (trip.getEndTime() != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Trip already ended");
        }

        boolean isDriver = trip.getDriver().getId().equals(currentUserId);
        boolean isAdmin = isAdminUser(currentUserId);

        if (!isDriver && !isAdmin) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Only the driver who started the trip or an admin can end it");
        }

        trip.setEndTime(ZonedDateTime.now());
        trip = tripRepository.save(trip);
        return toSummary(trip);
    }

    @Override
    @Transactional(readOnly = true)
    public TripSummary findById(UUID id) {
        return tripRepository.findById(id)
                .map(this::toSummary)
                .orElseThrow(() -> new EntityNotFoundException("Trip not found: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TripSummary> findAll(Pageable pageable) {
        return tripRepository.findAll(pageable)
                .map(this::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public TripSummary findActiveByVehicle(UUID vehicleId) {
        vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new EntityNotFoundException("Vehicle not found: " + vehicleId));

        return tripRepository.findOngoingTripByVehicle(vehicleId)
                .map(this::toSummary)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No active trip found for vehicle: " + vehicleId));
    }

    private boolean isAdminUser(UUID userId) {
        return userRepository.findById(userId)
                .map(user -> user.getRole() != null && "Admin".equals(user.getRole().getName()))
                .orElse(false);
    }

    private TripSummary toSummary(Trip trip) {
        return new TripSummary(
                trip.getId(),
                trip.getDriver().getId(),
                trip.getDriver().getUsername(),
                trip.getVehicle().getId(),
                trip.getVehicle().getLicensePlate(),
                trip.getStartTime(),
                trip.getEndTime()
        );
    }
}
