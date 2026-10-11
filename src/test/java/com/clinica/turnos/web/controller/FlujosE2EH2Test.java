package com.clinica.turnos.web.controller;

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
import java.time.ZoneId;
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
 * C-12 task 5.1: E2E H2 Flujos 1-5 encadenado (sin Testcontainers/Docker).
 * Alta paciente → reserva 201 → agenda contiene turno → cancelar →
 * reprogramar (original CANCELADO + nuevo RESERVADO) → atendido.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FlujosE2EH2Test {

    private static final ZoneId TZ = ZoneId.of("America/Argentina/Buenos_Aires");

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

    private Long espId;
    private Long profId;

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
        espId = especialidadService.crear(
                new CreateEspecialidadRequest("Ortodoncia", null)).id();
        profId = profesionalService.crear(new CreateProfesionalRequest(
                "E2E", "Doctora", "M-E2E", null, null, null, List.of(espId))).id();
    }

    private MockHttpSession loginSecre() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"secre1\",\"password\":\"secre1234\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    void flujos1a5Encadenados() throws Exception {
        MockHttpSession secre = loginSecre();
        LocalDate dia = LocalDate.now(TZ).plusDays(2);
        LocalDate diaRepro = LocalDate.now(TZ).plusDays(3);

        // Flujo 1 — alta paciente → 201 con id
        MvcResult alta = mockMvc.perform(post("/api/pacientes").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"E2E\",\"apellido\":\"Paciente\","
                                + "\"dni\":\"77777777\",\"email\":\"e2e@clinica.local\"}")
                        .accept(MediaType.APPLICATION_JSON).session(secre))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andReturn();
        Long pacId = ((Number) com.jayway.jsonpath.JsonPath.read(
                alta.getResponse().getContentAsString(), "$.id")).longValue();

        // Flujo 2 — reserva 10:00 → 201 RESERVADO
        MvcResult reserva = mockMvc.perform(post("/api/turnos").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pacienteId\":" + pacId + ",\"profesionalId\":"
                                + profId + ",\"especialidadId\":" + espId
                                + ",\"fecha\":\"" + dia + "\",\"horaInicio\":\"10:00\"}")
                        .accept(MediaType.APPLICATION_JSON).session(secre))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("RESERVADO"))
                .andReturn();
        Long turnoId = ((Number) com.jayway.jsonpath.JsonPath.read(
                reserva.getResponse().getContentAsString(), "$.id")).longValue();

        // Flujo 3 — agenda del día contiene el turno
        mockMvc.perform(get("/api/profesionales/" + profId + "/agenda")
                        .param("fecha", dia.toString())
                        .accept(MediaType.APPLICATION_JSON).session(secre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ocupados[0].id").value(turnoId.intValue()));

        // Flujo 4 — cancelar → CANCELADO
        mockMvc.perform(patch("/api/turnos/" + turnoId + "/cancelar").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"paciente no puede asistir\"}")
                        .accept(MediaType.APPLICATION_JSON).session(secre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADO"));

        // Flujo 5 — reprogramar un segundo turno y luego marcar atendido.
        // (El original ya CANCELADO no es reprogramable → se reserva uno nuevo.)
        MvcResult reserva2 = mockMvc.perform(post("/api/turnos").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pacienteId\":" + pacId + ",\"profesionalId\":"
                                + profId + ",\"especialidadId\":" + espId
                                + ",\"fecha\":\"" + dia + "\",\"horaInicio\":\"11:00\"}")
                        .accept(MediaType.APPLICATION_JSON).session(secre))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("RESERVADO"))
                .andReturn();
        Long turno2Id = ((Number) com.jayway.jsonpath.JsonPath.read(
                reserva2.getResponse().getContentAsString(), "$.id")).longValue();

        // Reprogramar turno2 al día siguiente 11:00 → 201 nuevo RESERVADO
        MvcResult repro = mockMvc.perform(post("/api/turnos/" + turno2Id + "/reprogramar")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nuevaFecha\":\"" + diaRepro + "\","
                                + "\"nuevaHoraInicio\":\"11:00\"}")
                        .accept(MediaType.APPLICATION_JSON).session(secre))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("RESERVADO"))
                .andExpect(jsonPath("$.fecha").value(diaRepro.toString()))
                .andReturn();
        Long turnoNuevoId = ((Number) com.jayway.jsonpath.JsonPath.read(
                repro.getResponse().getContentAsString(), "$.id")).longValue();

        // Original reprogramado quedó CANCELADO
        mockMvc.perform(get("/api/turnos/" + turno2Id)
                        .accept(MediaType.APPLICATION_JSON).session(secre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADO"));

        // Marcar atendido el nuevo → ATENDIDO
        mockMvc.perform(patch("/api/turnos/" + turnoNuevoId + "/atendido").with(csrf())
                        .accept(MediaType.APPLICATION_JSON).session(secre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ATENDIDO"));

        // Historial del paciente incluye los 3 turnos (Page: content[])
        mockMvc.perform(get("/api/pacientes/" + pacId + "/turnos")
                        .accept(MediaType.APPLICATION_JSON).session(secre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3));
    }
}
