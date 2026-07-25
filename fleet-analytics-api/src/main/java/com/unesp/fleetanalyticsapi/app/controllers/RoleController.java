package com.unesp.fleetanalyticsapi.app.controllers;

import com.unesp.fleetanalyticsapi.app.dtos.CreateRoleRequest;
import com.unesp.fleetanalyticsapi.app.dtos.RoleSummary;
import com.unesp.fleetanalyticsapi.app.services.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @PreAuthorize("hasAuthority('roles:read')")
    public ResponseEntity<List<RoleSummary>> findAll() {
        return ResponseEntity.ok(roleService.findAll());
    }

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('roles:read')")
    public ResponseEntity<Page<RoleSummary>> search(
            @RequestParam(required = false) String name,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(roleService.search(name, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('roles:read')")
    public ResponseEntity<RoleSummary> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(roleService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('roles:create')")
    public ResponseEntity<RoleSummary> create(@Valid @RequestBody CreateRoleRequest request) {
        RoleSummary summary = roleService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(summary);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('roles:update')")
    public ResponseEntity<RoleSummary> update(@PathVariable UUID id,
                                              @Valid @RequestBody CreateRoleRequest request) {
        return ResponseEntity.ok(roleService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('roles:delete')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        roleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
