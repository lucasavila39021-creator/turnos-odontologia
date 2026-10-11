package com.clinica.turnos.domain.model;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

/**
 * Intermedia explícita profesional_especialidad (C-07 D-01).
 * En vez de {@code @ManyToMany}: expone la FK para el guard de cautivos
 * (C-06/C-08, RN-PR-05) y permite COUNT/EXISTS por especialidad.
 */
@Entity
@Table(name = "profesional_especialidad")
public class ProfesionalEspecialidad {

    @EmbeddedId
    private ProfesionalEspecialidadId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("profesionalId")
    @JoinColumn(name = "profesional_id", nullable = false)
    private Profesional profesional;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("especialidadId")
    @JoinColumn(name = "especialidad_id", nullable = false)
    private Especialidad especialidad;

    protected ProfesionalEspecialidad() {
    }

    public ProfesionalEspecialidad(Profesional profesional, Especialidad especialidad) {
        this.profesional = profesional;
        this.especialidad = especialidad;
        this.id = new ProfesionalEspecialidadId(profesional.getId(), especialidad.getId());
    }

    public ProfesionalEspecialidadId getId() {
        return id;
    }

    public Profesional getProfesional() {
        return profesional;
    }

    public Especialidad getEspecialidad() {
        return especialidad;
    }
}
