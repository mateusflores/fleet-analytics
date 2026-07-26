package com.unesp.desktopsimulator.app.services.client.dto;

import java.util.UUID;

public record ApiUserSummary(
        UUID userId,
        String username,
        String firstName,
        String lastName,
        String roleName
) {}