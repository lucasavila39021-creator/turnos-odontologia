package com.clinica.turnos.web.controller;

import com.clinica.turnos.application.dto.CreateUsuarioRequest;
import com.clinica.turnos.application.dto.UpdateUsuarioRequest;
import com.clinica.turnos.application.dto.UsuarioResponse;
import com.clinica.turnos.application.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ABM de usuarios solo-ADMIN (C-04).
 * Thin controller: delega en UsuarioService; nunca expone la entidad.
 */
@RestController
@RequestMapping("/api/usuarios")
@PreAuthorize("hasRole('ADMIN')")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public ResponseEntity<Page<UsuarioResponse>> listar(
            @PageableDefault(size = 20, sort = "username", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(usuarioService.listar(pageable));
    }

    @PostMapping
    public ResponseEntity<UsuarioResponse> crear(
            @Valid @RequestBody CreateUsuarioRequest req, Authentication authentication) {
        UsuarioResponse created = usuarioService.crear(req, actorOf(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<UsuarioResponse> actualizar(
            @PathVariable Long id, @Valid @RequestBody UpdateUsuarioRequest req, Authentication authentication) {
        return ResponseEntity.ok(usuarioService.actualizar(id, req, actorOf(authentication)));
    }

    @PatchMapping("/{id}/desactivar")
    public ResponseEntity<UsuarioResponse> desactivar(
            @PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(usuarioService.desactivar(id, actorOf(authentication)));
    }

    private String actorOf(Authentication authentication) {
        return authentication != null ? authentication.getName() : "?";
    }
}
