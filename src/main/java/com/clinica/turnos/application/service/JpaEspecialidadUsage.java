package com.clinica.turnos.application.service;

import com.clinica.turnos.domain.model.ProfesionalEspecialidad;
import com.clinica.turnos.domain.repository.EspecialidadRepository;
import com.clinica.turnos.domain.repository.ProfesionalEspecialidadRepository;
import com.clinica.turnos.domain.repository.ProfesionalRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementación real del guard de no-desactivar (C-07 D-05, handoff C-06).
 * Reemplaza a {@code NoOpEspecialidadUsage}: cautivo = profesional con
 * {@code activo=true} vinculado a E cuya única especialidad activa sea E.
 */
@Component
public class JpaEspecialidadUsage implements EspecialidadUsagePort {

    private final ProfesionalEspecialidadRepository vinculoRepository;
    private final ProfesionalRepository profesionalRepository;
    private final EspecialidadRepository especialidadRepository;

    public JpaEspecialidadUsage(ProfesionalEspecialidadRepository vinculoRepository,
                                ProfesionalRepository profesionalRepository,
                                EspecialidadRepository especialidadRepository) {
        this.vinculoRepository = vinculoRepository;
        this.profesionalRepository = profesionalRepository;
        this.especialidadRepository = especialidadRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean tieneProfesionalesCautivos(Long especialidadId) {
        List<ProfesionalEspecialidad> vinculos = vinculoRepository.findByEspecialidadId(especialidadId);
        for (ProfesionalEspecialidad v : vinculos) {
            Long profId = v.getProfesional().getId();
            boolean activo = profesionalRepository.findById(profId)
                    .map(p -> p.isActivo()).orElse(false);
            if (!activo) {
                continue;
            }
            Set<Long> activasVinculadas = new HashSet<>();
            for (ProfesionalEspecialidad otro : vinculoRepository.findByProfesionalId(profId)) {
                Long espId = otro.getEspecialidad().getId();
                boolean espActiva = especialidadRepository.findById(espId)
                        .map(e -> e.isActiva()).orElse(false);
                if (espActiva) {
                    activasVinculadas.add(espId);
                }
            }
            if (activasVinculadas.size() == 1 && activasVinculadas.contains(especialidadId)) {
                return true;
            }
        }
        return false;
    }
}
