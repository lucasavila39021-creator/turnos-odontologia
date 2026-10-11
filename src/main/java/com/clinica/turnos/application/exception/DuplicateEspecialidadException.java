package com.clinica.turnos.application.exception;

/**
 * Nombre de especialidad duplicado (normalizado trim + case-insensitive, RN-PR-03).
 * Mapeada a 409 con fieldErrors.nombre en GlobalExceptionHandler (C-06 D-07).
 */
public class DuplicateEspecialidadException extends RuntimeException {

    private final String field = "nombre";

    public DuplicateEspecialidadException(String message) {
        super(message);
    }

    public DuplicateEspecialidadException() {
        super("nombre ya registrado");
    }

    public String getField() {
        return field;
    }
}
