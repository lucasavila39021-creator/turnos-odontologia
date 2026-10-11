package com.clinica.turnos.application.dto;

import com.clinica.turnos.domain.model.Paciente;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO de respuesta — nunca expone la entidad JPA directa (hard rule).
 */
public record PacienteResponse(
        Long id,
        String nombre,
        String apellido,
        String dni,
        String telefono,
        String email,
        LocalDate fechaNacimiento,
        Long usuarioId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static PacienteResponse from(Paciente p) {
        return new PacienteResponse(
                p.getId(),
                p.getNombre(),
                p.getApellido(),
                p.getDni(),
                p.getTelefono(),
                p.getEmail(),
                p.getFechaNacimiento(),
                p.getUsuarioId(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
