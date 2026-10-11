package com.clinica.turnos.web.mvc;

import com.clinica.turnos.application.dto.CreatePacienteRequest;
import com.clinica.turnos.application.dto.PacienteResponse;
import com.clinica.turnos.application.dto.TurnoResponse;
import com.clinica.turnos.application.exception.DuplicateDniException;
import com.clinica.turnos.application.service.PacienteService;
import com.clinica.turnos.application.service.TurnoService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

/**
 * Pacientes SSR (C-11 tasks 4.1/4.2): buscador por DNI/apellido + alta
 * inline (Flujo 4: alta → redirect a /turnos/nuevo con paciente
 * preseleccionado; DNI duplicado → error + link a la ficha), y ficha con
 * historial de turnos (PACIENTE solo la propia).
 */
@Controller
public class PacienteMvcController extends MvcBase {

    private final PacienteService pacienteService;
    private final TurnoService turnoService;

    public PacienteMvcController(PacienteService pacienteService, TurnoService turnoService) {
        this.pacienteService = pacienteService;
        this.turnoService = turnoService;
    }

    @GetMapping("/pacientes")
    @PreAuthorize("hasAnyRole('SECRETARIA','ADMIN','ODONTOLOGO')")
    public ModelAndView listar(
            @RequestParam(required = false) String dni,
            @RequestParam(required = false) String apellido) {
        ModelAndView mv = layout("pacientes", "Pacientes");
        var pagina = pacienteService.buscar(
                vacioANull(dni), vacioANull(apellido),
                PageRequest.of(0, 20, Sort.by("apellido", "nombre")));
        mv.addObject("resultados", pagina.getContent());
        mv.addObject("dni", dni != null ? dni : "");
        mv.addObject("apellido", apellido != null ? apellido : "");
        mv.addObject("fieldErrors", Map.of());
        return mv;
    }

    @PostMapping("/pacientes")
    @PreAuthorize("hasAnyRole('SECRETARIA','ADMIN')")
    public ModelAndView alta(
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String apellido,
            @RequestParam(required = false) String dni,
            @RequestParam(required = false) String telefono,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String qDni,
            @RequestParam(required = false) String qApellido) {
        Map<String, String> errores = new LinkedHashMap<>();
        if (nombre == null || nombre.isBlank()) {
            errores.put("nombre", "nombre obligatorio");
        }
        if (apellido == null || apellido.isBlank()) {
            errores.put("apellido", "apellido obligatorio");
        }
        if (dni == null || dni.isBlank()) {
            errores.put("dni", "dni obligatorio");
        }
        ModelAndView mv = layout("pacientes", "Pacientes");
        mv.addObject("dni", qDni != null ? qDni : "");
        mv.addObject("apellido", qApellido != null ? qApellido : "");
        mv.addObject("nuevoNombre", nombre != null ? nombre : "");
        mv.addObject("nuevoApellido", apellido != null ? apellido : "");
        mv.addObject("nuevoDni", dni != null ? dni : "");
        mv.addObject("nuevoTelefono", telefono != null ? telefono : "");
        mv.addObject("nuevoEmail", email != null ? email : "");
        if (!errores.isEmpty()) {
            var pagina = pacienteService.buscar(vacioANull(qDni), vacioANull(qApellido),
                    PageRequest.of(0, 20, Sort.by("apellido", "nombre")));
            mv.addObject("resultados", pagina.getContent());
            mv.addObject("fieldErrors", errores);
            mv.setStatus(HttpStatus.BAD_REQUEST);
            return mv;
        }
        try {
            PacienteResponse creado = pacienteService.crear(new CreatePacienteRequest(
                    nombre.trim(), apellido.trim(), dni.trim(),
                    vacioANull(telefono), vacioANull(email), null, null));
            // Flujo 4: continuar al alta de turno con el paciente preseleccionado.
            return new ModelAndView("redirect:/turnos/nuevo?paciente=" + creado.id());
        } catch (DuplicateDniException ex) {
            var pagina = pacienteService.buscar(vacioANull(qDni), vacioANull(qApellido),
                    PageRequest.of(0, 20, Sort.by("apellido", "nombre")));
            mv.addObject("resultados", pagina.getContent());
            mv.addObject("fieldErrors", Map.of("dni", "DNI ya registrado"));
            mv.addObject("pacienteIdExistente", ex.getPacienteId());
            mv.setStatus(HttpStatus.CONFLICT);
            return mv;
        }
    }

    @GetMapping("/pacientes/{id}")
    @PreAuthorize("hasAnyRole('SECRETARIA','ADMIN','ODONTOLOGO') or @authz.isPacienteOwner(#id)")
    public ModelAndView ficha(@PathVariable Long id) {
        PacienteResponse paciente;
        try {
            paciente = pacienteService.obtener(id);
        } catch (NoSuchElementException ex) {
            // PACIENTE fisgoneando ficha ajena: 403 sin revelar existencia.
            if ("PACIENTE".equals(rolName())) {
                ModelAndView mv = layout("error/403", "No autorizado", HttpStatus.FORBIDDEN);
                mv.addObject("mensaje", "No autorizado");
                mv.addObject("detalle", "No tiene permiso para ver esta ficha");
                return mv;
            }
            throw ex;
        }
        List<TurnoResponse> historial = turnoService.historialPorPaciente(id);
        ModelAndView mv = layout("paciente-detalle", "Paciente " + paciente.apellido());
        mv.addObject("paciente", paciente);
        mv.addObject("historial", historial);
        return mv;
    }

    private String vacioANull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
