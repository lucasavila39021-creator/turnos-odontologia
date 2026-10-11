package com.clinica.turnos.application.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Agenda diaria del profesional (C-09): ocupados RESERVADO + huecos libres
 * calculados por {@code HuecosCalculator}. CANCELADO/ATENDIDO cuentan
 * como libres y no aparecen en ocupados.
 */
public record AgendaResponse(
        LocalDate fecha,
        List<TurnoResponse> ocupados,
        List<LocalTime> libres
) {
}
