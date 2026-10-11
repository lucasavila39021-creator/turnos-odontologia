package com.clinica.turnos.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clinica.turnos.domain.model.Rol;
import com.clinica.turnos.domain.model.Usuario;
import com.clinica.turnos.domain.repository.UsuarioRepository;
import com.clinica.turnos.security.RateLimitService;
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
 * C-12 task 2.2/2.3: envelope unificado {error,message,fieldErrors,sugeridos}
 * en 400/401/403/409 (detail se mantiene por compat), 401 genérico y
 * JSON malformado → 400 nunca 500.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ErrorEnvelopeTest {

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
    }

    private MockHttpSession loginAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    void loginInvalido401Generico() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"otra\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Credenciales inválidas"))
                .andExpect(jsonPath("$.error").value("Credenciales inválidas"))
                .andExpect(jsonPath("$.message").value("Credenciales inválidas"));
    }

    @Test
    void validacion400ConEnvelope() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.username").exists())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void jsonMalformado400No500() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.body").exists())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void duplicado409ConEnvelope() throws Exception {
        MockHttpSession admin = loginAdmin();
        String body = "{\"username\":\"otro\",\"password\":\"secreta1234\","
                + "\"email\":\"admin@clinica.local\",\"rol\":\"SECRETARIA\"}";
        mockMvc.perform(post("/api/usuarios").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .accept(MediaType.APPLICATION_JSON).session(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors").exists())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.detail").exists());
    }
}
