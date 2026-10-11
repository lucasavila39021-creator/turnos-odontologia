package com.clinica.turnos.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clinica.turnos.application.exception.DuplicateUsuarioException;
import com.clinica.turnos.domain.model.Rol;
import com.clinica.turnos.domain.model.Usuario;
import com.clinica.turnos.domain.repository.UsuarioRepository;
import com.clinica.turnos.security.RateLimitService;
import com.clinica.turnos.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * E2E admin user-management sobre H2 (C-04 tasks 4.1-4.3 + 1.1/3.x).
 * Patrón espejo de AuthControllerH2Test: login real → sesión reutilizada.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UsuarioControllerH2Test {

    @Autowired MockMvc mockMvc;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService.clear();
        usuarioRepository.deleteAll();
        usuarioRepository.save(new Usuario("admin", passwordEncoder.encode("admin123"),
                "admin@clinica.local", Rol.ADMIN, true));
        usuarioRepository.save(new Usuario("paciente1", passwordEncoder.encode("paciente123"),
                "paciente@clinica.local", Rol.PACIENTE, true));
        usuarioRepository.save(new Usuario("odonto1", passwordEncoder.encode("odonto1234"),
                "odonto@clinica.local", Rol.ODONTOLOGO, true));
        usuarioRepository.save(new Usuario("secre1", passwordEncoder.encode("secre1234"),
                "secre@clinica.local", Rol.SECRETARIA, true));
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

    // 1.3 — preflight CORS incluye PATCH
    @Test
    void preflightOptionsIncluyePatch() throws Exception {
        mockMvc.perform(options("/api/usuarios")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "PATCH"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Methods",
                        org.hamcrest.Matchers.containsString("PATCH")));
    }

    // 1.1 — conteo de admins activos
    @Test
    void countByRolAndActivoTrue() {
        assertThat(usuarioRepository.countByRolAndActivoTrue(Rol.ADMIN)).isEqualTo(1);
        usuarioRepository.save(new Usuario("admin2", passwordEncoder.encode("admin12345"),
                "admin2@clinica.local", Rol.ADMIN, true));
        assertThat(usuarioRepository.countByRolAndActivoTrue(Rol.ADMIN)).isEqualTo(2);
        Usuario admin2 = usuarioRepository.findByUsername("admin2").orElseThrow();
        admin2.setActivo(false);
        usuarioRepository.saveAndFlush(admin2);
        assertThat(usuarioRepository.countByRolAndActivoTrue(Rol.ADMIN)).isEqualTo(1);
    }

    // 4.1 — ADMIN lista paginado
    @Test
    void adminListaUsuariosPaginado() throws Exception {
        MockHttpSession session = login("admin", "admin123");
        mockMvc.perform(get("/api/usuarios?page=0&size=20")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.content[0].password_hash").doesNotExist());
    }

    // 3.2/4.1 — alta válida 201 con BCrypt(12)
    @Test
    void adminAltaValida201() throws Exception {
        MockHttpSession session = login("admin", "admin123");
        String body = mockMvc.perform(post("/api/usuarios").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nuevo_od\",\"password\":\"segura123\","
                                + "\"rol\":\"ODONTOLOGO\",\"email\":\"nuevo@clinica.local\"}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("nuevo_od"))
                .andExpect(jsonPath("$.rol").value("ODONTOLOGO"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("password");
        assertThat(body).doesNotContain("$2a$");

        Usuario persisted = usuarioRepository.findByUsername("nuevo_od").orElseThrow();
        assertThat(persisted.getPasswordHash()).startsWith("$2a$12$");
        assertThat(passwordEncoder.matches("segura123", persisted.getPasswordHash())).isTrue();
    }

    // 4.3 — username duplicado 409
    @Test
    void usernameDuplicado409() throws Exception {
        MockHttpSession session = login("admin", "admin123");
        mockMvc.perform(post("/api/usuarios").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"odonto1\",\"password\":\"segura123\","
                                + "\"rol\":\"ODONTOLOGO\",\"email\":\"otro@clinica.local\"}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.username").exists());
        assertThat(usuarioRepository.findByUsername("odonto1").orElseThrow()
                .getEmail()).isEqualTo("odonto@clinica.local");
    }

    // 4.3 — email duplicado 409
    @Test
    void emailDuplicado409() throws Exception {
        MockHttpSession session = login("admin", "admin123");
        String body = mockMvc.perform(post("/api/usuarios").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"otro_user\",\"password\":\"segura123\","
                                + "\"rol\":\"PACIENTE\",\"email\":\"odonto@clinica.local\"}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("password");
        assertThat(usuarioRepository.findByUsername("otro_user")).isEmpty();
    }

    // 4.3 — password corto 400
    @Test
    void passwordCorto400() throws Exception {
        MockHttpSession session = login("admin", "admin123");
        mockMvc.perform(post("/api/usuarios").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"corto1\",\"password\":\"corta\","
                                + "\"rol\":\"PACIENTE\"}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
        assertThat(usuarioRepository.findByUsername("corto1")).isEmpty();
    }

    // 4.3 — rol inválido 400
    @Test
    void rolInvalido400() throws Exception {
        MockHttpSession session = login("admin", "admin123");
        mockMvc.perform(post("/api/usuarios").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"rolmal\",\"password\":\"segura123\","
                                + "\"rol\":\"INVALID\"}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isBadRequest());
        assertThat(usuarioRepository.findByUsername("rolmal")).isEmpty();
    }

    // 3.3/4.1 — cambio de rol válido 200
    @Test
    void cambioRolValido200() throws Exception {
        MockHttpSession session = login("admin", "admin123");
        Long id = usuarioRepository.findByUsername("paciente1").orElseThrow().getId();
        mockMvc.perform(patch("/api/usuarios/" + id).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rol\":\"SECRETARIA\"}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("SECRETARIA"));
        assertThat(usuarioRepository.findByUsername("paciente1").orElseThrow().getRol())
                .isEqualTo(Rol.SECRETARIA);
    }

    // 4.3 — PATCH vacío 400
    @Test
    void patchVacio400() throws Exception {
        MockHttpSession session = login("admin", "admin123");
        Long id = usuarioRepository.findByUsername("paciente1").orElseThrow().getId();
        mockMvc.perform(patch("/api/usuarios/" + id).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isBadRequest());
    }

    // 3.3/4.1 — desactivar usuario común 200
    @Test
    void desactivarComun200() throws Exception {
        MockHttpSession session = login("admin", "admin123");
        Long id = usuarioRepository.findByUsername("paciente1").orElseThrow().getId();
        mockMvc.perform(patch("/api/usuarios/" + id + "/desactivar").with(csrf())
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
        // desactivado ya no puede loguearse
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"paciente1\",\"password\":\"paciente123\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    // 4.3 — desactivar último admin 400 sin mutación
    @Test
    void desactivarUltimoAdmin400() throws Exception {
        MockHttpSession session = login("admin", "admin123");
        Long id = usuarioRepository.findByUsername("admin").orElseThrow().getId();
        String body = mockMvc.perform(patch("/api/usuarios/" + id + "/desactivar").with(csrf())
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("ltimo administrador");
        assertThat(body).doesNotContain("password");
        assertThat(usuarioRepository.findByUsername("admin").orElseThrow().isActivo()).isTrue();
        assertThat(usuarioRepository.countByRolAndActivoTrue(Rol.ADMIN)).isEqualTo(1);
    }

    // 4.3 — degradar último admin 400 sin mutación
    @Test
    void degradarUltimoAdmin400() throws Exception {
        MockHttpSession session = login("admin", "admin123");
        Long id = usuarioRepository.findByUsername("admin").orElseThrow().getId();
        mockMvc.perform(patch("/api/usuarios/" + id).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rol\":\"SECRETARIA\"}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isBadRequest());
        assertThat(usuarioRepository.findByUsername("admin").orElseThrow().getRol()).isEqualTo(Rol.ADMIN);
    }

    // 3.4 — mapeo DataIntegrityViolation → 409 según constraint
    @Test
    void dataIntegrityViolationMapea409segunConstraint() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        var emailEx = new DataIntegrityViolationException(
                "could not execute statement [Duplicate entry 'x' for key 'uk_usuario_email']");
        var emailResp = handler.handleConflict(emailEx);
        assertThat(emailResp.getStatusCode().value()).isEqualTo(409);
        assertThat(emailResp.getBody()).containsKey("fieldErrors");

        var userEx = new DataIntegrityViolationException(
                "could not execute statement [Duplicate entry 'x' for key 'uk_usuario_username']");
        var userResp = handler.handleConflict(userEx);
        assertThat(userResp.getStatusCode().value()).isEqualTo(409);

        var dupResp = handler.handleDuplicate(new DuplicateUsuarioException("username", "username ya registrado"));
        assertThat(dupResp.getStatusCode().value()).isEqualTo(409);
    }

    // 4.2 — RBAC: roles no-admin reciben 403 en los 4 endpoints
    @Test
    void rbacNoAdmin403() throws Exception {
        Long targetId = usuarioRepository.findByUsername("paciente1").orElseThrow().getId();
        String[][] creds = {
                {"paciente1", "paciente123"},
                {"odonto1", "odonto1234"},
                {"secre1", "secre1234"}
        };
        for (String[] c : creds) {
            MockHttpSession s = login(c[0], c[1]);
            mockMvc.perform(get("/api/usuarios").accept(MediaType.APPLICATION_JSON).session(s))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/usuarios").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"intruso_" + c[0] + "\",\"password\":\"segura123\","
                                    + "\"rol\":\"PACIENTE\"}")
                            .accept(MediaType.APPLICATION_JSON).session(s))
                    .andExpect(status().isForbidden());
            mockMvc.perform(patch("/api/usuarios/" + targetId).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"rol\":\"SECRETARIA\"}")
                            .accept(MediaType.APPLICATION_JSON).session(s))
                    .andExpect(status().isForbidden());
            mockMvc.perform(patch("/api/usuarios/" + targetId + "/desactivar").with(csrf())
                            .accept(MediaType.APPLICATION_JSON).session(s))
                    .andExpect(status().isForbidden());
            assertThat(usuarioRepository.findByUsername("intruso_" + c[0])).isEmpty();
        }
    }

    // 4.2 — sin sesión: 401 JSON / 302 HTML
    @Test
    void sinSesion401o302() throws Exception {
        mockMvc.perform(get("/api/usuarios").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/usuarios").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection());
    }
}
