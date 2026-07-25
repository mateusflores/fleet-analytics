package com.unesp.fleetanalyticsapi.app.services;

import com.unesp.fleetanalyticsapi.app.dtos.CreateTripRequest;
import com.unesp.fleetanalyticsapi.app.dtos.TripSummary;
import com.unesp.fleetanalyticsapi.model.entities.Role;
import com.unesp.fleetanalyticsapi.model.entities.Trip;
import com.unesp.fleetanalyticsapi.model.entities.User;
import com.unesp.fleetanalyticsapi.model.entities.Vehicle;
import com.unesp.fleetanalyticsapi.model.repositories.TripRepository;
import com.unesp.fleetanalyticsapi.model.repositories.UserRepository;
import com.unesp.fleetanalyticsapi.model.repositories.VehicleRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripServiceImplTest {

    @Mock
    private TripRepository tripRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @InjectMocks
    private TripServiceImpl tripService;

    private UUID driverId;
    private UUID vehicleId;
    private User driver;
    private Vehicle vehicle;

    @BeforeEach
    void setUp() {
        driverId = UUID.randomUUID();
        vehicleId = UUID.randomUUID();

        driver = new User();
        driver.setId(driverId);
        driver.setUsername("john_doe");

        vehicle = new Vehicle();
        vehicle.setId(vehicleId);
        vehicle.setLicensePlate("ABC-1234");
    }

    // ---- startTrip ----

    @Test
    void shouldStartTripSuccessfully() {
        CreateTripRequest request = new CreateTripRequest(driverId, vehicleId);

        when(userRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle));
        when(tripRepository.existsByVehicleIdAndEndTimeIsNull(vehicleId)).thenReturn(false);
        when(tripRepository.existsByDriverIdAndEndTimeIsNull(driverId)).thenReturn(false);
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> {
            Trip t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        TripSummary result = tripService.startTrip(request);

        assertNotNull(result.id());
        assertEquals(driverId, result.driverId());
        assertEquals("john_doe", result.driverName());
        assertEquals(vehicleId, result.vehicleId());
        assertEquals("ABC-1234", result.vehiclePlate());
        assertNotNull(result.startTime());
        assertNull(result.endTime());
    }

    @Test
    void shouldThrowWhenDriverNotFoundOnStart() {
        CreateTripRequest request = new CreateTripRequest(driverId, vehicleId);

        when(userRepository.findById(driverId)).thenReturn(Optional.empty());

        EntityNotFoundException exception = assertThrows(
                EntityNotFoundException.class,
                () -> tripService.startTrip(request));
        assertTrue(exception.getMessage().contains(driverId.toString()));
    }

    @Test
    void shouldThrowWhenVehicleNotFoundOnStart() {
        CreateTripRequest request = new CreateTripRequest(driverId, vehicleId);

        when(userRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.empty());

        EntityNotFoundException exception = assertThrows(
                EntityNotFoundException.class,
                () -> tripService.startTrip(request));
        assertTrue(exception.getMessage().contains(vehicleId.toString()));
    }

    @Test
    void shouldThrowWhenVehicleAlreadyHasActiveTrip() {
        CreateTripRequest request = new CreateTripRequest(driverId, vehicleId);

        when(userRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle));
        when(tripRepository.existsByVehicleIdAndEndTimeIsNull(vehicleId)).thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> tripService.startTrip(request));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("Vehicle already has an active trip", exception.getReason());
    }

    @Test
    void shouldThrowWhenDriverAlreadyHasActiveTrip() {
        CreateTripRequest request = new CreateTripRequest(driverId, vehicleId);

        when(userRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle));
        when(tripRepository.existsByVehicleIdAndEndTimeIsNull(vehicleId)).thenReturn(false);
        when(tripRepository.existsByDriverIdAndEndTimeIsNull(driverId)).thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> tripService.startTrip(request));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("Driver already has an active trip", exception.getReason());
    }

    // ---- endTrip ----

    @Test
    void shouldEndTripSuccessfully() {
        UUID tripId = UUID.randomUUID();
        Trip trip = createTrip(tripId, null);

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(tripRepository.save(any(Trip.class))).thenReturn(trip);

        TripSummary result = tripService.endTrip(tripId, driverId);

        assertNotNull(result.endTime());

        ArgumentCaptor<Trip> captor = ArgumentCaptor.forClass(Trip.class);
        verify(tripRepository).save(captor.capture());
        assertNotNull(captor.getValue().getEndTime());
    }

    @Test
    void shouldEndTripAsAdmin() {
        UUID tripId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Trip trip = createTrip(tripId, null);

        Role adminRole = new Role();
        adminRole.setName("Admin");

        User admin = new User();
        admin.setId(adminId);
        admin.setRole(adminRole);

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(tripRepository.save(any(Trip.class))).thenReturn(trip);

        TripSummary result = tripService.endTrip(tripId, adminId);

        assertNotNull(result.endTime());
    }

    @Test
    void shouldThrowWhenEndingAlreadyEndedTrip() {
        UUID tripId = UUID.randomUUID();
        Trip trip = createTrip(tripId, ZonedDateTime.now());

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> tripService.endTrip(tripId, driverId));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Trip already ended", exception.getReason());
    }

    @Test
    void shouldThrowWhenUnauthorizedUserEndsTrip() {
        UUID tripId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Trip trip = createTrip(tripId, null);

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(userRepository.findById(otherUserId)).thenReturn(Optional.of(new User()));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> tripService.endTrip(tripId, otherUserId));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void shouldThrowWhenEndingNonExistentTrip() {
        UUID tripId = UUID.randomUUID();

        when(tripRepository.findById(tripId)).thenReturn(Optional.empty());

        EntityNotFoundException exception = assertThrows(
                EntityNotFoundException.class,
                () -> tripService.endTrip(tripId, driverId));

        assertTrue(exception.getMessage().contains(tripId.toString()));
    }

    // ---- findById ----

    @Test
    void shouldFindByIdSuccessfully() {
        UUID tripId = UUID.randomUUID();
        Trip trip = createTrip(tripId, ZonedDateTime.now());

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        TripSummary result = tripService.findById(tripId);

        assertEquals(tripId, result.id());
        assertEquals(driverId, result.driverId());
        assertEquals(vehicleId, result.vehicleId());
    }

    @Test
    void shouldThrowWhenTripNotFound() {
        UUID tripId = UUID.randomUUID();

        when(tripRepository.findById(tripId)).thenReturn(Optional.empty());

        EntityNotFoundException exception = assertThrows(
                EntityNotFoundException.class,
                () -> tripService.findById(tripId));

        assertTrue(exception.getMessage().contains(tripId.toString()));
    }

    // ---- findAll ----

    @Test
    void shouldFindAllPaginated() {
        Trip trip = createTrip(UUID.randomUUID(), ZonedDateTime.now());
        Pageable pageable = PageRequest.of(0, 20);
        Page<Trip> page = new PageImpl<>(List.of(trip), pageable, 1);

        when(tripRepository.findAll(pageable)).thenReturn(page);

        Page<TripSummary> result = tripService.findAll(pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(trip.getId(), result.getContent().getFirst().id());
    }

    // ---- findActiveByVehicle ----

    @Test
    void shouldFindActiveByVehicleSuccessfully() {
        UUID tripId = UUID.randomUUID();
        Trip trip = createTrip(tripId, null);

        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle));
        when(tripRepository.findOngoingTripByVehicle(vehicleId))
                .thenReturn(Optional.of(trip));

        TripSummary result = tripService.findActiveByVehicle(vehicleId);

        assertEquals(tripId, result.id());
        assertNull(result.endTime());
    }

    @Test
    void shouldThrowWhenNoActiveTripForVehicle() {
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle));
        when(tripRepository.findOngoingTripByVehicle(vehicleId))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> tripService.findActiveByVehicle(vehicleId));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void shouldThrowWhenVehicleNotFoundForActiveTrip() {
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.empty());

        EntityNotFoundException exception = assertThrows(
                EntityNotFoundException.class,
                () -> tripService.findActiveByVehicle(vehicleId));

        assertTrue(exception.getMessage().contains(vehicleId.toString()));
        verify(tripRepository, never()).findOngoingTripByVehicle(any());
    }

    private Trip createTrip(UUID tripId, ZonedDateTime endTime) {
        Trip trip = new Trip();
        trip.setId(tripId);
        trip.setDriver(driver);
        trip.setVehicle(vehicle);
        trip.setStartTime(ZonedDateTime.now().minusHours(2));
        trip.setEndTime(endTime);
        return trip;
    }
}
