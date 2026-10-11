package com.clinica.turnos.web.controller;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
 * C-09 task 5.1: E2E H2 del Flujo 3 por rol (US-030) con seed de 3 turnos.
 * Verifica orden cronológico, anidados paciente/profesional/especialidad +
 * estado presentes, y fecha default = hoy en America/Argentina/Buenos_Aires.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TurnoFlujo3E2EH2Test {

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

    private Long prof1Id;
    private Long prof2Id;
    private Long pacAId;
    private Long pacBId;
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
        Long odoUser1 = usuarioRepository.save(new Usuario("odonto1",
                passwordEncoder.encode("odonto1234"), "odonto@clinica.local",
                Rol.ODONTOLOGO, true)).getId();
        Long pacUserA = usuarioRepository.save(new Usuario("pac1",
                passwordEncoder.encode("paciente123"), "pac1@clinica.local",
                Rol.PACIENTE, true)).getId();
        Long pacUserB = usuarioRepository.save(new Usuario("pac2",
                passwordEncoder.encode("paciente123"), "pac2@clinica.local",
                Rol.PACIENTE, true)).getId();
        Long espId = especialidadService.crear(
                new CreateEspecialidadRequest("Ortodoncia", null)).id();
        prof1Id = profesionalService.crear(new CreateProfesionalRequest(
                "Ana", "Gómez", "M-400", null, null, null, List.of(espId))).id();
        prof2Id = profesionalService.crear(new CreateProfesionalRequest(
                "Luis", "Pérez", "M-401", null, null, null, List.of(espId))).id();
        // odontólogo vinculado a su ficha (scoping RN-AU-04)
        Profesional f1 = profesionalRepository.findById(prof1Id).orElseThrow();
        f1.setUsuarioId(odoUser1);
        profesionalRepository.saveAndFlush(f1);
        pacAId = pacienteRepository.save(new Paciente("Juan", "Pérez", "11111111",
                null, null, null, pacUserA)).getId();
        pacBId = pacienteRepository.save(new Paciente("María", "López", "22222222",
                null, null, null, pacUserB)).getId();
        hoy = LocalDate.now(TZ);
        turnoRepository.save(new Turno(pacBId, prof1Id, espId, hoy,
                LocalTime.of(10, 0), LocalTime.of(10, 30)));
        turnoRepository.save(new Turno(pacAId, prof1Id, espId, hoy,
                LocalTime.of(9, 0), LocalTime.of(9, 30)));
        turnoRepository.save(new Turno(pacAId, prof2Id, espId, hoy,
                LocalTime.of(11, 0), LocalTime.of(11, 30)));
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

    @Test
    void flujo3SecretariaDiaYSemana() throws Exception {
        MockHttpSession secre = login("secre1", "secre1234");
        // día con fecha default (sin params → hoy TZ)
        mockMvc.perform(get("/api/turnos")
                        .accept(MediaType.APPLICATION_JSON).session(secre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].horaInicio", is("09:00:00")))
                .andExpect(jsonPath("$.content[1].horaInicio", is("10:00:00")))
                .andExpect(jsonPath("$.content[2].horaInicio", is("11:00:00")))
                .andExpect(jsonPath("$.content[*].paciente.nombre").exists())
                .andExpect(jsonPath("$.content[*].profesional.nombre").exists())
                .andExpect(jsonPath("$.content[*].especialidad.nombre").exists())
                .andExpect(jsonPath("$.content[*].estado", everyItem(is("RESERVADO"))));
        // semana
        mockMvc.perform(get("/api/turnos")
                        .param("desde", hoy.toString())
                        .param("hasta", hoy.plusDays(6).toString())
                        .accept(MediaType.APPLICATION_JSON).session(secre))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void flujo3OdontologoSoloPropia() throws Exception {
        MockHttpSession odo = login("odonto1", "odonto1234");
        mockMvc.perform(get("/api/turnos")
                        .accept(MediaType.APPLICATION_JSON).session(odo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].horaInicio", is("09:00:00")));
        // agenda propia con huecos: 09:00 y 10:00 ocupados, 11:00 libre (es de prof2)
        mockMvc.perform(get("/api/profesionales/" + prof1Id + "/agenda")
                        .param("fecha", hoy.toString())
                        .accept(MediaType.APPLICATION_JSON).session(odo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ocupados", hasSize(2)))
                .andExpect(jsonPath("$.libres").isArray());
    }

    @Test
    void flujo3PacienteSoloSuyos() throws Exception {
        MockHttpSession pac = login("pac1", "paciente123");
        mockMvc.perform(get("/api/turnos")
                        .accept(MediaType.APPLICATION_JSON).session(pac))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].horaInicio", is("09:00:00")))
                .andExpect(jsonPath("$.content[1].horaInicio", is("11:00:00")));
    }
}
