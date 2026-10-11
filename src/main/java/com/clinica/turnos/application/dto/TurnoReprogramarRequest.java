package com.clinica.turnos.application.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Reprogramar turno (C-10, RN-TU-08): destino nuevo. Acepta snake_case.
 */
public record TurnoReprogramarRequest(
        @NotNull(message = "nueva_fecha obligatoria")
        @JsonAlias({"nueva_fecha", "nuevaFecha"}) LocalDate nuevaFecha,
        @NotNull(message = "nueva_hora_inicio obligatoria")
        @JsonAlias({"nueva_hora_inicio", "nuevaHoraInicio"}) LocalTime nuevaHoraInicio
) {
}
