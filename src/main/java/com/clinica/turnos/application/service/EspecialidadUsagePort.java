package com.clinica.turnos.application.service;

/**
 * Puerto de uso de especialidad por profesionales (C-06 D-04).
 * Lógica pura: "¿existe profesional activo cuya única especialidad activa sea esta?".
 * C-06 provee {@link NoOpEspecialidadUsage}; C-07 lo reemplaza por la
 * implementación real sobre profesional_especialidad.
 */
public interface EspecialidadUsagePort {

    boolean tieneProfesionalesCautivos(Long especialidadId);
}
