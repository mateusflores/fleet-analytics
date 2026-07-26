package com.unesp.fleetanalyticsapi.app.controllers;

import com.unesp.fleetanalyticsapi.app.dtos.PermissionSummary;
import com.unesp.fleetanalyticsapi.model.repositories.PermissionRepository;
import com.unesp.fleetanalyticsapi.model.repositories.specifications.PermissionSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionRepository permissionRepository;

    @GetMapping
    @PreAuthorize("hasAuthority('roles:read')")
    public ResponseEntity<List<PermissionSummary>> findAll() {
        List<PermissionSummary> permissions = permissionRepository.findAll().stream()
                .map(p -> new PermissionSummary(p.getId(), p.getName(), p.getDescription()))
                .toList();
        return ResponseEntity.ok(permissions);
    }

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('roles:read')")
    public ResponseEntity<Page<PermissionSummary>> search(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String description,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<PermissionSummary> result = permissionRepository
                .findAll(PermissionSpecification.filter(name, description), pageable)
                .map(p -> new PermissionSummary(p.getId(), p.getName(), p.getDescription()));
        return ResponseEntity.ok(result);
    }
}
