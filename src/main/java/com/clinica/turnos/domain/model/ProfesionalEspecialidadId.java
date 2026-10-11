package com.clinica.turnos.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

/**
 * PK compuesta de la intermedia profesional_especialidad (C-07 D-01).
 */
@Embeddable
public class ProfesionalEspecialidadId implements Serializable {

    @Column(name = "profesional_id")
    private Long profesionalId;

    @Column(name = "especialidad_id")
    private Long especialidadId;

    protected ProfesionalEspecialidadId() {
    }

    public ProfesionalEspecialidadId(Long profesionalId, Long especialidadId) {
        this.profesionalId = profesionalId;
        this.especialidadId = especialidadId;
    }

    public Long getProfesionalId() {
        return profesionalId;
    }

    public Long getEspecialidadId() {
        return especialidadId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProfesionalEspecialidadId that)) {
            return false;
        }
        return Objects.equals(profesionalId, that.profesionalId)
                && Objects.equals(especialidadId, that.especialidadId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(profesionalId, especialidadId);
    }
}
