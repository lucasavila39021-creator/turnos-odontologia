package com.clinica.turnos.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clinica.turnos.application.dto.CreateEspecialidadRequest;
import com.clinica.turnos.application.dto.CreateProfesionalRequest;
import com.clinica.turnos.application.service.EspecialidadService;
import com.clinica.turnos.application.service.ProfesionalService;
import com.clinica.turnos.domain.model.EstadoTurno;
import com.clinica.turnos.domain.model.Paciente;
import com.clinica.turnos.domain.model.Rol;
import com.clinica.turnos.domain.model.Usuario;
import com.clinica.turnos.domain.repository.EspecialidadRepository;
import com.clinica.turnos.domain.repository.PacienteRepository;
import com.clinica.turnos.domain.repository.ProfesionalEspecialidadRepository;
import com.clinica.turnos.domain.repository.ProfesionalRepository;
import com.clinica.turnos.domain.repository.TurnoRepository;
import com.clinica.turnos.domain.repository.UsuarioRepository;
import com.clinica.turnos.security.RateLimitService;
import java.time.LocalDate;
import java.util.List;
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
 * C-10 (RED→GREEN): transiciones RESERVADO→CANCELADO/ATENDIDO + reprogramar
 * atómico. Patrón espejo de TurnoControllerH2Test: login real → sesión.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TurnoTransicionesIT {

    @Autowired MockMvc mockMvc;
    @Autowired TurnoRepository turnoRepository;
    @Autowired ProfesionalService profesionalService;
    @Autowired ProfesionalRepository profesionalRepository;
    @Autowired ProfesionalEspecialidadRepository vinculoRepository;
    @Autowired EspecialidadService especialidadService;
    @Autowired EspecialidadRepository especialidadRepository;
    @Autowired PacienteRepository pacienteRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired RateLimitService rateLimitService;

    private Long ortodonciaId;
    private Long profId;
    private Long profAjenoId;
    private Long pacAId;
    private Long pacBId;
    private LocalDate fechaFutura;

    @BeforeEach
    void setUp() {
        rateLimitService.clear();
        turnoRepository.deleteAll();
        vinculoRepository.deleteAll();
        profesionalRepository.deleteAll();
        especialidadRepository.deleteAll();
        pacienteRepository.deleteAll();
        usuarioRepository.deleteAll();
        usuarioRepository.save(new Usuario("admin", passwordEncoder.encode("admin123"),
                "admin@clinica.local", Rol.ADMIN, true));
        usuarioRepository.save(new Usuario("secre1", passwordEncoder.encode("secre1234"),
                "secre@clinica.local", Rol.SECRETARIA, true));
        Long odontoUid = usuarioRepository.save(new Usuario("odonto1",
                passwordEncoder.encode("odonto1234"),
                "odonto@clinica.local", Rol.ODONTOLOGO, true)).getId();
        Long pacUser1 = usuarioRepository.save(new Usuario("pac1",
                passwordEncoder.encode("paciente123"), "pac1@clinica.local",
                Rol.PACIENTE, true)).getId();
        Long pacUser2 = usuarioRepository.save(new Usuario("pac2",
                passwordEncoder.encode("paciente123"), "pac2@clinica.local",
                Rol.PACIENTE, true)).getId();
        ortodonciaId = especialidadService.crear(
                new CreateEspecialidadRequest("Ortodoncia", null)).id();
        profId = profesionalService.crear(new CreateProfesionalRequest(
                "Ana", "Gómez", "M-400", null, null, odontoUid, List.of(ortodonciaId))).id();
        profAjenoId = profesionalService.crear(new CreateProfesionalRequest(
                "Luis", "Pérez", "M-401", null, null, null, List.of(ortodonciaId))).id();
        pacAId = pacienteRepository.save(new Paciente("Juan", "Pérez", "11111111",
                null, null, null, pacUser1)).getId();
        pacBId = pacienteRepository.save(new Paciente("María", "López", "22222222",
                null, null, null, pacUser2)).getId();
        fechaFutura = LocalDate.now().plusDays(30);
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

    private String turnoJson(Long pac, Long prof, Long esp, LocalDate fecha, String hora) {
        return "{\"pacienteId\":" + pac + ",\"profesionalId\":" + prof
                + ",\"especialidadId\":" + esp + ",\"fecha\":\"" + fecha
                + "\",\"horaInicio\":\"" + hora + "\"}";
    }

    private Long reservar(MockHttpSession session, String body) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/turnos").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andReturn();
        String json = r.getResponse().getContentAsString();
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("\"id\":(\\d+)").matcher(json);
        assertThat(m.find()).isTrue();
        return Long.valueOf(m.group(1));
    }

    // 1.1 — motivo >255 → 400 fieldErrors.motivo
    @Test
    void motivoLargo400() throws Exception {
        MockHttpSession secre = login("secre1", "secre1234");
        Long id = reservar(secre, turnoJson(pacAId, profId, ortodonciaId, fechaFutura, "10:00"));
        String largo = "x".repeat(256);
        mockMvc.perform(patch("/api/turnos/" + id + "/cancelar").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"" + largo + "\"}")
                        .accept(MediaType.APPLICATION_JSON).session(secre))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.motivo").exists());
        assertThat(turnoRepository.findById(id).orElseThrow().getEstado())
                .isEqualTo(EstadoTurno.RESERVADO);
    }

    // 1.1 — reprogramar sin fecha → 400
    @Test
    void reprogramarSinFecha400() throws Exception {
        MockHttpSession secre = login("secre1", "secre1234");
        Long id = reservar(secre, turnoJson(pacAId, profId, ortodonciaId, fechaFutura, "10:00"));
        mockMvc.perform(post("/api/turnos/" + id + "/reprogramar").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nueva_hora_inicio\":\"11:00\"}")
                        .accept(MediaType.APPLICATION_JSON).session(secre))
                .andExpect(status().isBadRequest());
        assertThat(turnoRepository.findById(id).orElseThrow().getEstado())
                .isEqualTo(EstadoTurno.RESERVADO);
    }

    // 2.2 — cancelar ok 200 con motivo persistido
    @Test
    void cancelarOk200ConMotivo() throws Exception {
        MockHttpSession secre = login("secre1", "secre1234");
        Long id = reservar(secre, turnoJson(pacAId, profId, ortodonciaId, fechaFutura, "10:00"));
        mockMvc.perform(patch("/api/turnos/" + id + "/cancelar").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"paciente avisa\"}")
                        .accept(MediaType.APPLICATION_JSON).session(secre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADO"))
                .andExpect(jsonPath("$.motivoCancelacion").value("paciente avisa"));
        assertThat(turnoRepository.findById(id).orElseThrow().getMotivoCancelacion())
                .isEqualTo("paciente avisa");
    }

    // 2.2 — cancelar ya CANCELADO → 400 intacto
    @Test
    void cancelarYaCancelado400() throws Exception {
        MockHttpSession secre = login("secre1", "secre1234");
        Long id = reservar(secre, turnoJson(pacAId, profId, ortodonciaId, fechaFutura, "10:00"));
        mockMvc.perform(patch("/api/turnos/" + id + "/cancelar").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}")
                        .accept(MediaType.APPLICATION_JSON).session(secre))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/turnos/" + id + "/cancelar").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}")
                        .accept(MediaType.APPLICATION_JSON).session(secre))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("No se puede cancelar")));
        assertThat(turnoRepository.findById(id).orElseThrow().getEstado())
                .isEqualTo(EstadoTurno.CANCELADO);
    }

    // 2.2 — paciente ajeno 403, odontólogo 403, inexistente 404
    @Test
    void cancelarRbacy404() throws Exception {
        MockHttpSession secre = login("secre1", "secre1234");
        Long id = reservar(secre, turnoJson(pacAId, profId, ortodonciaId, fechaFutura, "10:00"));
        MockHttpSession pac2 = login("pac2", "paciente123");
        mockMvc.perform(patch("/api/turnos/" + id + "/cancelar").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}")
                        .accept(MediaType.APPLICATION_JSON).session(pac2))
                .andExpect(status().isForbidden());
        MockHttpSession odonto = login("odonto1", "odonto1234");
        mockMvc.perform(patch("/api/turnos/" + id + "/cancelar").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}")
                        .accept(MediaType.APPLICATION_JSON).session(odonto))
                .andExpect(status().isForbidden());
        assertThat(turnoRepository.findById(id).orElseThrow().getEstado())
                .isEqualTo(EstadoTurno.RESERVADO);
        mockMvc.perform(patch("/api/turnos/999999/cancelar").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}")
                        .accept(MediaType.APPLICATION_JSON).session(secre))
                .andExpect(status().isNotFound());
    }

    // 2.2 — paciente propio puede cancelar
    @Test
    void pacientePropioCancela200() throws Exception {
        MockHttpSession secre = login("secre1", "secre1234");
        Long id = reservar(secre, turnoJson(pacAId, profId, ortodonciaId, fechaFutura, "10:00"));
        MockHttpSession pac1 = login("pac1", "paciente123");
        mockMvc.perform(patch("/api/turnos/" + id + "/cancelar").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}")
                        .accept(MediaType.APPLICATION_JSON).session(pac1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADO"));
    }

    // 2.2 — atendido ok + paciente 403 + odontólogo ajeno 403 + desde CANCELADO 400
    @Test
    void atendidoTransicionesYrbac() throws Exception {
        MockHttpSession secre = login("secre1", "secre1234");
        Long id = reservar(secre, turnoJson(pacAId, profId, ortodonciaId, fechaFutura, "10:00"));
        MockHttpSession pac1 = login("pac1", "paciente123");
        mockMvc.perform(patch("/api/turnos/" + id + "/atendido").with(csrf())
                        .accept(MediaType.APPLICATION_JSON).session(pac1))
                .andExpect(status().isForbidden());
        // odontólogo ajeno (turno de profAjeno) → 403
        Long ajeno = reservar(secre, turnoJson(pacBId, profAjenoId, ortodonciaId,
                fechaFutura, "11:00"));
        MockHttpSession odonto = login("odonto1", "odonto1234");
        mockMvc.perform(patch("/api/turnos/" + ajeno + "/atendido").with(csrf())
                        .accept(MediaType.APPLICATION_JSON).session(odonto))
                .andExpect(status().isForbidden());
        assertThat(turnoRepository.findById(ajeno).orElseThrow().getEstado())
                .isEqualTo(EstadoTurno.RESERVADO);
        // odontólogo propio ok
        mockMvc.perform(patch("/api/turnos/" + id + "/atendido").with(csrf())
                        .accept(MediaType.APPLICATION_JSON).session(odonto))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ATENDIDO"));
        // desde ATENDIDO → 400
        mockMvc.perform(patch("/api/turnos/" + id + "/atendido").with(csrf())
                        .accept(MediaType.APPLICATION_JSON).session(secre))
                .andExpect(status().isBadRequest());
        // desde CANCELADO → 400
        mockMvc.perform(patch("/api/turnos/" + ajeno + "/cancelar").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}")
                        .accept(MediaType.APPLICATION_JSON).session(secre))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/turnos/" + ajeno + "/atendido").with(csrf())
                        .accept(MediaType.APPLICATION_JSON).session(secre))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/turnos/999999/atendido").with(csrf())
                        .accept(MediaType.APPLICATION_JSON).session(secre))
                .andExpect(status().isNotFound());
    }

    // 3.2 — reprogramar ok 201 deja dos registros
    @Test
    void reprogramarOk201DosRegistros() throws Exception {
        MockHttpSession secre = login("secre1", "secre1234");
        Long id = reservar(secre, turnoJson(pacAId, profId, ortodonciaId, fechaFutura, "10:00"));
        mockMvc.perform(post("/api/turnos/" + id + "/reprogramar").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nueva_fecha\":\"" + fechaFutura.plusDays(1)
                                + "\",\"nueva_hora_inicio\":\"11:00\"}")
                        .accept(MediaType.APPLICATION_JSON).session(secre))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("RESERVADO"))
                .andExpect(jsonPath("$.fecha").value(fechaFutura.plusDays(1).toString()));
        assertThat(turnoRepository.count()).isEqualTo(2);
        assertThat(turnoRepository.findById(id).orElseThrow().getEstado())
                .isEqualTo(EstadoTurno.CANCELADO);
        assertThat(turnoRepository.findById(id).orElseThrow().getMotivoCancelacion())
                .isEqualTo("reprogramado");
    }

    // 3.1 — destino ocupado → 409 con original intacto
    @Test
    void reprogramarOcupado409OriginalIntacto() throws Exception {
        MockHttpSession secre = login("secre1", "secre1234");
        Long id = reservar(secre, turnoJson(pacAId, profId, ortodonciaId, fechaFutura, "10:00"));
        reservar(secre, turnoJson(pacBId, profId, ortodonciaId, fechaFutura, "11:00"));
        mockMvc.perform(post("/api/turnos/" + id + "/reprogramar").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nueva_fecha\":\"" + fechaFutura
                                + "\",\"nueva_hora_inicio\":\"11:15\"}")
                        .accept(MediaType.APPLICATION_JSON).session(secre))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.sugeridos").exists());
        assertThat(turnoRepository.count()).isEqualTo(2);
        assertThat(turnoRepository.findById(id).orElseThrow().getEstado())
                .isEqualTo(EstadoTurno.RESERVADO);
    }

    // 3.1 — mismo hueco → 200 (excluye original)
    @Test
    void reprogramarMismoHueco200() throws Exception {
        MockHttpSession secre = login("secre1", "secre1234");
        Long id = reservar(secre, turnoJson(pacAId, profId, ortodonciaId, fechaFutura, "10:00"));
        mockMvc.perform(post("/api/turnos/" + id + "/reprogramar").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nueva_fecha\":\"" + fechaFutura
                                + "\",\"nueva_hora_inicio\":\"10:00\"}")
                        .accept(MediaType.APPLICATION_JSON).session(secre))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("RESERVADO"));
        assertThat(turnoRepository.count()).isEqualTo(2);
    }

    // 3.2 — RBAC + 400s de reprogramar
    @Test
    void reprogramarRbacy400s() throws Exception {
        MockHttpSession secre = login("secre1", "secre1234");
        Long id = reservar(secre, turnoJson(pacAId, profId, ortodonciaId, fechaFutura, "10:00"));
        String destino = "{\"nueva_fecha\":\"" + fechaFutura.plusDays(1)
                + "\",\"nueva_hora_inicio\":\"11:00\"}";
        MockHttpSession pac1 = login("pac1", "paciente123");
        mockMvc.perform(post("/api/turnos/" + id + "/reprogramar").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(destino)
                        .accept(MediaType.APPLICATION_JSON).session(pac1))
                .andExpect(status().isForbidden());
        MockHttpSession odonto = login("odonto1", "odonto1234");
        mockMvc.perform(post("/api/turnos/" + id + "/reprogramar").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(destino)
                        .accept(MediaType.APPLICATION_JSON).session(odonto))
                .andExpect(status().isForbidden());
        // fuera de horario → 400
        mockMvc.perform(post("/api/turnos/" + id + "/reprogramar").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nueva_fecha\":\"" + fechaFutura.plusDays(1)
                                + "\",\"nueva_hora_inicio\":\"07:00\"}")
                        .accept(MediaType.APPLICATION_JSON).session(secre))
                .andExpect(status().isBadRequest());
        // pasado → 400
        mockMvc.perform(post("/api/turnos/" + id + "/reprogramar").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nueva_fecha\":\"" + LocalDate.now().minusDays(1)
                                + "\",\"nueva_hora_inicio\":\"10:00\"}")
                        .accept(MediaType.APPLICATION_JSON).session(secre))
                .andExpect(status().isBadRequest());
        assertThat(turnoRepository.findById(id).orElseThrow().getEstado())
                .isEqualTo(EstadoTurno.RESERVADO);
        // original CANCELADO → 400 sin fila nueva
        mockMvc.perform(patch("/api/turnos/" + id + "/cancelar").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}")
                        .accept(MediaType.APPLICATION_JSON).session(secre))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/turnos/" + id + "/reprogramar").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(destino)
                        .accept(MediaType.APPLICATION_JSON).session(secre))
                .andExpect(status().isBadRequest());
        assertThat(turnoRepository.count()).isEqualTo(1);
    }

    // 4.1 — reserva → cancelar → re-reservar mismo hueco 201
    @Test
    void cancelarLiberaHuecoParaReReserva() throws Exception {
        MockHttpSession secre = login("secre1", "secre1234");
        Long id = reservar(secre, turnoJson(pacAId, profId, ortodonciaId, fechaFutura, "10:00"));
        mockMvc.perform(patch("/api/turnos/" + id + "/cancelar").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}")
                        .accept(MediaType.APPLICATION_JSON).session(secre))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/turnos").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(turnoJson(pacBId, profId, ortodonciaId, fechaFutura, "10:15"))
                        .accept(MediaType.APPLICATION_JSON).session(secre))
                .andExpect(status().isCreated());
    }

    // 4.1 — reserva → reprogramar → nuevo visible en agenda
    @Test
    void reprogramarNuevoVisibleEnAgenda() throws Exception {
        MockHttpSession secre = login("secre1", "secre1234");
        Long id = reservar(secre, turnoJson(pacAId, profId, ortodonciaId, fechaFutura, "10:00"));
        LocalDate destino = fechaFutura.plusDays(2);
        mockMvc.perform(post("/api/turnos/" + id + "/reprogramar").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nueva_fecha\":\"" + destino
                                + "\",\"nueva_hora_inicio\":\"11:00\"}")
                        .accept(MediaType.APPLICATION_JSON).session(secre))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/profesionales/" + profId + "/agenda")
                        .param("fecha", destino.toString())
                        .accept(MediaType.APPLICATION_JSON).session(secre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ocupados.length()").value(1));
    }

    // 4.1 — reserva → atendido → visible en ?estado=ATENDIDO
    @Test
    void atendidoVisibleEnFiltroEstado() throws Exception {
        MockHttpSession secre = login("secre1", "secre1234");
        Long id = reservar(secre, turnoJson(pacAId, profId, ortodonciaId, fechaFutura, "10:00"));
        mockMvc.perform(patch("/api/turnos/" + id + "/atendido").with(csrf())
                        .accept(MediaType.APPLICATION_JSON).session(secre))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/turnos")
                        .param("fecha", fechaFutura.toString())
                        .param("estado", "ATENDIDO")
                        .accept(MediaType.APPLICATION_JSON).session(secre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
