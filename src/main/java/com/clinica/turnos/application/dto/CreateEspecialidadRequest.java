package com.clinica.turnos.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO de alta de especialidad — validación Bean Validation (C-06, RN-PR-03).
 * nombre se guarda con trim; descripcion ausente o en blanco → null (servicio).
 */
public record CreateEspecialidadRequest(
        @NotBlank @Size(max = 80) String nombre,
        @Size(max = 255) String descripcion
) {
}
