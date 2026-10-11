package com.clinica.turnos.application.dto;

import com.clinica.turnos.domain.model.Especialidad;
import java.time.LocalDateTime;

/**
 * DTO de respuesta — nunca expone la entidad JPA directa (hard rule).
 */
public record EspecialidadResponse(
        Long id,
        String nombre,
        String descripcion,
        Boolean activa,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static EspecialidadResponse from(Especialidad e) {
        return new EspecialidadResponse(
                e.getId(),
                e.getNombre(),
                e.getDescripcion(),
                e.getActiva(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
