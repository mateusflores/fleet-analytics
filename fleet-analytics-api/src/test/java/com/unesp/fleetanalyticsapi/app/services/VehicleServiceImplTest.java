package com.unesp.fleetanalyticsapi.app.services;

import com.unesp.fleetanalyticsapi.app.dtos.CreateVehicleRequest;
import com.unesp.fleetanalyticsapi.app.dtos.UpdateVehicleRequest;
import com.unesp.fleetanalyticsapi.app.dtos.VehicleSummary;
import com.unesp.fleetanalyticsapi.model.entities.crud.Vehicle;
import com.unesp.fleetanalyticsapi.model.enums.FuelType;
import com.unesp.fleetanalyticsapi.model.enums.VehicleType;
import com.unesp.fleetanalyticsapi.model.repositories.VehicleRepository;
import jakarta.persistence.EntityNotFoundException;
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
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleServiceImplTest {

    @Mock
    private VehicleRepository vehicleRepository;

    @InjectMocks
    private VehicleServiceImpl vehicleService;

    @Test
    void shouldCreateVehicleWithFuelTypeEnum() {
        CreateVehicleRequest request = new CreateVehicleRequest(
                "SN123", "ABC-1234", "Volvo", "FH 540", 2023,
                "Blue", BigDecimal.valueOf(50000), VehicleType.RIGID_TRUCK,
                BigDecimal.valueOf(25000), FuelType.DIESEL, "Trucky", "12345678901");

        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(inv -> {
            Vehicle v = inv.getArgument(0);
            v.setId(UUID.randomUUID());
            return v;
        });

        VehicleSummary result = vehicleService.create(request);

        assertEquals(FuelType.DIESEL, result.fuelType());
        assertEquals(VehicleType.RIGID_TRUCK, result.vehicleType());

        ArgumentCaptor<Vehicle> captor = ArgumentCaptor.forClass(Vehicle.class);
        verify(vehicleRepository).save(captor.capture());
        assertEquals(FuelType.DIESEL, captor.getValue().getFuelType());
        assertTrue(captor.getValue().getActive());
    }

    @Test
    void shouldFindVehicleById() {
        UUID id = UUID.randomUUID();
        Vehicle vehicle = createTestVehicle(id, VehicleType.PASSAGER_VEHICLE, FuelType.FLEX);

        when(vehicleRepository.findById(id)).thenReturn(Optional.of(vehicle));

        VehicleSummary result = vehicleService.findById(id);

        assertEquals(id, result.id());
        assertEquals(FuelType.FLEX, result.fuelType());
    }

    @Test
    void shouldThrowWhenVehicleNotFound() {
        UUID id = UUID.randomUUID();
        when(vehicleRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> vehicleService.findById(id));
    }

    @Test
    void shouldUpdateFuelType() {
        UUID id = UUID.randomUUID();
        Vehicle vehicle = createTestVehicle(id, VehicleType.RIGID_TRUCK, FuelType.DIESEL);

        UpdateVehicleRequest request = new UpdateVehicleRequest(
                null, null, null, null, null, null, null,
                null, null, FuelType.ELECTRIC, null, null, true);

        when(vehicleRepository.findById(id)).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.save(any(Vehicle.class))).thenReturn(vehicle);

        VehicleSummary result = vehicleService.update(id, request);

        assertEquals(FuelType.ELECTRIC, result.fuelType());

        ArgumentCaptor<Vehicle> captor = ArgumentCaptor.forClass(Vehicle.class);
        verify(vehicleRepository).save(captor.capture());
        assertEquals(FuelType.ELECTRIC, captor.getValue().getFuelType());
    }

    @Test
    void shouldUpdateActiveField() {
        UUID id = UUID.randomUUID();
        Vehicle vehicle = createTestVehicle(id, VehicleType.RIGID_TRUCK, FuelType.DIESEL);
        vehicle.setActive(true);

        UpdateVehicleRequest request = new UpdateVehicleRequest(
                null, null, null, null, null, null, null,
                null, null, null, null, null, false);

        when(vehicleRepository.findById(id)).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.save(any(Vehicle.class))).thenReturn(vehicle);

        VehicleSummary result = vehicleService.update(id, request);

        ArgumentCaptor<Vehicle> captor = ArgumentCaptor.forClass(Vehicle.class);
        verify(vehicleRepository).save(captor.capture());
        assertEquals(false, captor.getValue().getActive());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldSearchWithSpecification() {
        UUID id = UUID.randomUUID();
        Vehicle vehicle = createTestVehicle(id, VehicleType.RIGID_TRUCK, FuelType.DIESEL);
        Pageable pageable = PageRequest.of(0, 20);
        Page<Vehicle> page = new PageImpl<>(List.of(vehicle), pageable, 1);

        when(vehicleRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);

        Page<VehicleSummary> result = vehicleService.search(
                "SN", "ABC", "Volvo", "FH", 2023, "Blue", "Trucky",
                VehicleType.RIGID_TRUCK, FuelType.DIESEL, "123",
                pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(FuelType.DIESEL, result.getContent().getFirst().fuelType());
        verify(vehicleRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    private Vehicle createTestVehicle(UUID id, VehicleType vehicleType, FuelType fuelType) {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(id);
        vehicle.setSerialNumber("SN-001");
        vehicle.setLicensePlate("ABC-1234");
        vehicle.setBrand("Volvo");
        vehicle.setModel("FH 540");
        vehicle.setYear(2023);
        vehicle.setColor("Blue");
        vehicle.setMileageKm(BigDecimal.valueOf(50000));
        vehicle.setVehicleType(vehicleType);
        vehicle.setMaxLoadKg(BigDecimal.valueOf(25000));
        vehicle.setFuelType(fuelType);
        vehicle.setNickname("Trucky");
        vehicle.setRenavam("12345678901");
        vehicle.setActive(true);
        return vehicle;
    }
}
