package com.clinica.turnos.web.controller;

import com.clinica.turnos.application.dto.CreatePacienteRequest;
import com.clinica.turnos.application.dto.PacienteResponse;
import com.clinica.turnos.application.dto.TurnoResponse;
import com.clinica.turnos.application.dto.UpdatePacienteRequest;
import com.clinica.turnos.application.service.PacienteService;
import com.clinica.turnos.application.service.TurnoService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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
 * ABM de pacientes (C-05, RN-PA-01..04, RN-AU-02/03).
 * Thin controller: delega en PacienteService; nunca expone la entidad.
 * Sin DELETE físico en v1 (DELETE → 404/405).
 */
@RestController
@RequestMapping("/api/pacientes")
public class PacienteController {

    private final PacienteService pacienteService;
    private final TurnoService turnoService;

    public PacienteController(PacienteService pacienteService, TurnoService turnoService) {
        this.pacienteService = pacienteService;
        this.turnoService = turnoService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SECRETARIA','ADMIN')")
    public ResponseEntity<PacienteResponse> crear(@Valid @RequestBody CreatePacienteRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pacienteService.crear(req));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SECRETARIA','ADMIN','ODONTOLOGO')")
    public ResponseEntity<Page<PacienteResponse>> listar(
            @RequestParam(required = false) String dni,
            @RequestParam(required = false) String apellido,
            @PageableDefault(size = 20, sort = {"apellido", "nombre"},
                    direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(pacienteService.buscar(dni, apellido, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SECRETARIA','ADMIN','ODONTOLOGO') or @authz.isPacienteOwner(#id)")
    public ResponseEntity<PacienteResponse> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(pacienteService.obtener(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('SECRETARIA','ADMIN') or @authz.isPacienteOwner(#id)")
    public ResponseEntity<PacienteResponse> actualizar(
            @PathVariable Long id, @Valid @RequestBody UpdatePacienteRequest req) {
        return ResponseEntity.ok(pacienteService.actualizar(id, req));
    }

    /**
     * Historial de turnos de la ficha (C-12: cableado real sobre
     * TurnoService.historialPorPaciente; antes placeholder vacío).
     * 404 si la ficha no existe; paginado en memoria (v1, ficha acotada).
     */
    @GetMapping("/{id}/turnos")
    @PreAuthorize("hasAnyRole('SECRETARIA','ADMIN','ODONTOLOGO') or @authz.isPacienteOwner(#id)")
    public ResponseEntity<Page<TurnoResponse>> historial(
            @PathVariable Long id,
            @PageableDefault(size = 20) Pageable pageable) {
        pacienteService.obtener(id);
        List<TurnoResponse> todos = turnoService.historialPorPaciente(id);
        int from = Math.min(pageable.getOffset() > Integer.MAX_VALUE
                ? Integer.MAX_VALUE : (int) pageable.getOffset(), todos.size());
        int to = Math.min(from + pageable.getPageSize(), todos.size());
        return ResponseEntity.ok(new PageImpl<>(todos.subList(from, to), pageable, todos.size()));
    }
}
