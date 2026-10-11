package com.clinica.turnos.application.exception;

/**
 * Desactivación rechazada: hay profesionales activos cuya única especialidad
 * activa es esta (US-020 CA-2). Mapeada a 400 en GlobalExceptionHandler (C-06 D-04/D-07).
 */
public class EspecialidadConProfesionalesException extends RuntimeException {

    public EspecialidadConProfesionalesException(String message) {
        super(message);
    }

    public EspecialidadConProfesionalesException() {
        super("No se puede desactivar: hay profesionales activos vinculados a esta especialidad");
    }
}
