package com.unesp.fleetanalyticsapi.app.dtos;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
        @NotBlank String refreshToken
) {
}
