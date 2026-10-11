package com.clinica.turnos.web.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clinica.turnos.application.dto.CreateEspecialidadRequest;
import com.clinica.turnos.application.dto.CreateProfesionalRequest;
import com.clinica.turnos.application.service.EspecialidadService;
import com.clinica.turnos.application.service.ProfesionalService;
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
 * C-12 task 3.2 (TDD borde horario, RN-TU-03/04, TZ RN-GL-02):
 * 07:59 → 400, 08:00 → 201, hoy hora pasada → 400.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TurnoHorarioBordeTest {

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
    private Long pacId;

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
                "Ana", "Borde", "M-BORDE", null, null, null, List.of(espId))).id();
        pacId = pacienteRepository.save(new Paciente("Juan", "Borde", "88888888",
                null, null, null, null)).getId();
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

    private String turnoJson(LocalDate fecha, String hora) {
        return "{\"pacienteId\":" + pacId + ",\"profesionalId\":" + profId
                + ",\"especialidadId\":" + espId + ",\"fecha\":\"" + fecha
                + "\",\"horaInicio\":\"" + hora + "\"}";
    }

    @Test
    void reserva0759Rechaza() throws Exception {
        MockHttpSession secre = loginSecre();
        LocalDate manana = LocalDate.now(TZ).plusDays(1);
        mockMvc.perform(post("/api/turnos").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(turnoJson(manana, "07:59"))
                        .accept(MediaType.APPLICATION_JSON).session(secre))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.hora_inicio").exists())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void reserva0800Acepta() throws Exception {
        MockHttpSession secre = loginSecre();
        LocalDate manana = LocalDate.now(TZ).plusDays(1);
        mockMvc.perform(post("/api/turnos").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(turnoJson(manana, "08:00"))
                        .accept(MediaType.APPLICATION_JSON).session(secre))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.estado").value("RESERVADO"));
    }

    @Test
    void reservaHoyHoraPasadaRechaza() throws Exception {
        MockHttpSession secre = loginSecre();
        LocalTime ahora = LocalTime.now(TZ);
        LocalDate fecha;
        String hora;
        LocalTime pasada = ahora.minusHours(2).withMinute(0).withSecond(0).withNano(0);
        LocalTime desde = LocalTime.of(8, 0);
        LocalTime hasta = LocalTime.of(18, 0);
        if (!pasada.isBefore(desde) && !pasada.plusMinutes(30).isAfter(hasta)) {
            fecha = LocalDate.now(TZ);
            hora = pasada.toString();
        } else if (ahora.isAfter(LocalTime.of(17, 30))) {
            // Noche: 17:00 de hoy ya pasó y está dentro del horario → RN-TU-04.
            fecha = LocalDate.now(TZ);
            hora = "17:00";
        } else {
            // Madrugada: no hay hora pasada dentro del horario → ayer 10:00
            // sigue ejercitando RN-TU-04 (pasado según TZ clínica).
            fecha = LocalDate.now(TZ).minusDays(1);
            hora = "10:00";
        }
        mockMvc.perform(post("/api/turnos").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(turnoJson(fecha, hora))
                        .accept(MediaType.APPLICATION_JSON).session(secre))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.fecha").exists());
    }
}
