package com.clinica.turnos.application.exception;

/**
 * Unicidad violada en pre-check (username/email duplicado).
 * Mapeada a 409 en GlobalExceptionHandler (C-04 D-04).
 */
public class DuplicateUsuarioException extends RuntimeException {

    private final String field;

    public DuplicateUsuarioException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
