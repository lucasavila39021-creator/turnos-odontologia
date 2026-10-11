package com.clinica.turnos.application.service;

import com.clinica.turnos.application.dto.CreateProfesionalRequest;
import com.clinica.turnos.application.dto.ProfesionalResponse;
import com.clinica.turnos.application.dto.UpdateProfesionalRequest;
import com.clinica.turnos.application.exception.DuplicateLinkException;
import com.clinica.turnos.application.exception.DuplicateMatriculaException;
import com.clinica.turnos.application.exception.InvalidProfesionalException;
import com.clinica.turnos.application.exception.ProfesionalConTurnosException;
import com.clinica.turnos.domain.model.Especialidad;
import com.clinica.turnos.domain.model.EstadoTurno;
import com.clinica.turnos.domain.model.Profesional;
import com.clinica.turnos.domain.model.ProfesionalEspecialidad;
import com.clinica.turnos.domain.model.Rol;
import com.clinica.turnos.domain.model.Usuario;
import com.clinica.turnos.domain.repository.EspecialidadRepository;
import com.clinica.turnos.domain.repository.ProfesionalEspecialidadRepository;
import com.clinica.turnos.domain.repository.ProfesionalRepository;
import com.clinica.turnos.domain.repository.TurnoRepository;
import com.clinica.turnos.domain.repository.UsuarioRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ABM de profesionales (C-07, RN-PR-01/02/04).
 * Validaciones en servicio con doble nivel (pre-check + constraint DB),
 * mismo patrón que EspecialidadService/PacienteService.
 * Guard RN-PR-04 real desde C-08 ({@link #tieneTurnosFuturosReservados}
 * consulta la tabla turno).
 */
@Service
public class ProfesionalService {

    private static final Logger log = LoggerFactory.getLogger(ProfesionalService.class);
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final ProfesionalRepository profesionalRepository;
    private final ProfesionalEspecialidadRepository vinculoRepository;
    private final EspecialidadRepository especialidadRepository;
    private final UsuarioRepository usuarioRepository;
    private final TurnoRepository turnoRepository;

    public ProfesionalService(ProfesionalRepository profesionalRepository,
                              ProfesionalEspecialidadRepository vinculoRepository,
                              EspecialidadRepository especialidadRepository,
                              UsuarioRepository usuarioRepository,
                              TurnoRepository turnoRepository) {
        this.profesionalRepository = profesionalRepository;
        this.vinculoRepository = vinculoRepository;
        this.especialidadRepository = especialidadRepository;
        this.usuarioRepository = usuarioRepository;
        this.turnoRepository = turnoRepository;
    }

    @Transactional
    public ProfesionalResponse crear(CreateProfesionalRequest dto) {
        String nombre = trimOrNull(dto.nombre());
        String apellido = trimOrNull(dto.apellido());
        if (nombre == null || nombre.isEmpty()) {
            throw new InvalidProfesionalException("nombre", "nombre obligatorio");
        }
        if (apellido == null || apellido.isEmpty()) {
            throw new InvalidProfesionalException("apellido", "apellido obligatorio");
        }
        String matricula = blankToNull(dto.matricula());
        if (matricula != null && profesionalRepository.existsByMatricula(matricula)) {
            throw new DuplicateMatriculaException("matricula ya registrada");
        }
        if (dto.usuarioId() != null) {
            validarLink(dto.usuarioId(), null);
        }
        List<Especialidad> especialidades = resolverEspecialidades(dto.especialidadIds());
        Profesional entity = new Profesional(
                nombre,
                apellido,
                matricula,
                blankToNull(dto.telefono()),
                blankToNull(dto.email()),
                dto.usuarioId(),
                true
        );
        try {
            Profesional saved = profesionalRepository.saveAndFlush(entity);
            vincular(saved, especialidades);
            log.info("profesional creado id={}", saved.getId());
            return cargarRespuesta(saved);
        } catch (DataIntegrityViolationException ex) {
            // Carrera concurrente: re-resolver contra la DB para el 409 exacto.
            if (matricula != null && profesionalRepository.existsByMatricula(matricula)) {
                throw new DuplicateMatriculaException("matricula ya registrada");
            }
            if (dto.usuarioId() != null && profesionalRepository.existsByUsuarioId(dto.usuarioId())) {
                throw new DuplicateLinkException("usuarioId", "usuario ya vinculado a otra ficha");
            }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public Page<ProfesionalResponse> listar(Long especialidadId, Boolean activo, Pageable pageable) {
        return profesionalRepository.findByFiltros(especialidadId, activo, pageable)
                .map(this::cargarRespuesta);
    }

    @Transactional(readOnly = true)
    public Page<ProfesionalResponse> listarPorEspecialidad(Long especialidadId, Pageable pageable) {
        return profesionalRepository.findByEspecialidadId(especialidadId, pageable)
                .map(this::cargarRespuesta);
    }

    @Transactional(readOnly = true)
    public ProfesionalResponse obtener(Long id) {
        return cargarRespuesta(getOrThrow(id));
    }

    @Transactional
    public ProfesionalResponse actualizar(Long id, UpdateProfesionalRequest dto) {
        Profesional entity = getOrThrow(id);
        if (dto.nombre() != null) {
            String nombre = dto.nombre().trim();
            if (nombre.isEmpty()) {
                throw new InvalidProfesionalException("nombre", "nombre no puede estar vacío");
            }
            entity.setNombre(nombre);
        }
        if (dto.apellido() != null) {
            String apellido = dto.apellido().trim();
            if (apellido.isEmpty()) {
                throw new InvalidProfesionalException("apellido", "apellido no puede estar vacío");
            }
            entity.setApellido(apellido);
        }
        if (dto.matricula() != null) {
            String matricula = blankToNull(dto.matricula());
            if (matricula != null && !matricula.equals(entity.getMatricula())
                    && profesionalRepository.findByMatricula(matricula)
                            .map(p -> !p.getId().equals(id)).orElse(false)) {
                throw new DuplicateMatriculaException("matricula ya registrada");
            }
            entity.setMatricula(matricula);
        }
        if (dto.telefono() != null) {
            entity.setTelefono(blankToNull(dto.telefono()));
        }
        if (dto.email() != null) {
            String normalizado = blankToNull(dto.email());
            if (normalizado != null && !EMAIL.matcher(normalizado).matches()) {
                throw new InvalidProfesionalException("email", "email con formato inválido");
            }
            entity.setEmail(normalizado);
        }
        if (dto.usuarioId() != null) {
            if (dto.usuarioId().isEmpty()) {
                entity.setUsuarioId(null);
            } else {
                validarLink(dto.usuarioId().get(), id);
                entity.setUsuarioId(dto.usuarioId().get());
            }
        }
        List<Especialidad> nuevas = null;
        if (dto.especialidadIds() != null) {
            nuevas = resolverEspecialidades(dto.especialidadIds());
        }
        if (dto.activo() != null) {
            if (!dto.activo() && entity.isActivo()) {
                if (tieneTurnosFuturosReservados(id)) {
                    throw new ProfesionalConTurnosException();
                }
                entity.setActivo(false);
            } else if (dto.activo()) {
                entity.setActivo(true);
            }
        }
        try {
            Profesional saved = profesionalRepository.saveAndFlush(entity);
            if (nuevas != null) {
                vinculoRepository.deleteByProfesionalId(id);
                vinculoRepository.flush();
                vincular(saved, nuevas);
            }
            return cargarRespuesta(saved);
        } catch (DataIntegrityViolationException ex) {
            if (entity.getMatricula() != null && otraMatricula(entity.getMatricula(), id)) {
                throw new DuplicateMatriculaException("matricula ya registrada");
            }
            if (entity.getUsuarioId() != null && otroVinculado(entity.getUsuarioId(), id)) {
                throw new DuplicateLinkException("usuarioId", "usuario ya vinculado a otra ficha");
            }
            throw ex;
        }
    }

    @Transactional
    public ProfesionalResponse desactivar(Long id) {
        Profesional entity = getOrThrow(id);
        if (!entity.isActivo()) {
            return cargarRespuesta(entity);
        }
        if (tieneTurnosFuturosReservados(id)) {
            throw new ProfesionalConTurnosException();
        }
        entity.setActivo(false);
        Profesional saved = profesionalRepository.saveAndFlush(entity);
        log.info("profesional desactivado id={}", id);
        return cargarRespuesta(saved);
    }

    /**
     * Guard RN-PR-04 (C-08 D-07): turnos futuros en estado RESERVADO
     * ({@code fecha >= hoy}) bloquean la desactivación. Query real sobre
     * la tabla {@code turno} con el índice (profesional_id, fecha).
     */
    public boolean tieneTurnosFuturosReservados(Long profesionalId) {
        return turnoRepository.existsByProfesionalIdAndFechaGreaterThanEqualAndEstado(
                profesionalId, LocalDate.now(), EstadoTurno.RESERVADO);
    }

    private Profesional getOrThrow(Long id) {
        return profesionalRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Profesional no encontrado: " + id));
    }

    /**
     * Resuelve y valida el set de especialidades (RN-PR-01): no vacío,
     * todos los ids existen y están activos. Dentro de la misma TX.
     */
    private List<Especialidad> resolverEspecialidades(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new InvalidProfesionalException(
                    "especialidad_ids", "debe vincular al menos una especialidad");
        }
        Set<Long> unicos = new LinkedHashSet<>(ids);
        List<Especialidad> encontradas = especialidadRepository.findAllById(unicos);
        if (encontradas.size() != unicos.size()) {
            throw new InvalidProfesionalException(
                    "especialidad_ids", "una o más especialidades no existen");
        }
        List<Especialidad> inactivas = encontradas.stream()
                .filter(e -> !e.isActiva()).toList();
        if (!inactivas.isEmpty()) {
            throw new InvalidProfesionalException(
                    "especialidad_ids", "una o más especialidades no están activas");
        }
        return new ArrayList<>(encontradas);
    }

    private void vincular(Profesional profesional, List<Especialidad> especialidades) {
        for (Especialidad esp : especialidades) {
            vinculoRepository.save(new ProfesionalEspecialidad(profesional, esp));
        }
        vinculoRepository.flush();
    }

    private ProfesionalResponse cargarRespuesta(Profesional p) {
        List<Especialidad> especialidades = vinculoRepository.findByProfesionalId(p.getId())
                .stream().map(ProfesionalEspecialidad::getEspecialidad).toList();
        return ProfesionalResponse.from(p, especialidades);
    }

    /**
     * Valida link a usuario (D-03): existe (404) → no vinculado a otra ficha
     * (409) → rol ODONTOLOGO (400). Orden intencional: unicidad antes que rol.
     */
    private void validarLink(Long usuarioId, Long excluirProfesionalId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + usuarioId));
        if (otroVinculado(usuarioId, excluirProfesionalId)) {
            throw new DuplicateLinkException("usuarioId", "usuario ya vinculado a otra ficha");
        }
        if (usuario.getRol() != Rol.ODONTOLOGO) {
            throw new InvalidProfesionalException(
                    "usuario_id", "solo un usuario con rol ODONTOLOGO puede vincularse a una ficha");
        }
    }

    private boolean otroVinculado(Long usuarioId, Long excluirProfesionalId) {
        return profesionalRepository.findByUsuarioId(usuarioId)
                .map(p -> excluirProfesionalId == null || !excluirProfesionalId.equals(p.getId()))
                .orElse(false);
    }

    private boolean otraMatricula(String matricula, Long excluirId) {
        return profesionalRepository.findByMatricula(matricula)
                .map(p -> !excluirId.equals(p.getId()))
                .orElse(false);
    }

    private String trimOrNull(String v) {
        return v == null ? null : v.trim();
    }

    private String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
