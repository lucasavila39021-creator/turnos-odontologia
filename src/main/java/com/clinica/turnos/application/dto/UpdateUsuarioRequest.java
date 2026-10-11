package com.clinica.turnos.application.dto;

import com.clinica.turnos.domain.model.Rol;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * DTO para edición parcial de Usuario — todos los campos nullable (C-04 D-03).
 * Al menos un campo debe ser no-nulo; username inmutable (no incluido:
 * si el cliente envía "username" Jackson falla con 400 por propiedad desconocida).
 */
public record UpdateUsuarioRequest(
        @Email @Size(max = 120) String email,
        Rol rol,
        Boolean activo
) {
    @AssertTrue(message = "Debe proveer al menos un campo: email, rol o activo")
    public boolean isAlMenosUnCampo() {
        return email != null || rol != null || activo != null;
    }
}
