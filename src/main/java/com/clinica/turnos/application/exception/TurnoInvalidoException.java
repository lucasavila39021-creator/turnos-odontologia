package com.clinica.turnos.application.exception;

/**
 * Regla de turno violada con 400 (C-08): RN-TU-03 (fuera de horario),
 * RN-TU-04 (fecha pasada), RN-PR-05 (especialidad ajena), profesional o
 * especialidad inactivos. Mapeada en GlobalExceptionHandler con fieldErrors.
 */
public class TurnoInvalidoException extends RuntimeException {

    private final String field;

    public TurnoInvalidoException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
