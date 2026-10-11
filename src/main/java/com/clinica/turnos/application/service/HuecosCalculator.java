package com.clinica.turnos.application.service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Cálculo puro de huecos libres (C-09 D-04): sin IO, testeable sin DB.
 * Genera slots {@code [desde, hasta-duracion]} con step
 * {@code duracionMin} y excluye los que solapan un ocupado con la misma
 * semántica de bordes que C-08: adyacente (fin == inicio) NO es solape,
 * es decir slot libre ⟺ {@code slotInicio >= ocupFin || slotFin <= ocupInicio}.
 */
public final class HuecosCalculator {

    private HuecosCalculator() {
    }

    /** Intervalo ocupado (turno RESERVADO). */
    public record Ocupado(LocalTime inicio, LocalTime fin) {
    }

    public static List<LocalTime> libres(LocalTime desde, LocalTime hasta,
                                         int duracionMin, List<Ocupado> ocupados) {
        List<LocalTime> libres = new ArrayList<>();
        LocalTime slot = desde;
        while (!slot.plusMinutes(duracionMin).isAfter(hasta)) {
            LocalTime fin = slot.plusMinutes(duracionMin);
            boolean choca = false;
            for (Ocupado o : ocupados) {
                if (slot.isBefore(o.fin()) && fin.isAfter(o.inicio())) {
                    choca = true;
                    break;
                }
            }
            if (!choca) {
                libres.add(slot);
            }
            slot = slot.plusMinutes(duracionMin);
        }
        return List.copyOf(libres);
    }
}
