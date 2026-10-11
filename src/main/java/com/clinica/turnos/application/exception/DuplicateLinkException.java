package com.clinica.turnos.application.exception;

/**
 * Link paciente↔usuario inválido por unicidad: el usuario ya está vinculado
 * a otra ficha (IN-01). Mapeada a 409 en GlobalExceptionHandler.
 */
public class DuplicateLinkException extends RuntimeException {

    private final String field;

    public DuplicateLinkException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
