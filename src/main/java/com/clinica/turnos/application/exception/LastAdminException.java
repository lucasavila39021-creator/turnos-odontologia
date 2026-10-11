package com.clinica.turnos.application.exception;

/**
 * Se lanza cuando una operación dejaría cero admins activos.
 * Mapeada a 400 en GlobalExceptionHandler (C-04 D-05).
 */
public class LastAdminException extends RuntimeException {

    public LastAdminException() {
        super("No se puede desactivar o degradar al último administrador activo");
    }

    public LastAdminException(String message) {
        super(message);
    }
}
