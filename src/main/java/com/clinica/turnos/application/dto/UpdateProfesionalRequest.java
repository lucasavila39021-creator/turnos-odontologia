package com.clinica.turnos.application.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Optional;

/**
 * DTO de edición parcial de profesional (C-07).
 * Todos los campos opcionales; {@code especialidadIds} reemplaza el set
 * (null = sin cambio, vacío = 400 en servicio). {@code usuarioId} usa
 * {@code Optional} para distinguir ausente (sin cambio) de null explícito
 * (desvincular). {@code email} sin {@code @Email} a nivel DTO: el blanco se
 * normaliza a null y el formato se valida en el servicio (patrón C-05).
 */
public record UpdateProfesionalRequest(
        @Size(max = 100) String nombre,
        @Size(max = 100) String apellido,
        @Size(max = 30) String matricula,
        @Size(max = 30) String telefono,
        @Size(max = 120) String email,
        @JsonAlias("usuario_id") Optional<Long> usuarioId,
        @JsonAlias("especialidad_ids") List<Long> especialidadIds,
        Boolean activo
) {
    @AssertTrue(message = "Debe proveer al menos un campo")
    public boolean isAlMenosUnCampo() {
        return nombre != null || apellido != null || matricula != null
                || telefono != null || email != null || usuarioId != null
                || especialidadIds != null || activo != null;
    }
}
