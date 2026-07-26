package com.unesp.desktopsimulator.app.services.client.dto;

public record ApiLoginRequest(
        String username,
        String password
) {}