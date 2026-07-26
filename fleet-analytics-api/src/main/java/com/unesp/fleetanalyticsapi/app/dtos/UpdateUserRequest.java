package com.unesp.fleetanalyticsapi.app.dtos;

import jakarta.validation.constraints.Email;

public record UpdateUserRequest(
        String firstName,
        String lastName,
        String phone,
        @Email String email
) {
}
