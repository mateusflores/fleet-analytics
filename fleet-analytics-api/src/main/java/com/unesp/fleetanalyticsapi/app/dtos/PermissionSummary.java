package com.unesp.fleetanalyticsapi.app.dtos;

import java.util.UUID;

public record PermissionSummary(
        UUID id,
        String name,
        String description
) {
}
