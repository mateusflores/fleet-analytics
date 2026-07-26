package com.unesp.fleetanalyticsapi.app.controllers;

import com.unesp.fleetanalyticsapi.app.dtos.CreateVehicleRequest;
import com.unesp.fleetanalyticsapi.app.dtos.UpdateVehicleRequest;
import com.unesp.fleetanalyticsapi.app.dtos.VehicleSummary;
import com.unesp.fleetanalyticsapi.app.services.VehicleService;
import com.unesp.fleetanalyticsapi.model.enums.FuelType;
import com.unesp.fleetanalyticsapi.model.enums.VehicleType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vehicles")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService vehicleService;

    @GetMapping
    @PreAuthorize("hasAuthority('vehicles:view')")
    public ResponseEntity<Page<VehicleSummary>> findAll(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(vehicleService.findAll(pageable));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('vehicles:view')")
    public ResponseEntity<Page<VehicleSummary>> search(
            @RequestParam(required = false) String serialNumber,
            @RequestParam(required = false) String licensePlate,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String color,
            @RequestParam(required = false) String nickname,
            @RequestParam(required = false) VehicleType vehicleType,
            @RequestParam(required = false) FuelType fuelType,
            @RequestParam(required = false) String renavam,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(vehicleService.search(serialNumber, licensePlate, brand, model,
                year, color, nickname, vehicleType, fuelType, renavam, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('vehicles:view')")
    public ResponseEntity<VehicleSummary> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(vehicleService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('vehicles:create')")
    public ResponseEntity<VehicleSummary> create(@Valid @RequestBody CreateVehicleRequest request) {
        VehicleSummary summary = vehicleService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(summary);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('vehicles:update')")
    public ResponseEntity<VehicleSummary> update(@PathVariable UUID id,
                                                 @Valid @RequestBody UpdateVehicleRequest request) {
        return ResponseEntity.ok(vehicleService.update(id, request));
    }

}
