package com.clinica.turnos.application.exception;

/**
 * Guard RN-PR-04: no se puede desactivar un profesional con turnos futuros
 * en estado RESERVADO. Mapeada a 400 en GlobalExceptionHandler (C-07).
 * Sin tabla turno hasta C-08 el check es defensivo (ver ProfesionalService).
 */
public class ProfesionalConTurnosException extends RuntimeException {

    public ProfesionalConTurnosException() {
        super("El profesional tiene turnos futuros reservados y no puede desactivarse");
    }

    public ProfesionalConTurnosException(String message) {
        super(message);
    }
}
