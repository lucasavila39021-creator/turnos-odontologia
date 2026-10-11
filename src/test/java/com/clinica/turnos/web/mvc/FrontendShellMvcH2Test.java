package com.clinica.turnos.web.mvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clinica.turnos.application.dto.CreateEspecialidadRequest;
import com.clinica.turnos.application.dto.CreateProfesionalRequest;
import com.clinica.turnos.application.service.EspecialidadService;
import com.clinica.turnos.application.service.ProfesionalService;
import com.clinica.turnos.domain.model.Paciente;
import com.clinica.turnos.domain.model.Profesional;
import com.clinica.turnos.domain.model.Rol;
import com.clinica.turnos.domain.model.Turno;
import com.clinica.turnos.domain.model.Usuario;
import com.clinica.turnos.domain.repository.EspecialidadRepository;
import com.clinica.turnos.domain.repository.PacienteRepository;
import com.clinica.turnos.domain.repository.ProfesionalEspecialidadRepository;
import com.clinica.turnos.domain.repository.ProfesionalRepository;
import com.clinica.turnos.domain.repository.TurnoRepository;
import com.clinica.turnos.domain.repository.UsuarioRepository;
import com.clinica.turnos.security.RateLimitService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * C-11 verificación HTML (tasks 1.1–5.2): shell por rol, agenda día/semana,
 * alta/detalle de turnos, pacientes + ficha, E2E de Flujos 1/2/4/5/3,
 * responsive/a11y/CSP. Solo MockMvc (sin navegador disponible en el
 * entorno: Playwright no aplica).
 */
@SpringBootTest
@AutoConfigureMockMvc
class FrontendShellMvcH2Test {

    private static final ZoneId TZ = ZoneId.of("America/Argentina/Buenos_Aires");

    @Autowired MockMvc mockMvc;
    @Autowired TurnoRepository turnoRepository;
    @Autowired ProfesionalService profesionalService;
    @Autowired ProfesionalRepository profesionalRepository;
    @Autowired ProfesionalEspecialidadRepository vinculoRepository;
    @Autowired EspecialidadRepository especialidadRepository;
    @Autowired EspecialidadService especialidadService;
    @Autowired PacienteRepository pacienteRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired RateLimitService rateLimitService;

    private Long prof1Id;
    private Long prof2Id;
    private Long espId;
    private Long pacAId;
    private Long pacBId;
    private Long turnoA1Id; // pacA + prof1 09:00 hoy
    private Long turnoBId; // pacB + prof1 10:00 hoy
    private Long turnoA2Id; // pacA + prof2 11:00 hoy
    private LocalDate hoy;

