package com.unesp.fleetanalyticsapi.app.controllers;

import com.unesp.fleetanalyticsapi.app.dtos.CreateVehicleRequest;
import com.unesp.fleetanalyticsapi.app.dtos.UpdateVehicleRequest;
import com.unesp.fleetanalyticsapi.app.dtos.VehicleSummary;
import com.unesp.fleetanalyticsapi.app.services.VehicleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vehicles")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService vehicleService;

    @GetMapping
    @PreAuthorize("hasAuthority('vehicles:view')")
    public ResponseEntity<List<VehicleSummary>> findAll() {
        return ResponseEntity.ok(vehicleService.findAll());
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

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('vehicles:update')")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        vehicleService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
