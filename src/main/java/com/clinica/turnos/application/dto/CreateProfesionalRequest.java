package com.clinica.turnos.application.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * DTO de alta de profesional — validación Bean Validation (C-07, RN-PR-01/02).
 * JSON en camelCase (usuarioId, especialidadIds); acepta alias snake_case
 * (usuario_id, especialidad_ids) para compatibilidad con la spec delta.
 */
public record CreateProfesionalRequest(
        @NotBlank @Size(max = 100) String nombre,
        @NotBlank @Size(max = 100) String apellido,
        @Size(max = 30) String matricula,
        @Size(max = 30) String telefono,
        @Email @Size(max = 120) String email,
        @JsonAlias("usuario_id") Long usuarioId,
        @NotEmpty(message = "debe vincular al menos una especialidad")
        @JsonAlias("especialidad_ids") List<Long> especialidadIds
) {
}
