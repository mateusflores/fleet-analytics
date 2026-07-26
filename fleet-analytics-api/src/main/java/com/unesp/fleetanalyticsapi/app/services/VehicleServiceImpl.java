package com.unesp.fleetanalyticsapi.app.services;

import com.unesp.fleetanalyticsapi.app.dtos.CreateVehicleRequest;
import com.unesp.fleetanalyticsapi.app.dtos.UpdateVehicleRequest;
import com.unesp.fleetanalyticsapi.app.dtos.VehicleSummary;
import com.unesp.fleetanalyticsapi.model.entities.crud.Vehicle;
import com.unesp.fleetanalyticsapi.model.enums.FuelType;
import com.unesp.fleetanalyticsapi.model.enums.VehicleType;
import com.unesp.fleetanalyticsapi.model.repositories.VehicleRepository;
import com.unesp.fleetanalyticsapi.model.repositories.specifications.VehicleSpecification;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VehicleServiceImpl implements VehicleService {

    private final VehicleRepository vehicleRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<VehicleSummary> findAll(Pageable pageable) {
        return vehicleRepository.findAll(pageable)
                .map(this::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public VehicleSummary findById(UUID id) {
        return vehicleRepository.findById(id)
                .map(this::toSummary)
                .orElseThrow(() -> new EntityNotFoundException("Vehicle not found: " + id));
    }

    @Override
    @Transactional
    public VehicleSummary create(CreateVehicleRequest request) {
        Vehicle vehicle = Vehicle.builder()
                .serialNumber(request.serialNumber())
                .licensePlate(request.licensePlate())
                .brand(request.brand())
                .model(request.model())
                .year(request.year())
                .color(request.color())
                .mileageKm(request.mileageKm())
                .vehicleType(request.vehicleType())
                .maxLoadKg(request.maxLoadKg())
                .fuelType(request.fuelType())
                .nickname(request.nickname())
                .renavam(request.renavam())
                .active(true)
                .build();

        vehicle = vehicleRepository.save(vehicle);
        return toSummary(vehicle);
    }

    @Override
    @Transactional
    public VehicleSummary update(UUID id, UpdateVehicleRequest request) {
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Vehicle not found: " + id));

        if (request.serialNumber() != null) vehicle.setSerialNumber(request.serialNumber());
        if (request.licensePlate() != null) vehicle.setLicensePlate(request.licensePlate());
        if (request.brand() != null) vehicle.setBrand(request.brand());
        if (request.model() != null) vehicle.setModel(request.model());
        if (request.year() != null) vehicle.setYear(request.year());
        if (request.color() != null) vehicle.setColor(request.color());
        if (request.mileageKm() != null) vehicle.setMileageKm(request.mileageKm());
        if (request.vehicleType() != null) vehicle.setVehicleType(request.vehicleType());
        if (request.maxLoadKg() != null) vehicle.setMaxLoadKg(request.maxLoadKg());
        if (request.fuelType() != null) vehicle.setFuelType(request.fuelType());
        if (request.nickname() != null) vehicle.setNickname(request.nickname());
        if (request.renavam() != null) vehicle.setRenavam(request.renavam());
        vehicle.setActive(request.active());

        vehicle = vehicleRepository.save(vehicle);
        return toSummary(vehicle);
    }


    @Override
    @Transactional(readOnly = true)
    public Page<VehicleSummary> search(String serialNumber, String licensePlate, String brand,
                                        String model, Integer year, String color, String nickname,
                                        VehicleType vehicleType, FuelType fuelType, String renavam,
                                        Pageable pageable) {
        return vehicleRepository.findAll(
                        VehicleSpecification.filter(serialNumber, licensePlate, brand, model,
                                year, color, nickname, vehicleType, fuelType, renavam),
                        pageable)
                .map(this::toSummary);
    }

    private VehicleSummary toSummary(Vehicle vehicle) {
        return new VehicleSummary(
                vehicle.getId(),
                vehicle.getSerialNumber(),
                vehicle.getLicensePlate(),
                vehicle.getBrand(),
                vehicle.getModel(),
                vehicle.getYear(),
                vehicle.getColor(),
                vehicle.getMileageKm(),
                vehicle.getVehicleType(),
                vehicle.getMaxLoadKg(),
                vehicle.getFuelType(),
                vehicle.getNickname(),
                vehicle.getRenavam(),
                vehicle.getActive()
        );
    }
}
