package com.clinica.turnos.web.mvc;

import com.clinica.turnos.application.dto.AgendaResponse;
import com.clinica.turnos.application.dto.ProfesionalResponse;
import com.clinica.turnos.application.dto.TurnoQueryParams;
import com.clinica.turnos.application.dto.TurnoResponse;
import com.clinica.turnos.application.exception.TurnoInvalidoException;
import com.clinica.turnos.application.service.ProfesionalService;
import com.clinica.turnos.application.service.TurnoService;
import com.clinica.turnos.domain.model.Profesional;
import com.clinica.turnos.domain.repository.ProfesionalRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

/**
 * Agenda SSR (C-11 tasks 2.1/2.2): vista día (default hoy TZ
 * America/Argentina/Buenos_Aires) y semana (rango 7 días), filtro
 * profesional+fecha, orden por hora_inicio, huecos libres vía
 * TurnoService.agenda (C-09). Scoping por rol vive en el servicio:
 * PACIENTE solo suyos, ODONTOLOGO solo propia, resto todo.
 */
@Controller
public class AgendaMvcController extends MvcBase {

    private final TurnoService turnoService;
    private final ProfesionalService profesionalService;
    private final ProfesionalRepository profesionalRepository;

    public AgendaMvcController(TurnoService turnoService,
                               ProfesionalService profesionalService,
                               ProfesionalRepository profesionalRepository) {
        this.turnoService = turnoService;
        this.profesionalService = profesionalService;
        this.profesionalRepository = profesionalRepository;
    }

    /** Un día de agenda para la vista semana. */
    public record DiaAgenda(LocalDate fecha, List<TurnoResponse> turnos, List<LocalTime> libres) {
    }

    @GetMapping("/agenda")
    @PreAuthorize("isAuthenticated()")
    public ModelAndView agenda(
            @RequestParam(required = false) String fecha,
            @RequestParam(required = false) String profesional,
            @RequestParam(required = false, defaultValue = "dia") String vista) {
        LocalDate dia;
        try {
            dia = fecha == null || fecha.isBlank() ? LocalDate.now(ZONA) : LocalDate.parse(fecha);
        } catch (DateTimeParseException ex) {
            ModelAndView mv = layout("error/400", "Agenda", HttpStatus.BAD_REQUEST);
            mv.addObject("mensaje", "Solicitud inválida");
            mv.addObject("fieldErrors", Map.of("fecha", "formato de fecha inválido (AAAA-MM-DD)"));
            mv.addObject("detalle", "formato de fecha inválido (AAAA-MM-DD)");
            return mv;
        }
        Long profId = parsearLong(profesional, "profesional");
        if (profId == null && "ODONTOLOGO".equals(rolName())) {
            profId = profesionalRepository.findByUsuarioId(userId())
                    .map(Profesional::getId).orElse(null);
        }

        List<ProfesionalResponse> profesionales = profesionalService
                .listar(null, true, PageRequest.of(0, 100)).getContent();

        if ("semana".equalsIgnoreCase(vista)) {
            List<DiaAgenda> dias = new ArrayList<>(7);
            for (int i = 0; i < 7; i++) {
                LocalDate d = dia.plusDays(i);
                if (profId != null) {
                    AgendaResponse ag = turnoService.agenda(profId, d);
                    dias.add(new DiaAgenda(d, ag.ocupados(), ag.libres()));
                } else {
                    var pagina = turnoService.listar(
                            new TurnoQueryParams(null, d, null, null, null), 0, 100);
                    List<TurnoResponse> ordenados = pagina.content().stream()
                            .sorted(Comparator.comparing(TurnoResponse::horaInicio))
                            .toList();
                    dias.add(new DiaAgenda(d, ordenados, List.of()));
                }
            }
            ModelAndView mv = layout("agenda", "Agenda semanal");
            mv.addObject("modo", "semana");
            mv.addObject("fecha", dia.toString());
            mv.addObject("fechaFin", dia.plusDays(6).toString());
            mv.addObject("dias", dias);
            mv.addObject("profesionalId", profId);
            mv.addObject("profesionales", profesionales);
            return mv;
        }

        // Vista día.
        List<TurnoResponse> turnos;
        List<LocalTime> libres;
        if (profId != null) {
            AgendaResponse ag = turnoService.agenda(profId, dia);
            turnos = ag.ocupados();
            libres = ag.libres();
        } else {
            var pagina = turnoService.listar(
                    new TurnoQueryParams(null, dia, null, null, null), 0, 100);
            turnos = pagina.content().stream()
                    .sorted(Comparator.comparing(TurnoResponse::horaInicio))
                    .toList();
            libres = List.of();
        }
        ModelAndView mv = layout("agenda", "Agenda del día");
        mv.addObject("modo", "dia");
        mv.addObject("fecha", dia.toString());
        mv.addObject("turnos", turnos);
        mv.addObject("libres", libres);
        mv.addObject("profesionalId", profId);
        mv.addObject("profesionales", profesionales);
        return mv;
    }

    /**
     * Mis turnos (destino post-login de PACIENTE): próximos 31 días con
     * scoping del servicio (PACIENTE fuerza su ficha).
     */
    @GetMapping("/mis-turnos")
    @PreAuthorize("isAuthenticated()")
    public ModelAndView misTurnos() {
        LocalDate hoy = LocalDate.now(ZONA);
        var pagina = turnoService.listar(
                new TurnoQueryParams(null, null, hoy, hoy.plusDays(30), null), 0, 100);
        ModelAndView mv = layout("mis-turnos", "Mis turnos");
        mv.addObject("turnos", pagina.content());
        mv.addObject("fecha", hoy.toString());
        return mv;
    }

    private Long parsearLong(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(valor);
        } catch (NumberFormatException ex) {
            throw new TurnoInvalidoException(campo, "valor numérico inválido");
        }
    }
}
