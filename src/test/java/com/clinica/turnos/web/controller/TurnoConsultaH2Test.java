package com.clinica.turnos.web.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clinica.turnos.application.dto.CreateEspecialidadRequest;
import com.clinica.turnos.application.service.EspecialidadService;
import com.clinica.turnos.application.service.ProfesionalService;
import com.clinica.turnos.application.dto.CreateProfesionalRequest;
import com.clinica.turnos.domain.model.Paciente;
import com.clinica.turnos.domain.model.Rol;
import com.clinica.turnos.domain.model.Turno;
import com.clinica.turnos.domain.model.Usuario;
import com.clinica.turnos.domain.repository.PacienteRepository;
import com.clinica.turnos.domain.repository.ProfesionalEspecialidadRepository;
import com.clinica.turnos.domain.repository.ProfesionalRepository;
import com.clinica.turnos.domain.repository.EspecialidadRepository;
import com.clinica.turnos.domain.repository.TurnoRepository;
import com.clinica.turnos.domain.repository.UsuarioRepository;
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * C-09 task 4.2 (RED→GREEN): GET /api/turnos, GET /api/turnos/{id},
 * GET /api/profesionales/{id}/agenda + GlobalExceptionHandler 400 fecha.
 * Patrón espejo de TurnoControllerH2Test: login real → sesión.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TurnoConsultaH2Test {

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
    private Long pacAId;
    private Long pacBId;
    private LocalDate dia;

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
        Long pacUser1 = usuarioRepository.save(new Usuario("pac1",
                passwordEncoder.encode("paciente123"), "pac1@clinica.local",
                Rol.PACIENTE, true)).getId();
        Long pacUser2 = usuarioRepository.save(new Usuario("pac2",
                passwordEncoder.encode("paciente123"), "pac2@clinica.local",
                Rol.PACIENTE, true)).getId();
        ortodonciaId = especialidadService.crear(
                new CreateEspecialidadRequest("Ortodoncia", null)).id();
        profId = profesionalService.crear(new CreateProfesionalRequest(
                "Ana", "Gómez", "M-300", null, null, null, List.of(ortodonciaId))).id();
        pacAId = pacienteRepository.save(new Paciente("Juan", "Pérez", "11111111",
                null, null, null, pacUser1)).getId();
        pacBId = pacienteRepository.save(new Paciente("María", "López", "22222222",
                null, null, null, pacUser2)).getId();
        dia = LocalDate.now().plusDays(30);
        turnoRepository.save(new Turno(pacAId, profId, ortodonciaId, dia,
                LocalTime.of(9, 0), LocalTime.of(9, 30)));
        turnoRepository.save(new Turno(pacBId, profId, ortodonciaId, dia.plusDays(3),
                LocalTime.of(10, 0), LocalTime.of(10, 30)));
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    void vistaSemana7Dias() throws Exception {
        MockHttpSession session = login("secre1", "secre1234");
        mockMvc.perform(get("/api/turnos")
                        .param("desde", dia.toString())
                        .param("hasta", dia.plusDays(6).toString())
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content", hasSize(2)));
    }

    @Test
    void fechaInvalida400() throws Exception {
        MockHttpSession session = login("secre1", "secre1234");
        mockMvc.perform(get("/api/turnos")
                        .param("fecha", "no-fecha")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.fecha").exists());
    }

    @Test
    void estadoInvalido400() throws Exception {
        MockHttpSession session = login("secre1", "secre1234");
        mockMvc.perform(get("/api/turnos")
                        .param("fecha", dia.toString())
                        .param("estado", "INEXISTENTE")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listaVaciaEnvelope() throws Exception {
        MockHttpSession session = login("secre1", "secre1234");
        mockMvc.perform(get("/api/turnos")
                        .param("fecha", dia.plusDays(60).toString())
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void sinSesion401EnLosTresEndpoints() throws Exception {
        mockMvc.perform(get("/api/turnos").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/turnos/1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/profesionales/" + profId + "/agenda")
                        .param("fecha", dia.toString()).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void detalleOkConAnidados() throws Exception {
        MockHttpSession session = login("secre1", "secre1234");
        Long id = turnoRepository.findAll().get(0).getId();
        mockMvc.perform(get("/api/turnos/" + id)
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paciente.nombre").exists())
                .andExpect(jsonPath("$.profesional.nombre").exists())
                .andExpect(jsonPath("$.especialidad.nombre").value("Ortodoncia"))
                .andExpect(jsonPath("$.estado").value("RESERVADO"));
    }

    @Test
    void detalleInexistente404() throws Exception {
        MockHttpSession session = login("secre1", "secre1234");
        mockMvc.perform(get("/api/turnos/999999")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isNotFound());
    }

    @Test
    void agendaOkConOcupadosYLibres() throws Exception {
        MockHttpSession session = login("secre1", "secre1234");
        mockMvc.perform(get("/api/profesionales/" + profId + "/agenda")
                        .param("fecha", dia.toString())
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fecha").value(dia.toString()))
                .andExpect(jsonPath("$.ocupados", hasSize(1)))
                .andExpect(jsonPath("$.libres").isArray());
    }

    @Test
    void agendaFechaInvalida400YProfesionalInexistente404() throws Exception {
        MockHttpSession session = login("secre1", "secre1234");
        mockMvc.perform(get("/api/profesionales/" + profId + "/agenda")
                        .param("fecha", "no-fecha")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.fecha").exists());
        mockMvc.perform(get("/api/profesionales/999999/agenda")
                        .param("fecha", dia.toString())
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isNotFound());
    }
}
