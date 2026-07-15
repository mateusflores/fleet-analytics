package com.unesp.fleetanalyticsapi.app.dtos;

public record UserSummary(
        String username,
        String email,
        boolean enabled,
        String roleName
) {
}
