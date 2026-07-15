package com.unesp.fleetanalyticsapi.app.services;

import com.unesp.fleetanalyticsapi.app.dtos.CreateVehicleRequest;
import com.unesp.fleetanalyticsapi.app.dtos.UpdateVehicleRequest;
import com.unesp.fleetanalyticsapi.app.dtos.VehicleSummary;

import java.util.List;
import java.util.UUID;

public interface VehicleService {

    List<VehicleSummary> findAll();

    VehicleSummary findById(UUID id);

    VehicleSummary create(CreateVehicleRequest request);

    VehicleSummary update(UUID id, UpdateVehicleRequest request);

    void deactivate(UUID id);
}
