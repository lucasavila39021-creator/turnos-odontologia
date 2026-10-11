package com.clinica.turnos.application.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Envelope estable de lista paginada (C-09 D-05): expone content + totales
 * sin atar el contrato a {@link Page}. Se construye con
 * {@code Page.map(TurnoResponse::from)} en el servicio.
 */
public record TurnoListResponse(
        List<TurnoResponse> content,
        int page,
        int size,
        long totalElements
) {
    public static TurnoListResponse from(Page<TurnoResponse> page) {
        return new TurnoListResponse(
                List.copyOf(page.getContent()),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements());
    }

    public static TurnoListResponse vacia(int page, int size) {
        return new TurnoListResponse(List.of(), page, size, 0);
    }
}
