package com.clinica.turnos.domain.model;

/**
 * Estado del turno — ENUM MySQL en V6__turno.sql (C-08).
 * Solo RESERVADO bloquea huecos (RN-TU-01/05); CANCELADO/ATENDIDO liberan.
 */
public enum EstadoTurno {
    RESERVADO,
    CANCELADO,
    ATENDIDO
}
