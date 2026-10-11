package com.clinica.turnos.application.service;

import com.clinica.turnos.application.dto.CreateEspecialidadRequest;
import com.clinica.turnos.application.dto.EspecialidadResponse;
import com.clinica.turnos.application.dto.UpdateEspecialidadRequest;
import com.clinica.turnos.application.exception.DuplicateEspecialidadException;
import com.clinica.turnos.application.exception.EspecialidadConProfesionalesException;
import com.clinica.turnos.domain.model.Especialidad;
import com.clinica.turnos.domain.repository.EspecialidadRepository;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ABM de especialidades (C-06, RN-PR-03, US-020).
 * Unicidad nombre en 2 niveles: pre-check normalizado (trim + IgnoreCase) +
 * constraint DB como red final ante carrera concurrente (patrón UsuarioService).
 * Regla no-desactivar vía {@link EspecialidadUsagePort} (D-04).
 */
@Service
public class EspecialidadService {

    private static final Logger log = LoggerFactory.getLogger(EspecialidadService.class);

    private final EspecialidadRepository especialidadRepository;
    private final EspecialidadUsagePort usagePort;

    public EspecialidadService(EspecialidadRepository especialidadRepository,
                               EspecialidadUsagePort usagePort) {
        this.especialidadRepository = especialidadRepository;
        this.usagePort = usagePort;
    }

    @Transactional
    public EspecialidadResponse crear(CreateEspecialidadRequest dto) {
        String nombre = dto.nombre() == null ? null : dto.nombre().trim();
        if (nombre == null || nombre.isEmpty()) {
            throw new IllegalArgumentException("nombre obligatorio");
        }
        if (especialidadRepository.existsByNombreIgnoreCase(nombre)) {
            throw new DuplicateEspecialidadException("nombre ya registrado");
        }
        Especialidad entity = new Especialidad(
                nombre,
                blankToNull(dto.descripcion()),
                true
        );
        try {
            Especialidad saved = especialidadRepository.saveAndFlush(entity);
            log.info("especialidad creada id={} nombre={}", saved.getId(), nombre);
            return EspecialidadResponse.from(saved);
        } catch (DataIntegrityViolationException ex) {
            // Carrera concurrente: el constraint DB es la red final.
            throw new DuplicateEspecialidadException("nombre ya registrado");
        }
    }

    @Transactional(readOnly = true)
    public Page<EspecialidadResponse> listar(Boolean activa, Pageable pageable) {
        Page<Especialidad> page = activa == null
                ? especialidadRepository.findAll(pageable)
                : especialidadRepository.findByActiva(activa, pageable);
        return page.map(EspecialidadResponse::from);
    }

    @Transactional
    public EspecialidadResponse actualizar(Long id, UpdateEspecialidadRequest dto) {
        Especialidad entity = especialidadRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Especialidad no encontrada: " + id));
        if (dto.nombre() != null) {
            String nombreTrim = dto.nombre().trim();
            if (nombreTrim.isEmpty()) {
                throw new IllegalArgumentException("nombre no puede estar vacío");
            }
            if (!nombreTrim.equalsIgnoreCase(entity.getNombre())
                    && especialidadRepository.existsByNombreIgnoreCaseAndIdNot(nombreTrim, id)) {
                throw new DuplicateEspecialidadException("nombre ya registrado");
            }
            entity.setNombre(nombreTrim);
        }
        if (dto.descripcion() != null) {
            entity.setDescripcion(blankToNull(dto.descripcion()));
        }
        if (dto.activa() != null) {
            boolean desactiva = !dto.activa() && Boolean.TRUE.equals(entity.getActiva());
            if (desactiva && usagePort.tieneProfesionalesCautivos(id)) {
                throw new EspecialidadConProfesionalesException();
            }
            entity.setActiva(dto.activa());
        }
        try {
            Especialidad saved = especialidadRepository.saveAndFlush(entity);
            return EspecialidadResponse.from(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateEspecialidadException("nombre ya registrado");
        }
    }

    private String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
