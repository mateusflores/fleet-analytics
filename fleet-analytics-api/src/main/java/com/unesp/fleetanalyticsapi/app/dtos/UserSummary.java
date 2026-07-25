package com.unesp.fleetanalyticsapi.app.dtos;

import java.util.UUID;

public record UserSummary(
        UUID userId,
        String username,
        String email,
        boolean enabled,
        String roleName
) {
}
