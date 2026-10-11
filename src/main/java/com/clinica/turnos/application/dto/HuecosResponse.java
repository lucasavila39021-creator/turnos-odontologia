package com.clinica.turnos.application.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Vista de huecos libres (C-09 D-05): alias de la parte {@code libres} de
 * {@link AgendaResponse}. Ambos nombres quedan exportados.
 */
public record HuecosResponse(
        LocalDate fecha,
        List<LocalTime> libres
) {
    public static HuecosResponse from(AgendaResponse agenda) {
        return new HuecosResponse(agenda.fecha(), List.copyOf(agenda.libres()));
    }
}
