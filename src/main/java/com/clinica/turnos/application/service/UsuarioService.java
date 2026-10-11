package com.clinica.turnos.application.service;

import com.clinica.turnos.application.dto.CreateUsuarioRequest;
import com.clinica.turnos.application.dto.UpdateUsuarioRequest;
import com.clinica.turnos.application.dto.UsuarioResponse;
import com.clinica.turnos.application.exception.DuplicateUsuarioException;
import com.clinica.turnos.application.exception.LastAdminException;
import com.clinica.turnos.domain.model.Rol;
import com.clinica.turnos.domain.model.Usuario;
import com.clinica.turnos.domain.repository.UsuarioRepository;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ABM de usuarios solo-ADMIN (C-04).
 * Thin-controller: toda la regla de negocio vive acá.
 */
@Service
public class UsuarioService {

    private static final Logger log = LoggerFactory.getLogger(UsuarioService.class);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public Page<UsuarioResponse> listar(Pageable pageable) {
        return usuarioRepository.findAll(pageable).map(UsuarioResponse::from);
    }

    @Transactional
    public UsuarioResponse crear(CreateUsuarioRequest dto, String actorUsername) {
        if (usuarioRepository.existsByUsernameIgnoreCase(dto.username())) {
            throw duplicate("username", "username ya registrado");
        }
        if (dto.email() != null && !dto.email().isBlank()
                && usuarioRepository.existsByEmailIgnoreCase(dto.email())) {
            throw duplicate("email", "email ya registrado");
        }
        String hash = passwordEncoder.encode(dto.password());
        Usuario entity = new Usuario(
                dto.username(),
                hash,
                (dto.email() == null || dto.email().isBlank()) ? null : dto.email(),
                dto.rol(),
                dto.activo() != null ? dto.activo() : true
        );
        try {
            Usuario saved = usuarioRepository.saveAndFlush(entity);
            return UsuarioResponse.from(saved);
        } catch (DataIntegrityViolationException ex) {
            // Carrera concurrente: el constraint DB es la red final.
            throw ex;
        }
    }

    @Transactional
    public UsuarioResponse actualizar(Long id, UpdateUsuarioRequest dto, String actorUsername) {
        Usuario entity = usuarioRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + id));

        if (dto.email() != null) {
            String nuevoEmail = dto.email().isBlank() ? null : dto.email();
            if (nuevoEmail != null && !nuevoEmail.equalsIgnoreCase(entity.getEmail())
                    && usuarioRepository.existsByEmailIgnoreCase(nuevoEmail)) {
                throw duplicate("email", "email ya registrado");
            }
            entity.setEmail(nuevoEmail);
        }

        boolean degradaRol = dto.rol() != null
                && entity.getRol() == Rol.ADMIN
                && dto.rol() != Rol.ADMIN;
        boolean desactiva = dto.activo() != null
                && !dto.activo()
                && entity.getRol() == Rol.ADMIN
                && Boolean.TRUE.equals(entity.getActivo());

        if ((degradaRol || desactiva) && esUltimoAdminActivo(entity)) {
            throw new LastAdminException();
        }

        if (dto.rol() != null && dto.rol() != entity.getRol()) {
            Rol anterior = entity.getRol();
            entity.setRol(dto.rol());
            log.info("usuario rol cambiado id={} by={} {}->{}", entity.getId(), actorUsername, anterior, dto.rol());
        }
        if (dto.activo() != null) {
            entity.setActivo(dto.activo());
        }

        try {
            Usuario saved = usuarioRepository.saveAndFlush(entity);
            return UsuarioResponse.from(saved);
        } catch (DataIntegrityViolationException ex) {
            throw ex;
        }
    }

    @Transactional
    public UsuarioResponse desactivar(Long id, String actorUsername) {
        Usuario entity = usuarioRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + id));
        if (Boolean.TRUE.equals(entity.getActivo())
                && entity.getRol() == Rol.ADMIN
                && esUltimoAdminActivo(entity)) {
            throw new LastAdminException();
        }
        entity.setActivo(false);
        Usuario saved = usuarioRepository.saveAndFlush(entity);
        log.info("usuario desactivado id={} by={}", entity.getId(), actorUsername);
        return UsuarioResponse.from(saved);
    }

    private boolean esUltimoAdminActivo(Usuario target) {
        if (target.getRol() != Rol.ADMIN || !Boolean.TRUE.equals(target.getActivo())) {
            return false;
        }
        return usuarioRepository.countByRolAndActivoTrue(Rol.ADMIN) <= 1;
    }

    private DuplicateUsuarioException duplicate(String field, String message) {
        return new DuplicateUsuarioException(field, message);
    }
}
