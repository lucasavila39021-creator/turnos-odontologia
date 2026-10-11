package com.clinica.turnos.application.dto;

import com.clinica.turnos.domain.model.Especialidad;
import com.clinica.turnos.domain.model.Profesional;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO de respuesta de profesional — nunca expone la entidad JPA (hard rule).
 * Incluye el set de especialidades vinculadas como pares id/nombre.
 */
public record ProfesionalResponse(
        Long id,
        String nombre,
        String apellido,
        String matricula,
        String telefono,
        String email,
        Long usuarioId,
        Boolean activo,
        List<EspecialidadInfo> especialidades,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record EspecialidadInfo(Long id, String nombre) {
        public static EspecialidadInfo from(Especialidad e) {
            return new EspecialidadInfo(e.getId(), e.getNombre());
        }
    }

    public static ProfesionalResponse from(Profesional p, List<Especialidad> especialidades) {
        List<EspecialidadInfo> infos = especialidades == null ? List.of()
                : especialidades.stream().map(EspecialidadInfo::from).toList();
        return new ProfesionalResponse(
                p.getId(),
                p.getNombre(),
                p.getApellido(),
                p.getMatricula(),
                p.getTelefono(),
                p.getEmail(),
                p.getUsuarioId(),
                p.getActivo(),
                infos,
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
