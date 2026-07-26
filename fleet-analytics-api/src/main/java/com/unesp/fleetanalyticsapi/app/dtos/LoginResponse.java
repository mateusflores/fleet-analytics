package com.unesp.fleetanalyticsapi.app.dtos;

import java.util.UUID;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        UUID userId,
        long expiresIn
) {
}
