package com.clinica.turnos.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clinica.turnos.application.dto.CreateEspecialidadRequest;
import com.clinica.turnos.application.dto.CreateProfesionalRequest;
import com.clinica.turnos.application.dto.TurnoCreateRequest;
import com.clinica.turnos.application.service.EspecialidadService;
import com.clinica.turnos.application.service.ProfesionalService;
import com.clinica.turnos.application.service.TurnoService;
import com.clinica.turnos.domain.model.EstadoTurno;
import com.clinica.turnos.domain.model.Paciente;
import com.clinica.turnos.domain.model.Rol;
import com.clinica.turnos.domain.model.Turno;
import com.clinica.turnos.domain.model.Usuario;
import com.clinica.turnos.domain.repository.EspecialidadRepository;
import com.clinica.turnos.domain.repository.PacienteRepository;
import com.clinica.turnos.domain.repository.ProfesionalEspecialidadRepository;
import com.clinica.turnos.domain.repository.ProfesionalRepository;
import com.clinica.turnos.domain.repository.TurnoRepository;
import com.clinica.turnos.domain.repository.UsuarioRepository;
import com.clinica.turnos.security.AuthzService;
import com.clinica.turnos.security.RateLimitService;
import java.time.LocalDate;
import java.time.LocalTime;
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
 * E2E reserva de turnos sobre H2 (C-08 tasks 1.2/1.3/2.x/3.1/4.1).
 * Patrón espejo de ProfesionalControllerH2Test: login real → sesión.
 * Testcontainers MySQL no disponible en este entorno (sin Docker).
 */
@SpringBootTest
@AutoConfigureMockMvc
class TurnoControllerH2Test {

    @Autowired MockMvc mockMvc;
    @Autowired TurnoService turnoService;
    @Autowired TurnoRepository turnoRepository;
    @Autowired ProfesionalService profesionalService;
    @Autowired ProfesionalRepository profesionalRepository;
    @Autowired ProfesionalEspecialidadRepository vinculoRepository;
    @Autowired EspecialidadService especialidadService;
    @Autowired EspecialidadRepository especialidadRepository;
    @Autowired PacienteRepository pacienteRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired AuthzService authzService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired RateLimitService rateLimitService;

    private Long ortodonciaId;
    private Long endodonciaId;
    private Long profId;
    private Long prof2Id;
    private Long pacAId;
    private Long pacBId;
    private Long pacSinLinkId;
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
        usuarioRepository.save(new Usuario("odonto1", passwordEncoder.encode("odonto1234"),
                "odonto@clinica.local", Rol.ODONTOLOGO, true));
        Long pacUser1 = usuarioRepository.save(new Usuario("pac1",
                passwordEncoder.encode("paciente123"), "pac1@clinica.local",
                Rol.PACIENTE, true)).getId();
        Long pacUser2 = usuarioRepository.save(new Usuario("pac2",
                passwordEncoder.encode("paciente123"), "pac2@clinica.local",
                Rol.PACIENTE, true)).getId();
        ortodonciaId = especialidadService.crear(
                new CreateEspecialidadRequest("Ortodoncia", null)).id();
        endodonciaId = especialidadService.crear(
                new CreateEspecialidadRequest("Endodoncia", null)).id();
        profId = profesionalService.crear(new CreateProfesionalRequest(
                "Ana", "Gómez", "M-200", null, null, null, List.of(ortodonciaId))).id();
        prof2Id = profesionalService.crear(new CreateProfesionalRequest(
                "Luis", "Pérez", "M-201", null, null, null, List.of(ortodonciaId))).id();
        pacAId = pacienteRepository.save(new Paciente("Juan", "Pérez", "11111111",
                null, null, null, pacUser1)).getId();
        pacBId = pacienteRepository.save(new Paciente("María", "López", "22222222",
                null, null, null, pacUser2)).getId();
        pacSinLinkId = pacienteRepository.save(new Paciente("Sin", "Link", "33333333",
                null, null, null, null)).getId();
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

    // 1.2 — Persistencia: save + find + solape true/false + adyacente + estado
    @Test
    void persistenciaSaveFindSolape() {
        Turno saved = turnoRepository.save(new Turno(pacAId, profId, ortodonciaId,
                fechaFutura, LocalTime.of(10, 0), LocalTime.of(10, 30)));
        assertThat(saved.getId()).isNotNull();
        assertThat(turnoRepository.findById(saved.getId())).isPresent();
        // solape real → true
        assertThat(turnoRepository.existsSolapeProfesional(profId, fechaFutura,
                LocalTime.of(10, 15), LocalTime.of(10, 45), EstadoTurno.RESERVADO)).isTrue();
        assertThat(turnoRepository.existsSolapePaciente(pacAId, fechaFutura,
                LocalTime.of(10, 15), LocalTime.of(10, 45), EstadoTurno.RESERVADO)).isTrue();
        // adyacente (fin == inicio) → false
        assertThat(turnoRepository.existsSolapeProfesional(profId, fechaFutura,
                LocalTime.of(10, 30), LocalTime.of(11, 0), EstadoTurno.RESERVADO)).isFalse();
        // otro profesional no solapa al primero
        assertThat(turnoRepository.existsSolapeProfesional(prof2Id, fechaFutura,
                LocalTime.of(10, 15), LocalTime.of(10, 45), EstadoTurno.RESERVADO)).isFalse();
        // toString no vuelca ids sensibles
        assertThat(saved.toString()).doesNotContain("11111111");
    }