    @BeforeEach
    void setUp() {
        rateLimitService.clear();
        turnoRepository.deleteAll();
        vinculoRepository.deleteAll();
        profesionalRepository.deleteAll();
        especialidadRepository.deleteAll();
        pacienteRepository.deleteAll();
        usuarioRepository.deleteAll();
        usuarioRepository.save(new Usuario("secre1", passwordEncoder.encode("secre1234"),
                "secre@clinica.local", Rol.SECRETARIA, true));
        usuarioRepository.save(new Usuario("admin1", passwordEncoder.encode("admin1234"),
                "admin@clinica.local", Rol.ADMIN, true));
        Long odoUser1 = usuarioRepository.save(new Usuario("odonto1",
                passwordEncoder.encode("odonto1234"), "odonto@clinica.local",
                Rol.ODONTOLOGO, true)).getId();
        Long pacUserA = usuarioRepository.save(new Usuario("pac1",
                passwordEncoder.encode("paciente123"), "pac1@clinica.local",
                Rol.PACIENTE, true)).getId();
        Long pacUserB = usuarioRepository.save(new Usuario("pac2",
                passwordEncoder.encode("paciente123"), "pac2@clinica.local",
                Rol.PACIENTE, true)).getId();
        espId = especialidadService.crear(
                new CreateEspecialidadRequest("Ortodoncia", null)).id();
        prof1Id = profesionalService.crear(new CreateProfesionalRequest(
                "Ana", "Gómez", "M-400", null, null, null, List.of(espId))).id();
        prof2Id = profesionalService.crear(new CreateProfesionalRequest(
                "Luis", "Pérez", "M-401", null, null, null, List.of(espId))).id();
        Profesional f1 = profesionalRepository.findById(prof1Id).orElseThrow();
        f1.setUsuarioId(odoUser1);
        profesionalRepository.saveAndFlush(f1);
        pacAId = pacienteRepository.save(new Paciente("Juan", "Pérez", "11111111",
                null, null, null, pacUserA)).getId();
        pacBId = pacienteRepository.save(new Paciente("María", "López", "22222222",
                null, null, null, pacUserB)).getId();
        hoy = LocalDate.now(TZ);
        turnoA1Id = turnoRepository.save(new Turno(pacAId, prof1Id, espId, hoy,
                LocalTime.of(9, 0), LocalTime.of(9, 30))).getId();
        turnoBId = turnoRepository.save(new Turno(pacBId, prof1Id, espId, hoy,
                LocalTime.of(10, 0), LocalTime.of(10, 30))).getId();
        turnoA2Id = turnoRepository.save(new Turno(pacAId, prof2Id, espId, hoy,
                LocalTime.of(11, 0), LocalTime.of(11, 30))).getId();
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    // 1.2 Shell: redirect raíz.

    @Test
    void raizAnonimoRedirigeLogin() throws Exception {
        mockMvc.perform(get("/").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/login")));
    }

    @Test
    void raizRedirigePorRol() throws Exception {
        mockMvc.perform(get("/").session(login("secre1", "secre1234")))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/agenda")));
        mockMvc.perform(get("/").session(login("pac1", "paciente123")))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/mis-turnos")));
        mockMvc.perform(get("/").session(login("odonto1", "odonto1234")))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/agenda")));
    }

    // 1.2 Menú por rol.

    @Test
    void menuPorRol() throws Exception {
        MockHttpSession secre = login("secre1", "secre1234");
        mockMvc.perform(get("/agenda").session(secre).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Pacientes")))
                .andExpect(content().string(containsString("Nuevo turno")))
                .andExpect(content().string(containsString("Agenda")));

        MockHttpSession odo = login("odonto1", "odonto1234");
        String htmlOdo = mockMvc.perform(get("/agenda").session(odo).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mi agenda")))
                .andReturn().getResponse().getContentAsString();
        MatcherAssert.assertThat(htmlOdo, Matchers.not(containsString("Usuarios")));
        MatcherAssert.assertThat(htmlOdo, Matchers.not(containsString("Pacientes")));

        MockHttpSession admin = login("admin1", "admin1234");
        mockMvc.perform(get("/agenda").session(admin).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Usuarios")));

        MockHttpSession pac = login("pac1", "paciente123");
        String htmlPac = mockMvc.perform(get("/mis-turnos").session(pac).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mis turnos")))
                .andReturn().getResponse().getContentAsString();
        MatcherAssert.assertThat(htmlPac, Matchers.not(containsString("Pacientes")));
    }

    // 1.3 Login mejorado.

    @Test
    void loginMejorado() throws Exception {
        mockMvc.perform(get("/login").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("_csrf")))
                .andExpect(content().string(containsString("for=\"username\"")))
                .andExpect(content().string(containsString("for=\"password\"")))
                .andExpect(content().string(containsString("viewport")));
        mockMvc.perform(get("/login").param("expired", "").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sesi\u00f3n expirada")));
        mockMvc.perform(get("/login").param("error", "").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Credenciales inv\u00e1lidas")));
    }

    @Test
    void sesionAusenteEnAgendaRedirigeLogin() throws Exception {
        mockMvc.perform(get("/agenda").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/login")));
    }

    // 2.1 Agenda día/semana.

    @Test
    void agendaDiaOrdenadaConHuecos() throws Exception {
        MockHttpSession secre = login("secre1", "secre1234");
        String html = mockMvc.perform(get("/agenda")
                        .param("fecha", hoy.toString())
                        .param("profesional", prof1Id.toString())
                        .session(secre).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("P\u00e9rez")))
                .andExpect(content().string(containsString("Ortodoncia")))
                .andExpect(content().string(containsString("RESERVADO")))
                .andExpect(content().string(containsString("Huecos libres")))
                .andReturn().getResponse().getContentAsString();
        MatcherAssert.assertThat("orden cronológico 09:00 antes de 10:00",
                html.indexOf("09:00") < html.indexOf("10:00"), Matchers.is(true));
    }

    @Test
    void agendaDiaVacioMuestraEstadoYLibres() throws Exception {
        MockHttpSession secre = login("secre1", "secre1234");
        mockMvc.perform(get("/agenda")
                        .param("fecha", hoy.plusDays(5).toString())
                        .param("profesional", prof1Id.toString())
                        .session(secre).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sin turnos")))
                .andExpect(content().string(containsString("Huecos libres")));
    }

    @Test
    void agendaSemanaCubreSieteDias() throws Exception {
        MockHttpSession secre = login("secre1", "secre1234");
        String html = mockMvc.perform(get("/agenda")
                        .param("fecha", hoy.toString())
                        .param("profesional", prof1Id.toString())
                        .param("vista", "semana")
                        .session(secre).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (int i = 0; i < 7; i++) {
            MatcherAssert.assertThat(html, containsString(hoy.plusDays(i).toString()));
        }
    }

    // 2.2 Scoping.

    @Test
    void agendaScopingPorRol() throws Exception {
        MockHttpSession pac = login("pac1", "paciente123");
        String htmlPac = mockMvc.perform(get("/agenda")
                        .param("fecha", hoy.toString())
                        .session(pac).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        MatcherAssert.assertThat(htmlPac, containsString("09:00"));
        MatcherAssert.assertThat(htmlPac, containsString("11:00"));
        MatcherAssert.assertThat(htmlPac, Matchers.not(containsString("10:00")));

        MockHttpSession odo = login("odonto1", "odonto1234");
        String htmlOdo = mockMvc.perform(get("/agenda")
                        .param("fecha", hoy.toString())
                        .session(odo).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        MatcherAssert.assertThat(htmlOdo, containsString("09:00"));
        MatcherAssert.assertThat(htmlOdo, containsString("10:00"));
        // "11:00" como hueco libre propio es legítimo (TurnoCard renderiza
        // la hora en <strong>fecha HH:MM</strong>; el turno de las 11:00 es
        // de otro profesional y no debe aparecer como tarjeta).
        MatcherAssert.assertThat(htmlOdo, Matchers.not(containsString("11:00</strong>")));
    }

    @Test
    void turnoAjenoParaPacienteDa403() throws Exception {
        MockHttpSession pac = login("pac1", "paciente123");
        mockMvc.perform(get("/turnos/" + turnoBId).session(pac).accept(MediaType.TEXT_HTML))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("No autorizado")));
    }

    // 3.1 Alta de turno.

    @Test
    void formNuevoTurnoConSlots() throws Exception {
        MockHttpSession secre = login("secre1", "secre1234");
        mockMvc.perform(get("/turnos/nuevo")
                        .param("profesional", prof1Id.toString())
                        .param("fecha", hoy.plusDays(1).toString())
                        .session(secre).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Paciente")))
                .andExpect(content().string(containsString("_csrf")))
                .andExpect(content().string(containsString("08:00")));
    }

    @Test
    void crearTurnoValidoRedirigeADetalle() throws Exception {
        MockHttpSession secre = login("secre1", "secre1234");
        mockMvc.perform(post("/turnos/nuevo").with(csrf()).session(secre)
                        .param("pacienteId", pacBId.toString())
                        .param("profesionalId", prof1Id.toString())
                        .param("especialidadId", espId.toString())
                        .param("fecha", hoy.plusDays(1).toString())
                        .param("horaInicio", "08:00")
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/turnos/")));
    }

    @Test
    void conflictoReRenderizaConSugeridosYConservaDatos() throws Exception {
        MockHttpSession secre = login("secre1", "secre1234");
        String fecha = hoy.plusDays(2).toString();
        mockMvc.perform(post("/turnos/nuevo").with(csrf()).session(secre)
                        .param("pacienteId", pacAId.toString())
                        .param("profesionalId", prof1Id.toString())
                        .param("especialidadId", espId.toString())
                        .param("fecha", fecha)
                        .param("horaInicio", "09:00")
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/turnos/nuevo").with(csrf()).session(secre)
                        .param("pacienteId", pacBId.toString())
                        .param("profesionalId", prof1Id.toString())
                        .param("especialidadId", espId.toString())
                        .param("fecha", fecha)
                        .param("horaInicio", "09:00")
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("Horario ocupado")))
                .andExpect(content().string(containsString("09:30")))
                .andExpect(content().string(containsString(fecha)));
    }

    @Test
    void postInvalidoNunca500() throws Exception {
        MockHttpSession secre = login("secre1", "secre1234");
        mockMvc.perform(post("/turnos/nuevo").with(csrf()).session(secre)
                        .param("fecha", "no-es-fecha")
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("pacienteId")));
    }

    // 3.2 Detalle + transiciones.

    @Test
    void detalleMuestraBotonesSegunRolYEstado() throws Exception {
        MockHttpSession secre = login("secre1", "secre1234");
        mockMvc.perform(get("/turnos/" + turnoA1Id).session(secre).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cancelar turno")))
                .andExpect(content().string(containsString("Reprogramar")))
                .andExpect(content().string(containsString("Marcar atendido")))
                .andExpect(content().string(containsString("badge-reservado")));

        MockHttpSession pac = login("pac1", "paciente123");
        String htmlPac = mockMvc.perform(get("/turnos/" + turnoA1Id)
                        .session(pac).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cancelar turno")))
                .andReturn().getResponse().getContentAsString();
        MatcherAssert.assertThat(htmlPac, Matchers.not(containsString("Reprogramar")));
        MatcherAssert.assertThat(htmlPac, Matchers.not(containsString("Marcar atendido")));
    }

    @Test
    void flujo2CancelarLiberaHueco() throws Exception {
        MockHttpSession secre = login("secre1", "secre1234");
        mockMvc.perform(post("/turnos/" + turnoA1Id + "/cancelar").with(csrf()).session(secre)
                        .param("motivo", "paciente ausente")
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection());
        String html = mockMvc.perform(get("/turnos/" + turnoA1Id)
                        .session(secre).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("CANCELADO")))
                .andReturn().getResponse().getContentAsString();
        MatcherAssert.assertThat(html, Matchers.not(containsString("Cancelar turno")));
        MatcherAssert.assertThat(html, Matchers.not(containsString("Reprogramar")));
    }

    @Test
    void odontologoNoPuedeCancelar() throws Exception {
        MockHttpSession odo = login("odonto1", "odonto1234");
        mockMvc.perform(post("/turnos/" + turnoA1Id + "/cancelar").with(csrf()).session(odo)
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().isForbidden());
    }

    @Test
    void flujo5Reprogramar() throws Exception {
        MockHttpSession secre = login("secre1", "secre1234");
        String destino = hoy.plusDays(3).toString();
        MvcResult r = mockMvc.perform(post("/turnos/" + turnoA1Id + "/reprogramar")
                        .with(csrf()).session(secre)
                        .param("nuevaFecha", destino)
                        .param("nuevaHora", "10:30")
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String location = r.getResponse().getHeader("Location");
        MatcherAssert.assertThat(location, containsString("/turnos/"));
        MatcherAssert.assertThat(location, Matchers.not(containsString(turnoA1Id.toString())));
    }

    @Test
    void atendidoYSoloDesdeReservado() throws Exception {
        MockHttpSession secre = login("secre1", "secre1234");
        mockMvc.perform(post("/turnos/" + turnoA1Id + "/atendido").with(csrf()).session(secre)
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/turnos/" + turnoA1Id).session(secre).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ATENDIDO")));
        // Transición inválida ATENDIDO→cancelar re-renderiza 400, no 500.
        mockMvc.perform(post("/turnos/" + turnoA1Id + "/cancelar").with(csrf()).session(secre)
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().isBadRequest());
    }

    // 4.1/4.2 Pacientes.

    @Test
    void buscadorPorApellido() throws Exception {
        MockHttpSession secre = login("secre1", "secre1234");
        mockMvc.perform(get("/pacientes").param("apellido", "P\u00e9r")
                        .session(secre).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("P\u00e9rez")))
                .andExpect(content().string(containsString("Ver ficha")));
    }

    @Test
    void altaInlineEncadenaANuevoTurno() throws Exception {
        MockHttpSession secre = login("secre1", "secre1234");
        MvcResult r = mockMvc.perform(post("/pacientes").with(csrf()).session(secre)
                        .param("nombre", "Nuevo")
                        .param("apellido", "Paciente")
                        .param("dni", "99999999")
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        MatcherAssert.assertThat(r.getResponse().getHeader("Location"),
                containsString("/turnos/nuevo?paciente="));
    }

    @Test
    void dniDuplicadoMuestraErrorConLink() throws Exception {
        MockHttpSession secre = login("secre1", "secre1234");
        mockMvc.perform(post("/pacientes").with(csrf()).session(secre)
                        .param("nombre", "Otro")
                        .param("apellido", "Nombre")
                        .param("dni", "11111111")
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("DNI ya registrado")))
                .andExpect(content().string(containsString("Ver ficha existente")))
                .andExpect(content().string(containsString("/pacientes/" + pacAId)));
    }

    @Test
    void fichaConHistorialYScoping() throws Exception {
        MockHttpSession secre = login("secre1", "secre1234");
        mockMvc.perform(get("/pacientes/" + pacAId).session(secre).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("11111111")))
                .andExpect(content().string(containsString("Historial de turnos")))
                .andExpect(content().string(containsString("09:00")));

        MockHttpSession pac = login("pac1", "paciente123");
        mockMvc.perform(get("/pacientes/" + pacAId).session(pac).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk());
        mockMvc.perform(get("/pacientes/" + pacBId).session(pac).accept(MediaType.TEXT_HTML))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("No autorizado")));
        mockMvc.perform(get("/pacientes").session(pac).accept(MediaType.TEXT_HTML))
                .andExpect(status().isForbidden());
    }

    // 5.1 E2E Flujo 4 + Flujo 1 + Flujo 3.

    @Test
    void e2eAltaReservaYAgenda() throws Exception {
        MockHttpSession secre = login("secre1", "secre1234");
        MvcResult alta = mockMvc.perform(post("/pacientes").with(csrf()).session(secre)
                        .param("nombre", "E2E")
                        .param("apellido", "Flujo")
                        .param("dni", "88888888")
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String loc = alta.getResponse().getHeader("Location");
        MatcherAssert.assertThat(loc, containsString("/turnos/nuevo?paciente="));
        String nuevoId = loc.substring(loc.lastIndexOf('=') + 1);

        String fecha = hoy.plusDays(4).toString();
        mockMvc.perform(post("/turnos/nuevo").with(csrf()).session(secre)
                        .param("pacienteId", nuevoId)
                        .param("profesionalId", prof2Id.toString())
                        .param("especialidadId", espId.toString())
                        .param("fecha", fecha)
                        .param("horaInicio", "14:00")
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/agenda").param("fecha", fecha)
                        .param("profesional", prof2Id.toString())
                        .session(secre).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Flujo")))
                .andExpect(content().string(containsString("14:00")));
    }

    // 5.2 Responsive + a11y + CSP.

    @Test
    void responsiveA11yYCsp() throws Exception {
        MockHttpSession secre = login("secre1", "secre1234");
        MvcResult r = mockMvc.perform(get("/agenda")
                        .param("fecha", hoy.toString())
                        .param("profesional", prof1Id.toString())
                        .session(secre).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy",
                        containsString("default-src 'self'")))
                .andExpect(content().string(containsString("viewport")))
                .andExpect(content().string(containsString("/css/app.css")))
                .andExpect(content().string(containsString("<label")))
                .andReturn();
        String html = r.getResponse().getContentAsString();
        MatcherAssert.assertThat(html, Matchers.not(containsString("onclick")));
    }
}
