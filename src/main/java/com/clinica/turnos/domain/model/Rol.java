package com.clinica.turnos.domain.model;

/**
 * Roles del sistema — RN-AU-06: un único rol por usuario en v1.
 * Mapeado a ENUM MySQL en V2__usuario.sql.
 */
public enum Rol {
    PACIENTE,
    ODONTOLOGO,
    SECRETARIA,
    ADMIN
}
