package com.clinica.turnos.application.dto;

import com.clinica.turnos.domain.model.Rol;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO para creación de Usuario — validación Bean Validation.
 * Hard rule: @Valid en controller (C-04), nunca exponer entidad directa.
 */
public record CreateUsuarioRequest(
        @NotBlank @Size(max = 50) @Pattern(regexp = "^[a-zA-Z0-9._-]{3,50}$",
                message = "username debe tener 3-50 caracteres alfanuméricos, punto, guion o guion bajo") String username,
        @NotBlank @Size(min = 8, max = 100) String password,
        @Email @Size(max = 120) String email,
        @NotNull Rol rol,
        Boolean activo
) {
    public CreateUsuarioRequest {
        if (activo == null) {
            activo = true;
        }
    }

    /**
     * Constructor de conveniencia sin activo (default true).
     */
    public CreateUsuarioRequest(String username, String password, String email, Rol rol) {
        this(username, password, email, rol, true);
    }
}
