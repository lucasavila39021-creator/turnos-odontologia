package com.clinica.turnos.application.dto;

import com.clinica.turnos.domain.model.EstadoTurno;
import java.time.LocalDate;

/**
 * Filtros combinables de GET /api/turnos (C-09).
 * El controller parsea los query params (String) y el servicio aplica
 * scoping por rol + defaults (fecha = hoy America/Argentina/Buenos_Aires).
 */
public record TurnoQueryParams(
        Long profesionalId,
        LocalDate fecha,
        LocalDate desde,
        LocalDate hasta,
        EstadoTurno estado
) {
}
