package com.clinica.turnos.web.controller;

import com.clinica.turnos.application.dto.AgendaResponse;
import com.clinica.turnos.application.dto.TurnoCancelarRequest;
import com.clinica.turnos.application.dto.TurnoCreateRequest;
import com.clinica.turnos.application.dto.TurnoListResponse;
import com.clinica.turnos.application.dto.TurnoQueryParams;
import com.clinica.turnos.application.dto.TurnoReprogramarRequest;
import com.clinica.turnos.application.dto.TurnoResponse;
import com.clinica.turnos.application.exception.TurnoInvalidoException;
import com.clinica.turnos.application.service.TurnoService;
import com.clinica.turnos.domain.model.EstadoTurno;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
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
 * Reserva (C-08) + consulta y agenda (C-09, RN-AU-01..04) + transiciones
 * (C-10, RN-TU-06/07/08): cancelar/atendido/reprogramar.
 * Thin controller: delega en TurnoService; nunca expone la entidad.
 * PACIENTE/SECRETARIA/ADMIN crean; ODONTOLOGO cae a 403 por denegación.
 * Los 3 GET exigen sesión (401 sin auth); el scoping por rol vive en el
 * servicio (D-01): PACIENTE solo suyos, ODONTOLOGO solo propia.
 */
@RestController
@RequestMapping("/api/turnos")
public class TurnoController {

    private final TurnoService turnoService;

    public TurnoController(TurnoService turnoService) {
        this.turnoService = turnoService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PACIENTE','SECRETARIA','ADMIN')")
    public ResponseEntity<TurnoResponse> crear(@Valid @RequestBody TurnoCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(turnoService.crear(req));
    }

    /**
     * Lista con filtros combinables + paginación. Sin fecha/desde/hasta el
     * servicio filtra por hoy en America/Argentina/Buenos_Aires.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TurnoListResponse> listar(
            @RequestParam(required = false) String profesional,
            @RequestParam(required = false) String fecha,
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta,
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        TurnoQueryParams params = new TurnoQueryParams(
                parsearLong(profesional, "profesional"),
                parsearFecha(fecha),
                parsearFecha(desde),
                parsearFecha(hasta),
                parsearEstado(estado));
        return ResponseEntity.ok(turnoService.listar(params, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TurnoResponse> detalle(@PathVariable Long id) {
        return ResponseEntity.ok(turnoService.detalle(id));
    }

    /**
     * Cancelar (C-10, RN-TU-06/07, Flujo 2): RESERVADO → CANCELADO.
     * Body opcional {motivo?}; ODONTOLOGO cae a 403 por denegación.
     */
    @PatchMapping("/{id}/cancelar")
    @PreAuthorize("hasAnyRole('PACIENTE','SECRETARIA','ADMIN')")
    public ResponseEntity<TurnoResponse> cancelar(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) TurnoCancelarRequest req) {
        String motivo = req != null ? req.motivo() : null;
        return ResponseEntity.ok(turnoService.cancelar(id, motivo));
    }

    /**
     * Marcar atendido (C-10, US-034): RESERVADO → ATENDIDO, sin body.
     * PACIENTE cae a 403 por denegación; ODONTOLOGO solo propia (servicio).
     */
    @PatchMapping("/{id}/atendido")
    @PreAuthorize("hasAnyRole('SECRETARIA','ODONTOLOGO','ADMIN')")
    public ResponseEntity<TurnoResponse> atendido(@PathVariable Long id) {
        return ResponseEntity.ok(turnoService.atendido(id));
    }

    /**
     * Reprogramar (C-10, RN-TU-08, Flujo 5): cancela original + crea nuevo
     * en una TX; responde 201 con el turno nuevo.
     */
    @PostMapping("/{id}/reprogramar")
    @PreAuthorize("hasAnyRole('SECRETARIA','ADMIN')")
    public ResponseEntity<TurnoResponse> reprogramar(
            @PathVariable Long id,
            @Valid @RequestBody TurnoReprogramarRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(turnoService.reprogramar(id, req.nuevaFecha(), req.nuevaHoraInicio()));
    }

    private static LocalDate parsearFecha(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(valor);
        } catch (DateTimeParseException ex) {
            throw new TurnoInvalidoException("fecha", "formato de fecha inválido (AAAA-MM-DD)");
        }
    }

    private static Long parsearLong(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(valor);
        } catch (NumberFormatException ex) {
            throw new TurnoInvalidoException(campo, "valor numérico inválido");
        }
    }

    private static EstadoTurno parsearEstado(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return EstadoTurno.valueOf(valor.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new TurnoInvalidoException("estado", "estado inválido");
        }
    }
}
