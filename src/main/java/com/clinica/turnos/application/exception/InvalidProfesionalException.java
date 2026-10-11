package com.clinica.turnos.application.exception;

/**
 * Regla de profesional violada con 400 (C-07): especialidad_ids vacío o con
 * ids inexistentes/inactivos (RN-PR-01), usuario no-odontólogo, email inválido
 * en PATCH, re-asignación que dejaría sin especialidades activas.
 * Mapeada en GlobalExceptionHandler.
 */
public class InvalidProfesionalException extends RuntimeException {

    private final String field;

    public InvalidProfesionalException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
