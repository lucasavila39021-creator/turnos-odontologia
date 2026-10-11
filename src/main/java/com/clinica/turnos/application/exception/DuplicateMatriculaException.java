package com.clinica.turnos.application.exception;

/**
 * Matrícula de profesional duplicada (RN-PR-02, comparación exacta).
 * Mapeada a 409 con fieldErrors.matricula en GlobalExceptionHandler (C-07).
 */
public class DuplicateMatriculaException extends RuntimeException {

    private final String field = "matricula";

    public DuplicateMatriculaException(String message) {
        super(message);
    }

    public DuplicateMatriculaException() {
        super("matricula ya registrada");
    }

    public String getField() {
        return field;
    }
}
