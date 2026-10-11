package com.clinica.turnos.application.dto;

import com.clinica.turnos.domain.model.Especialidad;
import com.clinica.turnos.domain.model.EstadoTurno;
import com.clinica.turnos.domain.model.Paciente;
import com.clinica.turnos.domain.model.Profesional;
import com.clinica.turnos.domain.model.Turno;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * DTO de respuesta de turno — nunca expone la entidad JPA (hard rule).
 * Anida paciente/profesional/especialidad como pares id/nombre.
 */
public record TurnoResponse(
        Long id,
        PacienteInfo paciente,
        ProfesionalInfo profesional,
        EspecialidadInfo especialidad,
        LocalDate fecha,
        LocalTime horaInicio,
        LocalTime horaFin,
        EstadoTurno estado,
        String motivoCancelacion,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record PacienteInfo(Long id, String nombre, String apellido) {
        public static PacienteInfo from(Paciente p) {
            return new PacienteInfo(p.getId(), p.getNombre(), p.getApellido());
        }
    }

    public record ProfesionalInfo(Long id, String nombre, String apellido) {
        public static ProfesionalInfo from(Profesional p) {
            return new ProfesionalInfo(p.getId(), p.getNombre(), p.getApellido());
        }
    }

    public record EspecialidadInfo(Long id, String nombre) {
        public static EspecialidadInfo from(Especialidad e) {
            return new EspecialidadInfo(e.getId(), e.getNombre());
        }
    }

    public static TurnoResponse from(Turno t, Paciente p, Profesional prof, Especialidad e) {
        return new TurnoResponse(
                t.getId(),
                PacienteInfo.from(p),
                ProfesionalInfo.from(prof),
                EspecialidadInfo.from(e),
                t.getFecha(),
                t.getHoraInicio(),
                t.getHoraFin(),
                t.getEstado(),
                t.getMotivoCancelacion(),
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }
}
