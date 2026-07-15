package com.unesp.fleetanalyticsapi.app.dtos;

import java.util.Set;
import java.util.UUID;

public record RoleSummary(
        UUID id,
        String name,
        Set<PermissionSummary> permissions
) {
}
