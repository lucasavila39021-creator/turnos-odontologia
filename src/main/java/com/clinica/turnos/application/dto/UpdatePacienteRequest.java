package com.clinica.turnos.application.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Optional;

/**
 * DTO de edición parcial de paciente — todos los campos opcionales (C-05).
 * {@code dni} inmutable en v1: se declara solo para rechazarlo con 400 y
 * {@code fieldErrors.dni} en el servicio (Jackson no falla por defecto ante
 * propiedades desconocidas). {@code email} sin {@code @Email} a nivel DTO:
 * el blanco se normaliza a {@code null} y el formato se valida en el
 * servicio. {@code usuarioId} usa {@code Optional} para distinguir ausente
 * (sin cambio) de {@code null} explícito (desvincular, IN-01).
 */
public record UpdatePacienteRequest(
        @Size(max = 100) String nombre,
        @Size(max = 100) String apellido,
        @Size(max = 30) String telefono,
        @Size(max = 120) String email,
        @Past LocalDate fechaNacimiento,
        Optional<Long> usuarioId,
        String dni
) {
    @AssertTrue(message = "Debe proveer al menos un campo")
    public boolean isAlMenosUnCampo() {
        return nombre != null || apellido != null || telefono != null
                || email != null || fechaNacimiento != null || usuarioId != null
                || dni != null;
    }
}
