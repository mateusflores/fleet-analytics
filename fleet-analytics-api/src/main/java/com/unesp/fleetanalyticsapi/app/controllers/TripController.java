package com.unesp.fleetanalyticsapi.app.controllers;

import com.unesp.fleetanalyticsapi.app.dtos.CreateTripRequest;
import com.unesp.fleetanalyticsapi.app.dtos.TripSummary;
import com.unesp.fleetanalyticsapi.app.services.TripService;
import com.unesp.fleetanalyticsapi.model.entities.crud.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    @PostMapping
    @PreAuthorize("hasAuthority('travel:start')")
    public ResponseEntity<TripSummary> startTrip(@Valid @RequestBody CreateTripRequest request) {
        TripSummary summary = tripService.startTrip(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(summary);
    }

    @PatchMapping("/{id}/end")
    @PreAuthorize("hasAuthority('travel:start')")
    public ResponseEntity<TripSummary> endTrip(@PathVariable UUID id,
                                                Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        TripSummary summary = tripService.endTrip(id, currentUser.getId());
        return ResponseEntity.ok(summary);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('travel:read')")
    public ResponseEntity<Page<TripSummary>> findAll(
            @PageableDefault(size = 20, sort = "startTime", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(tripService.findAll(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('travel:read')")
    public ResponseEntity<TripSummary> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(tripService.findById(id));
    }

    @GetMapping("/vehicle/{vehicleId}/active")
    @PreAuthorize("hasAuthority('travel:read')")
    public ResponseEntity<TripSummary> findActiveByVehicle(@PathVariable UUID vehicleId) {
        return ResponseEntity.ok(tripService.findActiveByVehicle(vehicleId));
    }
}
