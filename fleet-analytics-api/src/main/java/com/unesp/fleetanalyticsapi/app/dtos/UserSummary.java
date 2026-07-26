package com.unesp.fleetanalyticsapi.app.dtos;

import java.util.UUID;

public record UserSummary(
        UUID userId,
        String username,
        String email,
        String firstName,
        String lastName,
        String phone,
        boolean enabled,
        String roleName
) {
}
