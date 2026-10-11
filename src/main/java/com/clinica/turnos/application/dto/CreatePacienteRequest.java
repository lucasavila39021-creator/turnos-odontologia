package com.clinica.turnos.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * DTO de alta de paciente — validación Bean Validation (C-05, RN-PA-01/02).
 * JSON en camelCase (fechaNacimiento, usuarioId), consistente con UsuarioResponse.
 */
public record CreatePacienteRequest(
        @NotBlank @Size(max = 100) String nombre,
        @NotBlank @Size(max = 100) String apellido,
        @NotBlank @Size(max = 20) String dni,
        @Size(max = 30) String telefono,
        @Email @Size(max = 120) String email,
        @Past LocalDate fechaNacimiento,
        Long usuarioId
) {
}
