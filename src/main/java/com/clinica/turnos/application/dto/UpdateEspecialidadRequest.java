package com.clinica.turnos.application.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

/**
 * DTO de edición parcial de especialidad — todos los campos opcionales (C-06).
 * nombre con validaciones idénticas al alta; descripcion en blanco → null;
 * activa nullable (null = sin cambio).
 */
public record UpdateEspecialidadRequest(
        @Size(max = 80) String nombre,
        @Size(max = 255) String descripcion,
        Boolean activa
) {
    @AssertTrue(message = "Debe proveer al menos un campo")
    public boolean isAlMenosUnCampo() {
        return nombre != null || descripcion != null || activa != null;
    }
}
