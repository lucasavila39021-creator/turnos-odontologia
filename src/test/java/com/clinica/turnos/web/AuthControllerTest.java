package com.clinica.turnos.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clinica.turnos.domain.model.Rol;
import com.clinica.turnos.domain.model.Usuario;
import com.clinica.turnos.domain.repository.UsuarioRepository;
import com.clinica.turnos.security.RateLimitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration tests for auth — C-03 8.1-8.4.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AuthControllerTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("turnos_test")
            .withUsername("test")
            .withPassword("test")
            .withEnv("MYSQL_ROOT_PASSWORD", "root");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("app.timezone", () -> "America/Argentina/Buenos_Aires");
        registry.add("APP_COOKIE_SECURE", () -> "false");
    }

    @Autowired MockMvc mockMvc;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired RateLimitService rateLimitService;
    @Autowired ObjectMapper objectMapper;

    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASS = "admin123";
    private static final String INACT_USER = "inact";
    private static final String INACT_PASS = "inact123";
    private static final String PACIENTE_USER = "paciente1";
    private static final String PACIENTE_PASS = "paciente123";

    @BeforeEach
    void setUp() {
        rateLimitService.clear();
        // Seed users if not exist
        String hashAdmin = passwordEncoder.encode(ADMIN_PASS);
        String hashInact = passwordEncoder.encode(INACT_PASS);
        String hashPac = passwordEncoder.encode(PACIENTE_PASS);
        usuarioRepository.findByUsernameIgnoreCase(ADMIN_USER).orElseGet(() -> usuarioRepository.save(new Usuario(ADMIN_USER, hashAdmin, "admin@clinica.local", Rol.ADMIN, true)));
        usuarioRepository.findByUsernameIgnoreCase(INACT_USER).orElseGet(() -> {
            Usuario u = new Usuario(INACT_USER, hashInact, "inact@clinica.local", Rol.PACIENTE, false);
            return usuarioRepository.save(u);
        });
        // ensure inactive stays inactive
        usuarioRepository.findByUsernameIgnoreCase(INACT_USER).ifPresent(u -> {
            if (u.isActivo()) { u.setActivo(false); usuarioRepository.save(u); }
        });
        usuarioRepository.findByUsernameIgnoreCase(PACIENTE_USER).orElseGet(() -> usuarioRepository.save(new Usuario(PACIENTE_USER, hashPac, "paciente@clinica.local", Rol.PACIENTE, true)));
    }

    @Test
    void login_ok_returns200AndMeOk() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + ADMIN_USER + "\",\"password\":\"" + ADMIN_PASS + "\"}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(ADMIN_USER))
                .andExpect(jsonPath("$.rol").value("ADMIN"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();
        // Session verification (MockMvc uses MockHttpSession, Set-Cookie header not present in mock)
        org.springframework.mock.web.MockHttpSession session = (org.springframework.mock.web.MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        // Verify application.properties contains SameSite config (real cookie check via unit test)
        // call me with session
        mockMvc.perform(get("/api/auth/me")
                .accept(MediaType.APPLICATION_JSON)
                .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(ADMIN_USER))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void login_wrongPassword_returns401Generic() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + ADMIN_USER + "\",\"password\":\"wrong\"}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Credenciales inválidas"));
        // same for non-existent user should also be 401 generic (case)
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"noexiste\",\"password\":\"wrong\"}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Credenciales inválidas"));
    }

    @Test
    void login_inactive_returns401Generic() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + INACT_USER + "\",\"password\":\"" + INACT_PASS + "\"}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Credenciales inválidas"));
    }

    @Test
    void me_withoutSession_returns401JsonAnd302Form() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me")
                .accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/login")));
    }

    @Test
    void rateLimit_after5Fails_429_andDistinctUsernameNotLimited() throws Exception {
        rateLimitService.clear();
        String ip = "1.1.1.1";
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"admin\",\"password\":\"wrong\"}")
                    .header("X-Forwarded-For", ip)
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"wrong\"}")
                .header("X-Forwarded-For", ip)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429));

        // different username same IP should not be limited
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"other\",\"password\":\"wrong\"}")
                .header("X-Forwarded-For", ip)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        // GET /me should not consume bucket
        mockMvc.perform(get("/api/auth/me")
                .header("X-Forwarded-For", ip)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        // again other still not 429
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"other\",\"password\":\"wrong\"}")
                .header("X-Forwarded-For", ip)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rbac_adminOnly_paciente403_admin200() throws Exception {
        // login as paciente
        MvcResult pacienteLogin = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + PACIENTE_USER + "\",\"password\":\"" + PACIENTE_PASS + "\"}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        org.springframework.mock.web.MockHttpSession pacSession = (org.springframework.mock.web.MockHttpSession) pacienteLogin.getRequest().getSession(false);
        mockMvc.perform(get("/api/test/admin-only")
                .accept(MediaType.APPLICATION_JSON)
                .session(pacSession))
                .andExpect(status().isForbidden());

        // admin
        MvcResult adminLogin = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + ADMIN_USER + "\",\"password\":\"" + ADMIN_PASS + "\"}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        org.springframework.mock.web.MockHttpSession adminSession = (org.springframework.mock.web.MockHttpSession) adminLogin.getRequest().getSession(false);
        mockMvc.perform(get("/api/test/admin-only")
                .accept(MediaType.APPLICATION_JSON)
                .session(adminSession))
                .andExpect(status().isOk());
        // health without session still 200
        mockMvc.perform(get("/api/health")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void logout_invalidatesSession_me401() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + ADMIN_USER + "\",\"password\":\"" + ADMIN_PASS + "\"}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        org.springframework.mock.web.MockHttpSession session = (org.springframework.mock.web.MockHttpSession) login.getRequest().getSession(false);
        mockMvc.perform(post("/api/auth/logout")
                .session(session)
                .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/auth/me")
                .accept(MediaType.APPLICATION_JSON)
                .session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_caseInsensitive_ok() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"ADMIN\",\"password\":\"" + ADMIN_PASS + "\"}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(ADMIN_USER.toLowerCase() .equals("admin") ? "admin" : "ADMIN"));
        // at least 200 even with upper case
    }

    @Test
    void login_blank_returns400_fieldErrors() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"\",\"password\":\"x\"}")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.username").exists());
    }

    @Test
    void getLogin_returns200Html() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    @Test
    void css_withoutSession_permitAll() throws Exception {
        mockMvc.perform(get("/css/main.css"))
                .andExpect(status().isNotFound()); // file may not exist -> 404 is also permitAll not 401/302, but we test not 401/302
        // Ensure not 401
        // Alternative check: permitAll for /css/** means not redirect to login
        // If spring returns 404, it's not auth failure
    }
}
