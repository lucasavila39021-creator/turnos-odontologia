package com.clinica.turnos.application.exception;

/**
 * DNI duplicado en alta de paciente (RN-PA-03, US-010 CA-2).
 * Lleva el id de la ficha existente para el link 409. Mapeada en
 * GlobalExceptionHandler.
 */
public class DuplicateDniException extends RuntimeException {

    private final Long pacienteId;

    public DuplicateDniException(Long pacienteId) {
        super("DNI ya registrado");
        this.pacienteId = pacienteId;
    }

    public Long getPacienteId() {
        return pacienteId;
    }
}
