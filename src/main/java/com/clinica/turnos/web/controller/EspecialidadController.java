package com.clinica.turnos.web.controller;

import com.clinica.turnos.application.dto.CreateEspecialidadRequest;
import com.clinica.turnos.application.dto.EspecialidadResponse;
import com.clinica.turnos.application.dto.ProfesionalResponse;
import com.clinica.turnos.application.dto.UpdateEspecialidadRequest;
import com.clinica.turnos.application.service.EspecialidadService;
import com.clinica.turnos.application.service.ProfesionalService;
import com.clinica.turnos.domain.repository.EspecialidadRepository;
import jakarta.validation.Valid;
import java.util.NoSuchElementException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ABM de especialidades (C-06, RN-PR-03, RN-AU-02/05).
 * Thin controller: delega en EspecialidadService; nunca expone la entidad.
 * GET lista y GET profesionales: solo autenticado. POST/PATCH: ADMIN.
 * GET /{id}/profesionales lista profesionales reales desde C-07.
 */
@RestController
@RequestMapping("/api/especialidades")
public class EspecialidadController {

    private final EspecialidadService especialidadService;
    private final EspecialidadRepository especialidadRepository;
    private final ProfesionalService profesionalService;

    public EspecialidadController(EspecialidadService especialidadService,
                                  EspecialidadRepository especialidadRepository,
                                  ProfesionalService profesionalService) {
        this.especialidadService = especialidadService;
        this.especialidadRepository = especialidadRepository;
        this.profesionalService = profesionalService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EspecialidadResponse> crear(@Valid @RequestBody CreateEspecialidadRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(especialidadService.crear(req));
    }

    @GetMapping
    public ResponseEntity<Page<EspecialidadResponse>> listar(
            @RequestParam(required = false) Boolean activa,
            @PageableDefault(size = 20, sort = "nombre", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(especialidadService.listar(activa, pageable));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EspecialidadResponse> actualizar(
            @PathVariable Long id, @Valid @RequestBody UpdateEspecialidadRequest req) {
        return ResponseEntity.ok(especialidadService.actualizar(id, req));
    }

    @GetMapping("/{id}/profesionales")
    public ResponseEntity<Page<ProfesionalResponse>> profesionales(
            @PathVariable Long id,
            @PageableDefault(size = 20, sort = {"apellido", "nombre"},
                    direction = Sort.Direction.ASC) Pageable pageable) {
        if (!especialidadRepository.existsById(id)) {
            throw new NoSuchElementException("Especialidad no encontrada: " + id);
        }
        return ResponseEntity.ok(profesionalService.listarPorEspecialidad(id, pageable));
    }
}
