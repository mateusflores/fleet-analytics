package com.unesp.fleetanalyticsapi.app.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Set;
import java.util.UUID;

public record CreateRoleRequest(
        @NotBlank String name,
        @NotNull Set<UUID> permissionIds
) {
}
