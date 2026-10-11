package com.clinica.turnos.application.exception;

import java.util.List;

/**
 * Solape RN-TU-01/05 (C-08): hueco ocupado → 409 con sugeridos (máx 3).
 * Mapeada en GlobalExceptionHandler al envelope con {@code error + sugeridos}.
 */
public class TurnoOcupadoException extends RuntimeException {

    private final List<String> sugeridos;

    public TurnoOcupadoException(List<String> sugeridos) {
        super("Horario ocupado");
        this.sugeridos = sugeridos == null ? List.of() : List.copyOf(sugeridos);
    }

    public TurnoOcupadoException(String message, List<String> sugeridos) {
        super(message);
        this.sugeridos = sugeridos == null ? List.of() : List.copyOf(sugeridos);
    }

    public List<String> getSugeridos() {
        return sugeridos;
    }
}
