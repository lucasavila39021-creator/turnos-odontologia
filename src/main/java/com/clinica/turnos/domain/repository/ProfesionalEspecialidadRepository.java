package com.clinica.turnos.domain.repository;

import com.clinica.turnos.domain.model.ProfesionalEspecialidad;
import com.clinica.turnos.domain.model.ProfesionalEspecialidadId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfesionalEspecialidadRepository
        extends JpaRepository<ProfesionalEspecialidad, ProfesionalEspecialidadId> {

    List<ProfesionalEspecialidad> findByProfesionalId(Long profesionalId);

    List<ProfesionalEspecialidad> findByEspecialidadId(Long especialidadId);

    void deleteByProfesionalId(Long profesionalId);

    boolean existsByEspecialidadId(Long especialidadId);

    /**
     * RN-PR-05 (C-08): la especialidad pertenece al profesional.
     */
    boolean existsByProfesionalIdAndEspecialidadId(Long profesionalId, Long especialidadId);
}
