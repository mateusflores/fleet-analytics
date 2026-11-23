package com.unesp.fleetanalyticsapi.app.controllers;

import com.unesp.fleetanalyticsapi.app.dtos.FleetPolicySummary;
import com.unesp.fleetanalyticsapi.app.dtos.UpdateFleetPolicyRequest;
import com.unesp.fleetanalyticsapi.app.services.FleetPolicyService;
import com.unesp.fleetanalyticsapi.model.entities.crud.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Políticas da frota (limites para métricas e insights). */
@RestController
@RequestMapping("/api/v1/fleet-policy")
@RequiredArgsConstructor
public class FleetPolicyController {

    private final FleetPolicyService policyService;

    @GetMapping
    @PreAuthorize("hasAuthority('analytics:view')")
    public ResponseEntity<FleetPolicySummary> get() {
        return ResponseEntity.ok(policyService.get());
    }

    @PutMapping
    @PreAuthorize("hasAuthority('fleet-policy:update')")
    public ResponseEntity<FleetPolicySummary> update(@Valid @RequestBody UpdateFleetPolicyRequest request,
                                                     Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        return ResponseEntity.ok(policyService.update(request, currentUser.getId()));
    }
}
