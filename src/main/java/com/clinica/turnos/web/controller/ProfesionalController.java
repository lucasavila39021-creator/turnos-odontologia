package com.clinica.turnos.web.controller;

import com.clinica.turnos.application.dto.CreateProfesionalRequest;
import com.clinica.turnos.application.dto.ProfesionalResponse;
import com.clinica.turnos.application.dto.AgendaResponse;
import com.clinica.turnos.application.dto.UpdateProfesionalRequest;
import com.clinica.turnos.application.exception.TurnoInvalidoException;
import com.clinica.turnos.application.service.ProfesionalService;
import com.clinica.turnos.application.service.TurnoService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
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
 * ABM de profesionales (C-07, RN-PR-01/02/04).
 * Thin controller: delega en ProfesionalService; nunca expone la entidad.
 * POST/PATCH: solo ADMIN. GET lista/detalle: cualquier rol autenticado.
 */
@RestController
@RequestMapping("/api/profesionales")
public class ProfesionalController {

    private final ProfesionalService profesionalService;
    private final TurnoService turnoService;

    public ProfesionalController(ProfesionalService profesionalService,
                                 TurnoService turnoService) {
        this.profesionalService = profesionalService;
        this.turnoService = turnoService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProfesionalResponse> crear(@Valid @RequestBody CreateProfesionalRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(profesionalService.crear(req));
    }

    @GetMapping
    public ResponseEntity<Page<ProfesionalResponse>> listar(
            @RequestParam(required = false, name = "especialidad") Long especialidadId,
            @RequestParam(required = false) Boolean activo,
            @PageableDefault(size = 20, sort = {"apellido", "nombre"},
                    direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(profesionalService.listar(especialidadId, activo, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProfesionalResponse> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(profesionalService.obtener(id));
    }

    /**
     * Agenda diaria del profesional (C-09): ocupados RESERVADO + libres.
     * Sin fecha → hoy en America/Argentina/Buenos_Aires (servicio).
     * Scoping por rol en el servicio: ODONTOLOGO solo la propia (403 otra).
     */
    @GetMapping("/{id}/agenda")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AgendaResponse> agenda(
            @PathVariable Long id,
            @RequestParam(required = false) String fecha) {
        LocalDate dia = null;
        if (fecha != null && !fecha.isBlank()) {
            try {
                dia = LocalDate.parse(fecha);
            } catch (DateTimeParseException ex) {
                throw new TurnoInvalidoException("fecha", "formato de fecha inválido (AAAA-MM-DD)");
            }
        }
        return ResponseEntity.ok(turnoService.agenda(id, dia));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProfesionalResponse> actualizar(
            @PathVariable Long id, @Valid @RequestBody UpdateProfesionalRequest req) {
        return ResponseEntity.ok(profesionalService.actualizar(id, req));
    }

    @PatchMapping("/{id}/desactivar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProfesionalResponse> desactivar(@PathVariable Long id) {
        return ResponseEntity.ok(profesionalService.desactivar(id));
    }
}
