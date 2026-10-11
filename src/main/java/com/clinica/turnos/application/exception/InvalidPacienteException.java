package com.clinica.turnos.application.exception;

/**
 * Regla de paciente violada con 400: dni inmutable en PATCH, rol de usuario
 * vinculado distinto de PACIENTE (IN-01). Mapeada en GlobalExceptionHandler.
 */
public class InvalidPacienteException extends RuntimeException {

    private final String field;

    public InvalidPacienteException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