    // 1.3 — Validación DTO: paciente ausente → 400 con fieldErrors, sin fila
    @Test
    void validacionCampoAusente400() throws Exception {
        MockHttpSession session = login("secre1", "secre1234");
        long antes = turnoRepository.count();
        mockMvc.perform(post("/api/turnos").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profesionalId\":" + profId + ",\"especialidadId\":"
                                + ortodonciaId + ",\"fecha\":\"" + fechaFutura
                                + "\",\"horaInicio\":\"10:00\"}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.pacienteId").exists());
        assertThat(turnoRepository.count()).isEqualTo(antes);
    }

    // 2.1 — Reserva ok 201 con hora_fin calculada (10:00 → 10:30)
    @Test
    void reservaOk201CalculaHoraFin() throws Exception {
        MockHttpSession session = login("secre1", "secre1234");
        mockMvc.perform(post("/api/turnos").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(turnoJson(pacAId, profId, ortodonciaId, fechaFutura, "10:00"))
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.estado").value("RESERVADO"))
                .andExpect(jsonPath("$.horaInicio", startsWith("10:00")))
                .andExpect(jsonPath("$.horaFin", startsWith("10:30")))
                .andExpect(jsonPath("$.paciente.id").value(pacAId.intValue()))
                .andExpect(jsonPath("$.profesional.id").value(profId.intValue()))
                .andExpect(jsonPath("$.especialidad.id").value(ortodonciaId.intValue()));
        assertThat(turnoRepository.count()).isEqualTo(1);
    }

    // 2.1 — FK inexistente → 404, sin fila
    @Test
    void fkInexistente404() throws Exception {
        MockHttpSession session = login("secre1", "secre1234");
        mockMvc.perform(post("/api/turnos").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(turnoJson(999999L, profId, ortodonciaId, fechaFutura, "10:00"))
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isNotFound());
        assertThat(turnoRepository.count()).isZero();
    }

    // 3.1 — Solape profesional → 409 con error + sugeridos, sin fila
    @Test
    void solapeProfesional409ConSugeridos() throws Exception {
        MockHttpSession session = login("secre1", "secre1234");
        turnoService.crear(new TurnoCreateRequest(pacAId, profId, ortodonciaId,
                fechaFutura, LocalTime.of(10, 0)));
        mockMvc.perform(post("/api/turnos").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(turnoJson(pacBId, profId, ortodonciaId, fechaFutura, "10:15"))
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.sugeridos").exists())
                .andExpect(jsonPath("$.sugeridos.length()").value(3));
        assertThat(turnoRepository.count()).isEqualTo(1);
    }

    // 3.1 — Adyacente no solapa → 201
    @Test
    void adyacenteNoSolapa201() throws Exception {
        MockHttpSession session = login("secre1", "secre1234");
        turnoService.crear(new TurnoCreateRequest(pacAId, profId, ortodonciaId,
                fechaFutura, LocalTime.of(10, 0)));
        mockMvc.perform(post("/api/turnos").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(turnoJson(pacBId, profId, ortodonciaId, fechaFutura, "10:30"))
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isCreated());
        assertThat(turnoRepository.count()).isEqualTo(2);
    }

    // 3.1 — Solape paciente con otro profesional → 409
    @Test
    void solapePaciente409() throws Exception {
        MockHttpSession session = login("secre1", "secre1234");
        turnoService.crear(new TurnoCreateRequest(pacAId, profId, ortodonciaId,
                fechaFutura, LocalTime.of(10, 0)));
        mockMvc.perform(post("/api/turnos").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(turnoJson(pacAId, prof2Id, ortodonciaId, fechaFutura, "10:15"))
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.sugeridos").exists());
        assertThat(turnoRepository.count()).isEqualTo(1);
    }

    // 3.1 — Fuera de horario → 400 con fieldErrors.hora_inicio
    @Test
    void fueraHorario400() throws Exception {
        MockHttpSession session = login("secre1", "secre1234");
        mockMvc.perform(post("/api/turnos").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(turnoJson(pacAId, profId, ortodonciaId, fechaFutura, "07:30"))
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.hora_inicio").exists());
        assertThat(turnoRepository.count()).isZero();
    }

    // 3.1 — Fecha pasada → 400 con fieldErrors.fecha
    @Test
    void fechaPasada400() throws Exception {
        MockHttpSession session = login("secre1", "secre1234");
        mockMvc.perform(post("/api/turnos").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(turnoJson(pacAId, profId, ortodonciaId,
                                LocalDate.now().minusDays(1), "10:00"))
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.fecha").exists());
        assertThat(turnoRepository.count()).isZero();
    }

    // 3.1 — Especialidad ajena (RN-PR-05) → 400 con fieldErrors.especialidad_id
    @Test
    void especialidadAjena400() throws Exception {
        MockHttpSession session = login("secre1", "secre1234");
        mockMvc.perform(post("/api/turnos").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(turnoJson(pacAId, profId, endodonciaId, fechaFutura, "10:00"))
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.especialidad_id").exists());
        assertThat(turnoRepository.count()).isZero();
    }

    // 3.1 — PACIENTE reserva para otro → 403, sin fila
    @Test
    void pacienteAjeno403() throws Exception {
        MockHttpSession pac = login("pac1", "paciente123");
        mockMvc.perform(post("/api/turnos").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(turnoJson(pacBId, profId, ortodonciaId, fechaFutura, "10:00"))
                        .accept(MediaType.APPLICATION_JSON).session(pac))
                .andExpect(status().isForbidden());
        assertThat(turnoRepository.count()).isZero();
        // propio ok → 201
        mockMvc.perform(post("/api/turnos").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(turnoJson(pacAId, profId, ortodonciaId, fechaFutura, "10:00"))
                        .accept(MediaType.APPLICATION_JSON).session(pac))
                .andExpect(status().isCreated());
    }

    // 2.3 — PACIENTE sin link → 403 (ficha huérfana reservada por otro paciente)
    @Test
    void pacienteSinLink403() throws Exception {
        MockHttpSession pac = login("pac1", "paciente123");
        mockMvc.perform(post("/api/turnos").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(turnoJson(pacSinLinkId, profId, ortodonciaId, fechaFutura, "10:00"))
                        .accept(MediaType.APPLICATION_JSON).session(pac))
                .andExpect(status().isForbidden());
        assertThat(turnoRepository.count()).isZero();
    }

    // 3.1 — ODONTOLOGO crear → 403; no-auth → 401
    @Test
    void odontologo403NoAuth401() throws Exception {
        MockHttpSession odonto = login("odonto1", "odonto1234");
        mockMvc.perform(post("/api/turnos").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(turnoJson(pacAId, profId, ortodonciaId, fechaFutura, "10:00"))
                        .accept(MediaType.APPLICATION_JSON).session(odonto))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/turnos").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(turnoJson(pacAId, profId, ortodonciaId, fechaFutura, "10:00"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        assertThat(turnoRepository.count()).isZero();
    }

    // 2.2 — Envelope 409 problem-details con error + sugeridos
    @Test
    void envelope409ProblemDetails() throws Exception {
        MockHttpSession session = login("admin", "admin123");
        turnoService.crear(new TurnoCreateRequest(pacAId, profId, ortodonciaId,
                fechaFutura, LocalTime.of(10, 0)));
        mockMvc.perform(post("/api/turnos").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(turnoJson(pacBId, profId, ortodonciaId, fechaFutura, "10:15"))
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Horario ocupado"))
                .andExpect(jsonPath("$.sugeridos").isArray())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Horario ocupado")));
    }

    // Spec — CANCELADO libera el hueco → 201
    @Test
    void canceladoLiberaHueco201() throws Exception {
        MockHttpSession session = login("secre1", "secre1234");
        var resp = turnoService.crear(new TurnoCreateRequest(pacAId, profId, ortodonciaId,
                fechaFutura, LocalTime.of(10, 0)));
        Turno t = turnoRepository.findById(resp.id()).orElseThrow();
        t.setEstado(EstadoTurno.CANCELADO);
        turnoRepository.saveAndFlush(t);
        mockMvc.perform(post("/api/turnos").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(turnoJson(pacBId, profId, ortodonciaId, fechaFutura, "10:15"))
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isCreated());
    }

    // 4.1 — Guard real: desactivar con turno futuro → 400 ficha intacta
    @Test
    void desactivarConTurnoFuturo400() throws Exception {
        MockHttpSession secre = login("secre1", "secre1234");
        MockHttpSession admin = login("admin", "admin123");
        mockMvc.perform(post("/api/turnos").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(turnoJson(pacAId, profId, ortodonciaId, fechaFutura, "10:00"))
                        .accept(MediaType.APPLICATION_JSON).session(secre))
                .andExpect(status().isCreated());
        assertThat(profesionalService.tieneTurnosFuturosReservados(profId)).isTrue();
        assertThat(profesionalService.tieneTurnosFuturosReservados(prof2Id)).isFalse();
        mockMvc.perform(patch("/api/profesionales/" + profId + "/desactivar").with(csrf())
                        .accept(MediaType.APPLICATION_JSON).session(admin))
                .andExpect(status().isBadRequest());
        assertThat(profesionalRepository.findById(profId).orElseThrow().isActivo()).isTrue();
        // sin futuros → 200
        mockMvc.perform(patch("/api/profesionales/" + prof2Id + "/desactivar").with(csrf())
                        .accept(MediaType.APPLICATION_JSON).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
    }
}
