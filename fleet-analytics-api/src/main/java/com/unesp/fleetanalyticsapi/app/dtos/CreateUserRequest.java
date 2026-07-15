package com.unesp.fleetanalyticsapi.app.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateUserRequest(
        @NotBlank @Size(min = 6, max = 30) String username,
        @NotBlank @Email String email,
        @NotBlank String password,
        @NotNull UUID roleId
) {
}
