package com.unesp.desktopsimulator.app.services.client.dto;

import java.util.UUID;

public record ApiLoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        UUID userId,
        long expiresIn
) {}