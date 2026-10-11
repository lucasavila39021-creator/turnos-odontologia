package com.clinica.turnos.application.service;

import com.clinica.turnos.application.dto.CreatePacienteRequest;
import com.clinica.turnos.application.dto.PacienteResponse;
import com.clinica.turnos.application.dto.UpdatePacienteRequest;
import com.clinica.turnos.application.exception.DuplicateDniException;
import com.clinica.turnos.application.exception.DuplicateLinkException;
import com.clinica.turnos.application.exception.InvalidPacienteException;
import com.clinica.turnos.domain.model.Paciente;
import com.clinica.turnos.domain.model.Rol;
import com.clinica.turnos.domain.model.Usuario;
import com.clinica.turnos.domain.repository.PacienteRepository;
import com.clinica.turnos.domain.repository.UsuarioRepository;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ABM de pacientes (C-05, RN-PA-01..04).
 * Unicidad DNI en 2 niveles: pre-check + constraint DB como red final ante
 * carrera concurrente (patrón UsuarioService). Sin DELETE físico en v1.
 */
@Service
public class PacienteService {

    private static final Logger log = LoggerFactory.getLogger(PacienteService.class);
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final PacienteRepository pacienteRepository;
    private final UsuarioRepository usuarioRepository;

    public PacienteService(PacienteRepository pacienteRepository, UsuarioRepository usuarioRepository) {
        this.pacienteRepository = pacienteRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional
    public PacienteResponse crear(CreatePacienteRequest dto) {
        String dni = dto.dni() == null ? null : dto.dni().trim();
        pacienteRepository.findByDni(dni).ifPresent(p -> {
            throw new DuplicateDniException(p.getId());
        });
        if (dto.usuarioId() != null) {
            validarLink(dto.usuarioId(), null);
        }
        Paciente entity = new Paciente(
                trimOrNull(dto.nombre()),
                trimOrNull(dto.apellido()),
                dni,
                blankToNull(dto.telefono()),
                blankToNull(dto.email()),
                dto.fechaNacimiento(),
                dto.usuarioId()
        );
        try {
            Paciente saved = pacienteRepository.saveAndFlush(entity);
            log.info("paciente creado id={} dni-hash={}", saved.getId(), Integer.toHexString(dni.hashCode()));
            return PacienteResponse.from(saved);
        } catch (DataIntegrityViolationException ex) {
            // Carrera concurrente: re-resolver contra la DB para dar link 409.
            Paciente existente = pacienteRepository.findByDni(dni).orElse(null);
            if (existente != null) {
                throw new DuplicateDniException(existente.getId());
            }
            if (dto.usuarioId() != null && pacienteRepository.existsByUsuarioId(dto.usuarioId())) {
                throw new DuplicateLinkException("usuarioId", "usuario ya vinculado a otra ficha");
            }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public Page<PacienteResponse> buscar(String dni, String apellido, Pageable pageable) {
        String dniNorm = blankToNull(dni) == null ? null : dni.trim();
        String apeNorm = blankToNull(apellido);
        Page<Paciente> page;
        if (dniNorm != null && apeNorm != null) {
            page = pacienteRepository.findByDniAndApellidoStartingWithIgnoreCase(dniNorm, apeNorm.trim(), pageable);
        } else if (dniNorm != null) {
            List<Paciente> uno = pacienteRepository.findByDni(dniNorm).map(List::of).orElseGet(List::of);
            page = new PageImpl<>(uno, pageable, uno.size());
        } else if (apeNorm != null) {
            page = pacienteRepository.findByApellidoStartingWithIgnoreCase(apeNorm.trim(), pageable);
        } else {
            page = pacienteRepository.findAll(pageable);
        }
        return page.map(PacienteResponse::from);
    }

    @Transactional(readOnly = true)
    public PacienteResponse obtener(Long id) {
        return PacienteResponse.from(getOrThrow(id));
    }

    @Transactional
    public PacienteResponse actualizar(Long id, UpdatePacienteRequest dto) {
        if (dto.dni() != null) {
            throw new InvalidPacienteException("dni", "dni inmutable en v1");
        }
        Paciente entity = getOrThrow(id);
        if (dto.nombre() != null) {
            entity.setNombre(trimOrNull(dto.nombre()));
        }
        if (dto.apellido() != null) {
            entity.setApellido(trimOrNull(dto.apellido()));
        }
        if (dto.telefono() != null) {
            entity.setTelefono(blankToNull(dto.telefono()));
        }
        if (dto.email() != null) {
            String normalizado = blankToNull(dto.email());
            if (normalizado != null && !EMAIL.matcher(normalizado).matches()) {
                throw new InvalidPacienteException("email", "email con formato inválido");
            }
            entity.setEmail(normalizado);
        }
        if (dto.fechaNacimiento() != null) {
            entity.setFechaNacimiento(dto.fechaNacimiento());
        }
        if (dto.usuarioId() != null) {
            if (dto.usuarioId().isEmpty()) {
                entity.setUsuarioId(null);
            } else {
                validarLink(dto.usuarioId().get(), id);
                entity.setUsuarioId(dto.usuarioId().get());
            }
        }
        try {
            Paciente saved = pacienteRepository.saveAndFlush(entity);
            return PacienteResponse.from(saved);
        } catch (DataIntegrityViolationException ex) {
            if (entity.getUsuarioId() != null && otroVinculado(entity.getUsuarioId(), id)) {
                throw new DuplicateLinkException("usuarioId", "usuario ya vinculado a otra ficha");
            }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public Page<Object> historial(Long id, Pageable pageable) {
        getOrThrow(id);
        // Placeholder hasta C-08 (entidad Turno): contrato congelado desde v1.
        return Page.empty(pageable);
    }

    private Paciente getOrThrow(Long id) {
        return pacienteRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Paciente no encontrado: " + id));
    }

    /**
     * Valida link IN-01: usuario existe, no vinculado a otra ficha, rol PACIENTE.
     *
     * @param excluirPacienteId ficha a excluir del chequeo de unicidad (PATCH) o null (alta)
     */
    private void validarLink(Long usuarioId, Long excluirPacienteId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + usuarioId));
        if (otroVinculado(usuarioId, excluirPacienteId)) {
            throw new DuplicateLinkException("usuarioId", "usuario ya vinculado a otra ficha");
        }
        if (usuario.getRol() != Rol.PACIENTE) {
            throw new InvalidPacienteException("usuarioId", "solo un usuario con rol PACIENTE puede vincularse a una ficha");
        }
    }

    private boolean otroVinculado(Long usuarioId, Long excluirPacienteId) {
        return pacienteRepository.findByUsuarioId(usuarioId)
                .map(p -> excluirPacienteId == null || !excluirPacienteId.equals(p.getId()))
                .orElse(false);
    }

    private String trimOrNull(String v) {
        return v == null ? null : v.trim();
    }

    private String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
