package com.clinica.turnos.web.mvc;

import com.clinica.turnos.application.dto.AgendaResponse;
import com.clinica.turnos.application.dto.EspecialidadResponse;
import com.clinica.turnos.application.dto.PacienteResponse;
import com.clinica.turnos.application.dto.ProfesionalResponse;
import com.clinica.turnos.application.dto.TurnoCreateRequest;
import com.clinica.turnos.application.dto.TurnoResponse;
import com.clinica.turnos.application.exception.TurnoInvalidoException;
import com.clinica.turnos.application.exception.TurnoOcupadoException;
import com.clinica.turnos.application.service.EspecialidadService;
import com.clinica.turnos.application.service.PacienteService;
import com.clinica.turnos.application.service.ProfesionalService;
import com.clinica.turnos.application.service.TurnoService;
import com.clinica.turnos.domain.model.EstadoTurno;
import com.clinica.turnos.security.AuthzService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

/**
 * Turnos SSR (C-11 tasks 3.1/3.2): alta con validación inline + repoblado
 * tras error, detalle con botones por rol+estado, y POSTs que delegan en
 * TurnoService con los mismos guards que C-10 (solo RESERVADO transiciona).
 * Nunca 500 por input inválido: todo parse va a fieldErrors (400 UX).
 */
@Controller
public class TurnoMvcController extends MvcBase {

    private final TurnoService turnoService;
    private final PacienteService pacienteService;
    private final ProfesionalService profesionalService;
    private final EspecialidadService especialidadService;
    private final AuthzService authzService;

    public TurnoMvcController(TurnoService turnoService,
                              PacienteService pacienteService,
                              ProfesionalService profesionalService,
                              EspecialidadService especialidadService,
                              AuthzService authzService) {
        this.turnoService = turnoService;
        this.pacienteService = pacienteService;
        this.profesionalService = profesionalService;
        this.especialidadService = especialidadService;
        this.authzService = authzService;
    }

    @GetMapping("/turnos/nuevo")
    @PreAuthorize("hasAnyRole('PACIENTE','SECRETARIA','ADMIN')")
    public ModelAndView nuevo(
            @RequestParam(required = false) String paciente,
            @RequestParam(required = false) String profesional,
            @RequestParam(required = false) String fecha) {
        TurnoForm form = new TurnoForm();
        form.setPacienteId(paciente);
        form.setProfesionalId(profesional);
        form.setFecha(fecha);
        ModelAndView mv = layout("turno-form", "Nuevo turno");
        mv.addObject("form", form);
        mv.addObject("fieldErrors", Map.of());
        cargarApoyos(mv, form);
        return mv;
    }

    @PostMapping("/turnos/nuevo")
    @PreAuthorize("hasAnyRole('PACIENTE','SECRETARIA','ADMIN')")
    public ModelAndView crear(@ModelAttribute TurnoForm form) {
        Map<String, String> errores = new LinkedHashMap<>();
        Long pacienteId = parsearLong(form.getPacienteId(), "pacienteId", "paciente obligatorio", errores);
        Long profesionalId = parsearLong(form.getProfesionalId(), "profesionalId", "profesional obligatorio", errores);
        Long especialidadId = parsearLong(form.getEspecialidadId(), "especialidadId", "especialidad obligatoria", errores);
        LocalDate fecha = parsearFecha(form.getFecha(), errores);
        LocalTime hora = parsearHora(form.getHoraInicio(), errores);

        ModelAndView mv = layout("turno-form", "Nuevo turno");
        mv.addObject("form", form);
        if (!errores.isEmpty()) {
            mv.addObject("fieldErrors", errores);
            mv.setStatus(HttpStatus.BAD_REQUEST);
            cargarApoyos(mv, form);
            return mv;
        }
        try {
            TurnoResponse creado = turnoService.crear(new TurnoCreateRequest(
                    pacienteId, profesionalId, especialidadId, fecha, hora));
            return new ModelAndView("redirect:/turnos/" + creado.id());
        } catch (TurnoOcupadoException ex) {
            mv.addObject("fieldErrors", Map.of("horaInicio",
                    "Horario ocupado" + (ex.getSugeridos().isEmpty() ? ""
                            : ", probá " + String.join(", ", ex.getSugeridos()))));
            mv.addObject("sugeridos", ex.getSugeridos());
            mv.setStatus(HttpStatus.CONFLICT);
            cargarApoyos(mv, form);
            return mv;
        } catch (TurnoInvalidoException ex) {
            mv.addObject("fieldErrors", Map.of(ex.getField(), ex.getMessage()));
            mv.setStatus(HttpStatus.BAD_REQUEST);
            cargarApoyos(mv, form);
            return mv;
        } catch (NoSuchElementException ex) {
            mv.addObject("fieldErrors", Map.of("form", ex.getMessage()));
            mv.setStatus(HttpStatus.NOT_FOUND);
            cargarApoyos(mv, form);
            return mv;
        }
    }

