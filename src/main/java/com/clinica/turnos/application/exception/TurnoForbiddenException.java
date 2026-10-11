package com.clinica.turnos.application.exception;

/**
 * Ownership de reserva (C-08): PACIENTE que pide turno para otra ficha
 * (o ficha sin link) → 403. Mapeada en GlobalExceptionHandler.
 */
public class TurnoForbiddenException extends RuntimeException {

    public TurnoForbiddenException() {
        super("No tiene permiso para reservar un turno para este paciente");
    }

    public TurnoForbiddenException(String message) {
        super(message);
    }
}
