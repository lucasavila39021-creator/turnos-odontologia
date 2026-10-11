package com.clinica.turnos.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clinica.turnos.domain.model.Paciente;
import com.clinica.turnos.domain.model.Rol;
import com.clinica.turnos.domain.model.Usuario;
import com.clinica.turnos.domain.repository.PacienteRepository;
import com.clinica.turnos.domain.repository.UsuarioRepository;
import com.clinica.turnos.security.RateLimitService;
import java.time.LocalDate;
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
 * E2E pacientes ABM sobre H2 (C-05 tasks 2.x/3.x/4.1).
 * Patrón espejo de UsuarioControllerH2Test: login real → sesión reutilizada.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PacienteControllerH2Test {

    @Autowired MockMvc mockMvc;
    @Autowired PacienteRepository pacienteRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired RateLimitService rateLimitService;

    private Usuario pacUser;
    private Usuario pacUser2;
    private Paciente fichaPropia;
    private Paciente fichaAjena;

    @BeforeEach
    void setUp() {
        rateLimitService.clear();
        pacienteRepository.deleteAll();
        usuarioRepository.deleteAll();
        usuarioRepository.save(new Usuario("admin", passwordEncoder.encode("admin123"),
                "admin@clinica.local", Rol.ADMIN, true));
        usuarioRepository.save(new Usuario("secre1", passwordEncoder.encode("secre1234"),
                "secre@clinica.local", Rol.SECRETARIA, true));
        usuarioRepository.save(new Usuario("odonto1", passwordEncoder.encode("odonto1234"),
                "odonto@clinica.local", Rol.ODONTOLOGO, true));
        pacUser = usuarioRepository.save(new Usuario("pac1", passwordEncoder.encode("paciente123"),
                "pac1@clinica.local", Rol.PACIENTE, true));
        pacUser2 = usuarioRepository.save(new Usuario("pac2", passwordEncoder.encode("paciente123"),
                "pac2@clinica.local", Rol.PACIENTE, true));
        fichaPropia = pacienteRepository.save(new Paciente("Juan", "Pérez", "30111222",
                null, null, LocalDate.of(1990, 1, 1), pacUser.getId()));
        fichaAjena = pacienteRepository.save(new Paciente("María", "Gómez", "30222333",
                null, null, null, pacUser2.getId()));
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

    // Alta ok 201 como secretaria
    @Test
    void altaOk201() throws Exception {
        MockHttpSession session = login("secre1", "secre1234");
        mockMvc.perform(post("/api/pacientes").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana\",\"apellido\":\"Gómez\",\"dni\":\"30444555\","
                                + "\"telefono\":\"+54 11 1234-5678\",\"email\":\"ana@mail.com\","
                                + "\"fechaNacimiento\":\"1990-05-01\"}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Ana"))
                .andExpect(jsonPath("$.dni").value("30444555"))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
        assertThat(pacienteRepository.findByDni("30444555")).isPresent();
    }

    // DNI con espacios se normaliza por trim
    @Test
    void altaDniConEspaciosSeNormaliza() throws Exception {
        MockHttpSession session = login("secre1", "secre1234");
        mockMvc.perform(post("/api/pacientes").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana\",\"apellido\":\"López\",\"dni\":\"  30555666  \"}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dni").value("30555666"));
    }

    // DNI duplicado → 409 con link a ficha existente, sin fila nueva
    @Test
    void dniDuplicado409ConLink() throws Exception {
        MockHttpSession session = login("secre1", "secre1234");
        long antes = pacienteRepository.count();
        mockMvc.perform(post("/api/pacientes").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Otro\",\"apellido\":\"Otro\",\"dni\":\"30111222\"}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.dni").exists())
                .andExpect(jsonPath("$.pacienteId").value(fichaPropia.getId()))
                .andExpect(jsonPath("$.pacienteUri").value("/api/pacientes/" + fichaPropia.getId()));
        assertThat(pacienteRepository.count()).isEqualTo(antes);
    }

    // Obligatorios faltantes → 400 por campo, nunca 500
    @Test
    void obligatoriosFaltantes400() throws Exception {
        MockHttpSession session = login("secre1", "secre1234");
        mockMvc.perform(post("/api/pacientes").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apellido\":\"Gómez\",\"dni\":\"30999888\"}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.nombre").exists());
        mockMvc.perform(post("/api/pacientes").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana\",\"apellido\":\"Gómez\",\"dni\":\"   \"}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.dni").exists());
        assertThat(pacienteRepository.findByDni("30999888")).isEmpty();
    }

    // Email inválido → 400
    @Test
    void emailInvalido400() throws Exception {
        MockHttpSession session = login("secre1", "secre1234");
        mockMvc.perform(post("/api/pacientes").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana\",\"apellido\":\"Gómez\",\"dni\":\"30999777\","
                                + "\"email\":\"no-es-mail\"}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
        assertThat(pacienteRepository.findByDni("30999777")).isEmpty();
    }

    // Fecha futura → 400
    @Test
    void fechaFutura400() throws Exception {
        MockHttpSession session = login("secre1", "secre1234");
        mockMvc.perform(post("/api/pacientes").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana\",\"apellido\":\"Gómez\",\"dni\":\"30999666\","
                                + "\"fechaNacimiento\":\"2099-01-01\"}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.fechaNacimiento").exists());
    }

    // Búsqueda por apellido prefijo case-insensitive + orden apellido,nombre
    // (fixtures ASCII: el orden con tildes depende del collation de cada DB)
    @Test
    void busquedaApellidoPrefijo() throws Exception {
        MockHttpSession session = login("secre1", "secre1234");
        pacienteRepository.save(new Paciente("Ana", "Alvarez", "30333444",
                null, null, null, null));
        pacienteRepository.save(new Paciente("Luis", "Arias", "30333555",
                null, null, null, null));
        mockMvc.perform(get("/api/pacientes?apellido=A")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].apellido").value("Alvarez"))
                .andExpect(jsonPath("$.content[1].apellido").value("Arias"));
    }

    // Búsqueda por DNI exacto + página vacía
    @Test
    void busquedaDniExacto() throws Exception {
        MockHttpSession session = login("secre1", "secre1234");
        mockMvc.perform(get("/api/pacientes?dni=30111222")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].dni").value("30111222"));
        mockMvc.perform(get("/api/pacientes?dni=99999999")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    // Ficha inexistente → 404
    @Test
    void fichaInexistente404() throws Exception {
        MockHttpSession session = login("secre1", "secre1234");
        mockMvc.perform(get("/api/pacientes/999999")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isNotFound());
    }

    // Edición parcial ok
    @Test
    void patchParcial200() throws Exception {
        MockHttpSession session = login("secre1", "secre1234");
        mockMvc.perform(patch("/api/pacientes/" + fichaPropia.getId()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telefono\":\"+54 11 9999-0000\"}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefono").value("+54 11 9999-0000"))
                .andExpect(jsonPath("$.nombre").value("Juan"));
    }

    // DNI inmutable: intento de cambiarlo → 400 con fieldErrors.dni y ficha intacta
    @Test
    void patchDniInmutable400() throws Exception {
        MockHttpSession session = login("secre1", "secre1234");
        mockMvc.perform(patch("/api/pacientes/" + fichaPropia.getId()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dni\":\"99999999\"}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.dni").exists());
        assertThat(pacienteRepository.findById(fichaPropia.getId()).orElseThrow().getDni())
                .isEqualTo("30111222");
    }

    // Email en blanco se normaliza a null
    @Test
    void patchEmailBlancoNull() throws Exception {
        MockHttpSession session = login("secre1", "secre1234");
        pacienteRepository.findById(fichaPropia.getId()).ifPresent(p -> {
            p.setEmail("juan@mail.com");
            pacienteRepository.saveAndFlush(p);
        });
        mockMvc.perform(patch("/api/pacientes/" + fichaPropia.getId()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"   \"}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", org.hamcrest.Matchers.nullValue()));
    }

    // Email con formato inválido en PATCH → 400
    @Test
    void patchEmailInvalido400() throws Exception {
        MockHttpSession session = login("secre1", "secre1234");
        mockMvc.perform(patch("/api/pacientes/" + fichaPropia.getId()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"no-es-mail\"}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    // Historial vacío pre-C-08 + 404 si no existe
    @Test
    void historialVacio200() throws Exception {
        MockHttpSession session = login("secre1", "secre1234");
        mockMvc.perform(get("/api/pacientes/" + fichaPropia.getId() + "/turnos")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
        mockMvc.perform(get("/api/pacientes/999999/turnos")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isNotFound());
    }

    // Link a usuario PACIENTE + doble link → 409
    @Test
    void linkUsuarioOkYDobleLink409() throws Exception {
        MockHttpSession session = login("secre1", "secre1234");
        Usuario libre = usuarioRepository.save(new Usuario("paclibre", passwordEncoder.encode("paciente123"),
                "libre@clinica.local", Rol.PACIENTE, true));
        mockMvc.perform(post("/api/pacientes").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Libre\",\"apellido\":\"López\",\"dni\":\"30777888\","
                                + "\"usuarioId\":" + libre.getId() + "}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usuarioId").value(libre.getId()));
        mockMvc.perform(post("/api/pacientes").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Otro\",\"apellido\":\"Otro\",\"dni\":\"30777999\","
                                + "\"usuarioId\":" + libre.getId() + "}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.usuarioId").exists());
        assertThat(pacienteRepository.findByDni("30777999")).isEmpty();
    }

    // Link a usuario inexistente → 404
    @Test
    void linkUsuarioInexistente404() throws Exception {
        MockHttpSession session = login("secre1", "secre1234");
        mockMvc.perform(post("/api/pacientes").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"X\",\"apellido\":\"Y\",\"dni\":\"30777666\","
                                + "\"usuarioId\":999999}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isNotFound());
    }

    // Link a usuario con rol no-PACIENTE → 400
    @Test
    void linkRolNoPaciente400() throws Exception {
        MockHttpSession session = login("secre1", "secre1234");
        Long odontoId = usuarioRepository.findByUsername("odonto1").orElseThrow().getId();
        mockMvc.perform(post("/api/pacientes").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"X\",\"apellido\":\"Y\",\"dni\":\"30777555\","
                                + "\"usuarioId\":" + odontoId + "}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.usuarioId").exists());
    }

    // Desvincular con null explícito en PATCH
    @Test
    void unlinkNullExplicito200() throws Exception {
        MockHttpSession session = login("secre1", "secre1234");
        assertThat(fichaPropia.getUsuarioId()).isNotNull();
        mockMvc.perform(patch("/api/pacientes/" + fichaPropia.getId()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":null}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId", org.hamcrest.Matchers.nullValue()));
        assertThat(pacienteRepository.findById(fichaPropia.getId()).orElseThrow().getUsuarioId())
                .isNull();
    }

    // RBAC: paciente ve propia 200, ajena 403, lista 403, POST 403
    @Test
    void pacienteOwnership() throws Exception {
        MockHttpSession session = login("pac1", "paciente123");
        mockMvc.perform(get("/api/pacientes/" + fichaPropia.getId())
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/pacientes/" + fichaAjena.getId())
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/pacientes")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/pacientes").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"X\",\"apellido\":\"Y\",\"dni\":\"30000001\"}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/pacientes/" + fichaPropia.getId() + "/turnos")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isOk());
        // PATCH propia ok
        mockMvc.perform(patch("/api/pacientes/" + fichaPropia.getId()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telefono\":\"111\"}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isOk());
        // PATCH ajena 403
        mockMvc.perform(patch("/api/pacientes/" + fichaAjena.getId()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telefono\":\"111\"}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isForbidden());
    }

    // RBAC: odontólogo solo lectura
    @Test
    void odontologoSoloLectura() throws Exception {
        MockHttpSession session = login("odonto1", "odonto1234");
        mockMvc.perform(get("/api/pacientes/" + fichaPropia.getId())
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/pacientes")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/pacientes").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"X\",\"apellido\":\"Y\",\"dni\":\"30000002\"}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/pacientes/" + fichaPropia.getId()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telefono\":\"111\"}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isForbidden());
        assertThat(pacienteRepository.findByDni("30000002")).isEmpty();
    }

    // RBAC: admin acceso completo
    @Test
    void adminAccesoCompleto() throws Exception {
        MockHttpSession session = login("admin", "admin123");
        mockMvc.perform(get("/api/pacientes")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/pacientes").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Adm\",\"apellido\":\"Crea\",\"dni\":\"30000003\"}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isCreated());
    }

    // Sin DELETE físico en v1
    @Test
    void deleteNoExiste() throws Exception {
        MockHttpSession session = login("secre1", "secre1234");
        mockMvc.perform(delete("/api/pacientes/" + fichaPropia.getId()).with(csrf())
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().is4xxClientError());
        assertThat(pacienteRepository.findById(fichaPropia.getId())).isPresent();
    }

    // Sin sesión: 401 JSON / 302 HTML
    @Test
    void sinSesion401o302() throws Exception {
        mockMvc.perform(get("/api/pacientes").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/pacientes").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection());
    }

    // Repositorio: persiste y recupera por DNI/usuario_id; toString sin PII
    @Test
    void repositorioDniYUsuarioId() {
        assertThat(pacienteRepository.findByDni("30111222")).isPresent();
        assertThat(pacienteRepository.existsByDni("30111222")).isTrue();
        assertThat(pacienteRepository.findByUsuarioId(pacUser.getId())).isPresent();
        assertThat(pacienteRepository.existsByUsuarioId(999999L)).isFalse();
        assertThat(pacienteRepository.findByApellidoStartingWithIgnoreCase("pér",
                org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements())
                .isEqualTo(1);
        String str = fichaPropia.toString();
        assertThat(str).doesNotContain("30111222");
        assertThat(str).doesNotContain("Pérez");
    }
}