    @GetMapping("/turnos/{id}")
    @PreAuthorize("isAuthenticated()")
    public ModelAndView detalle(@PathVariable Long id) {
        TurnoResponse turno = turnoService.detalle(id);
        return vistaDetalle(turno, Map.of(), null);
    }

    @PostMapping("/turnos/{id}/cancelar")
    @PreAuthorize("hasAnyRole('PACIENTE','SECRETARIA','ADMIN')")
    public ModelAndView cancelar(@PathVariable Long id,
                                 @RequestParam(required = false) String motivo) {
        try {
            TurnoResponse turno = turnoService.cancelar(id, motivo);
            return new ModelAndView("redirect:/turnos/" + turno.id());
        } catch (TurnoInvalidoException ex) {
            return vistaDetalle(turnoService.detalle(id),
                    Map.of("estado", ex.getMessage()), HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/turnos/{id}/atendido")
    @PreAuthorize("hasAnyRole('SECRETARIA','ODONTOLOGO','ADMIN')")
    public ModelAndView atendido(@PathVariable Long id) {
        try {
            TurnoResponse turno = turnoService.atendido(id);
            return new ModelAndView("redirect:/turnos/" + turno.id());
        } catch (TurnoInvalidoException ex) {
            return vistaDetalle(turnoService.detalle(id),
                    Map.of("estado", ex.getMessage()), HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/turnos/{id}/reprogramar")
    @PreAuthorize("hasAnyRole('SECRETARIA','ADMIN')")
    public ModelAndView reprogramar(@PathVariable Long id,
                                    @RequestParam(required = false) String nuevaFecha,
                                    @RequestParam(required = false) String nuevaHora) {
        Map<String, String> errores = new LinkedHashMap<>();
        LocalDate fecha = null;
        LocalTime hora = null;
        try {
            fecha = nuevaFecha == null || nuevaFecha.isBlank() ? null : LocalDate.parse(nuevaFecha);
            if (fecha == null) {
                errores.put("nuevaFecha", "fecha obligatoria");
            }
        } catch (DateTimeParseException ex) {
            errores.put("nuevaFecha", "formato de fecha inválido (AAAA-MM-DD)");
        }
        try {
            hora = nuevaHora == null || nuevaHora.isBlank() ? null : LocalTime.parse(nuevaHora);
            if (hora == null) {
                errores.put("nuevaHora", "hora obligatoria");
            }
        } catch (DateTimeParseException ex) {
            errores.put("nuevaHora", "formato de hora inválido (HH:MM)");
        }
        if (!errores.isEmpty()) {
            return vistaDetalle(turnoService.detalle(id), errores, HttpStatus.BAD_REQUEST);
        }
        try {
            TurnoResponse nuevo = turnoService.reprogramar(id, fecha, hora);
            return new ModelAndView("redirect:/turnos/" + nuevo.id());
        } catch (TurnoOcupadoException ex) {
            Map<String, String> err = Map.of("nuevaHora",
                    "Horario ocupado" + (ex.getSugeridos().isEmpty() ? ""
                            : ", probá " + String.join(", ", ex.getSugeridos())));
            ModelAndView mv = vistaDetalle(turnoService.detalle(id), err, HttpStatus.CONFLICT);
            mv.addObject("sugeridos", ex.getSugeridos());
            return mv;
        } catch (TurnoInvalidoException ex) {
            return vistaDetalle(turnoService.detalle(id),
                    Map.of(ex.getField(), ex.getMessage()), HttpStatus.BAD_REQUEST);
        }
    }

    private ModelAndView vistaDetalle(TurnoResponse turno, Map<String, String> errores, HttpStatus status) {
        ModelAndView mv = layout("turno-detalle", "Turno #" + turno.id(), status);
        mv.addObject("turno", turno);
        mv.addObject("fieldErrors", errores);
        boolean reservado = turno.estado() == EstadoTurno.RESERVADO;
        boolean staff = hasAny("SECRETARIA", "ADMIN");
        boolean propioPaciente = authzService.isPropioPaciente(turno.paciente().id());
        boolean propioProfesional = authzService.isPropioProfesional(turno.profesional().id());
        mv.addObject("canCancelar", reservado
                && (staff || ("PACIENTE".equals(rolName()) && propioPaciente)));
        mv.addObject("canReprogramar", reservado && staff);
        mv.addObject("canAtendido", reservado
                && (staff || ("ODONTOLOGO".equals(rolName()) && propioProfesional)));
        return mv;
    }

    /**
     * Selects de apoyo: profesionales activos, especialidades (filtradas
     * server-side por profesional RN-PR-05 cuando hay uno elegido),
     * pacientes (búsqueda DNI/apellido o primera página, degradación sin JS)
     * y slots libres del día elegido.
     */
    private void cargarApoyos(ModelAndView mv, TurnoForm form) {
        List<ProfesionalResponse> profesionales = profesionalService
                .listar(null, true, PageRequest.of(0, 100)).getContent();
        mv.addObject("profesionales", profesionales);

        Long profId = tryLong(form.getProfesionalId());
        List<EspecialidadResponse> especialidades;
        if (profId != null) {
            try {
                especialidades = profesionalService.obtener(profId).especialidades().stream()
                        .map(e -> new EspecialidadResponse(e.id(), e.nombre(), null, true, null, null))
                        .toList();
            } catch (RuntimeException ex) {
                especialidades = List.of();
            }
        } else {
            especialidades = especialidadService
                    .listar(true, PageRequest.of(0, 100)).getContent();
        }
        mv.addObject("especialidades", especialidades);

        String q = form.getPacienteBusqueda();
        List<PacienteResponse> pacientes;
        try {
            if (q != null && !q.isBlank()) {
                boolean esDni = q.trim().matches("[0-9]+");
                pacientes = pacienteService.buscar(esDni ? q.trim() : null, esDni ? null : q.trim(),
                        PageRequest.of(0, 20, Sort.by("apellido", "nombre"))).getContent();
            } else {
                pacientes = pacienteService.buscar(null, null,
                        PageRequest.of(0, 20, Sort.by("apellido", "nombre"))).getContent();
            }
        } catch (RuntimeException ex) {
            pacientes = List.of();
        }
        mv.addObject("pacientes", pacientes);

        List<LocalTime> slots = List.of();
        LocalDate fecha = tryFecha(form.getFecha());
        if (profId != null && fecha != null) {
            try {
                AgendaResponse ag = turnoService.agenda(profId, fecha);
                slots = new ArrayList<>(ag.libres());
            } catch (RuntimeException ex) {
                slots = List.of();
            }
        }
        mv.addObject("slots", slots);
    }

    private Long parsearLong(String valor, String campo, String obligatorio, Map<String, String> errores) {
        if (valor == null || valor.isBlank()) {
            errores.put(campo, obligatorio);
            return null;
        }
        try {
            return Long.valueOf(valor.trim());
        } catch (NumberFormatException ex) {
            errores.put(campo, "valor numérico inválido");
            return null;
        }
    }

    private LocalDate parsearFecha(String valor, Map<String, String> errores) {
        if (valor == null || valor.isBlank()) {
            errores.put("fecha", "fecha obligatoria");
            return null;
        }
        try {
            return LocalDate.parse(valor.trim());
        } catch (DateTimeParseException ex) {
            errores.put("fecha", "formato de fecha inválido (AAAA-MM-DD)");
            return null;
        }
    }

    private LocalTime parsearHora(String valor, Map<String, String> errores) {
        if (valor == null || valor.isBlank()) {
            errores.put("horaInicio", "hora obligatoria");
            return null;
        }
        try {
            return LocalTime.parse(valor.trim());
        } catch (DateTimeParseException ex) {
            errores.put("horaInicio", "formato de hora inválido (HH:MM)");
            return null;
        }
    }

    private Long tryLong(String valor) {
        try {
            return valor == null || valor.isBlank() ? null : Long.valueOf(valor.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private LocalDate tryFecha(String valor) {
        try {
            return valor == null || valor.isBlank() ? null : LocalDate.parse(valor.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
