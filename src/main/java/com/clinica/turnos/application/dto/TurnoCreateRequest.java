package com.clinica.turnos.application.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DTO de reserva de turno (C-08, RN-TU-02).
 * Solo trae hora_inicio: hora_fin la calcula el servicio
 * (hora_inicio + APP_TURNO_DURACION_MIN). Acepta snake_case.
 */
public record TurnoCreateRequest(
        @NotNull(message = "paciente_id obligatorio")
        @JsonAlias("paciente_id") Long pacienteId,
        @NotNull(message = "profesional_id obligatorio")
        @JsonAlias("profesional_id") Long profesionalId,
        @NotNull(message = "especialidad_id obligatorio")
        @JsonAlias("especialidad_id") Long especialidadId,
        @NotNull(message = "fecha obligatoria") LocalDate fecha,
        @NotNull(message = "hora_inicio obligatoria")
        @JsonAlias("hora_inicio") LocalTime horaInicio
) {
}
