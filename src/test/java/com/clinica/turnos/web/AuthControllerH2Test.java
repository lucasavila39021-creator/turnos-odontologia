package com.clinica.turnos.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clinica.turnos.domain.model.Rol;
import com.clinica.turnos.domain.model.Usuario;
import com.clinica.turnos.domain.repository.UsuarioRepository;
import com.clinica.turnos.security.RateLimitService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * H2-based quick verification of auth — mirrors AuthControllerTest but runs without Docker.
 * Used for local TDD green evidence when Docker unavailable.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerH2Test {

    @Autowired MockMvc mockMvc;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService.clear();
        usuarioRepository.deleteAll();
        String hashAdmin = passwordEncoder.encode("admin123");
        String hashInact = passwordEncoder.encode("inact123");
        String hashPac = passwordEncoder.encode("paciente123");
        usuarioRepository.save(new Usuario("admin", hashAdmin, "admin@clinica.local", Rol.ADMIN, true));
        usuarioRepository.save(new Usuario("inact", hashInact, "inact@clinica.local", Rol.PACIENTE, false));
        usuarioRepository.save(new Usuario("paciente1", hashPac, "paciente@clinica.local", Rol.PACIENTE, true));
    }

    @Test
    void login_ok() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"admin123\"}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.rol").value("ADMIN"))
                .andReturn();
        org.springframework.mock.web.MockHttpSession session = (org.springframework.mock.web.MockHttpSession) result.getRequest().getSession(false);
        org.junit.jupiter.api.Assertions.assertNotNull(session);
        mockMvc.perform(get("/api/auth/me").accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"));
    }

    @Test
    void login_wrong() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"wrong\"}").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_inactive() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"inact\",\"password\":\"inact123\"}").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_withoutSession() throws Exception {
        mockMvc.perform(get("/api/auth/me").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void rateLimit() throws Exception {
        rateLimitService.clear();
        String ip = "1.1.1.9";
        for (int i=0;i<5;i++) {
            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"admin\",\"password\":\"wrong\"}").header("X-Forwarded-For", ip).accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"wrong\"}").header("X-Forwarded-For", ip).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isTooManyRequests());
        // different user not limited
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"other\",\"password\":\"wrong\"}").header("X-Forwarded-For", ip).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rbac() throws Exception {
        MvcResult paciente = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"paciente1\",\"password\":\"paciente123\"}").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();
        org.springframework.mock.web.MockHttpSession pacSession = (org.springframework.mock.web.MockHttpSession) paciente.getRequest().getSession(false);
        mockMvc.perform(get("/api/test/admin-only").accept(MediaType.APPLICATION_JSON).session(pacSession))
                .andExpect(status().isForbidden());
        MvcResult admin = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"admin123\"}").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();
        org.springframework.mock.web.MockHttpSession adminSession = (org.springframework.mock.web.MockHttpSession) admin.getRequest().getSession(false);
        mockMvc.perform(get("/api/test/admin-only").accept(MediaType.APPLICATION_JSON).session(adminSession))
                .andExpect(status().isOk());
    }

    @Test
    void logout() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"admin123\"}").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();
        org.springframework.mock.web.MockHttpSession session = (org.springframework.mock.web.MockHttpSession) login.getRequest().getSession(false);
        mockMvc.perform(post("/api/auth/logout").session(session).with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/auth/me").accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void caseInsensitive() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"ADMIN\",\"password\":\"admin123\"}").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void formLogin_redirectByRole() throws Exception {
        mockMvc.perform(post("/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", "admin")
                .param("password", "admin123")
                .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/agenda")));
        mockMvc.perform(post("/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", "paciente1")
                .param("password", "paciente123")
                .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/mis-turnos")));
    }
}
