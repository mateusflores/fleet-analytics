package com.unesp.desktopsimulator.app.services.client.dto;

public record ApiLogoutRequest(
        String refreshToken
) {}