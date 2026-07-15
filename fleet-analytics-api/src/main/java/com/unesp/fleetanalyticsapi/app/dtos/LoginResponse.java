package com.unesp.fleetanalyticsapi.app.dtos;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {
}
