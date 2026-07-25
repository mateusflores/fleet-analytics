package com.unesp.fleetanalyticsapi.app.services;

import com.unesp.fleetanalyticsapi.app.dtos.CreateVehicleRequest;
import com.unesp.fleetanalyticsapi.app.dtos.UpdateVehicleRequest;
import com.unesp.fleetanalyticsapi.app.dtos.VehicleSummary;
import com.unesp.fleetanalyticsapi.model.enums.FuelType;
import com.unesp.fleetanalyticsapi.model.enums.VehicleType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface VehicleService {

    Page<VehicleSummary> findAll(Pageable pageable);

    VehicleSummary findById(UUID id);

    VehicleSummary create(CreateVehicleRequest request);

    VehicleSummary update(UUID id, UpdateVehicleRequest request);

    Page<VehicleSummary> search(String serialNumber, String licensePlate, String brand,
                                String model, Integer year, String color, String nickname,
                                VehicleType vehicleType, FuelType fuelType, String renavam,
                                Pageable pageable);
}
