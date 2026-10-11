package com.clinica.turnos.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO login — C-03.
 */
public record LoginRequest(
        @NotBlank(message = "username must not be blank")
        @Size(max = 50, message = "username size must be between 0 and 50")
        String username,

        @NotBlank(message = "password must not be blank")
        @Size(max = 100, message = "password size must be between 0 and 100")
        String password
) {}
