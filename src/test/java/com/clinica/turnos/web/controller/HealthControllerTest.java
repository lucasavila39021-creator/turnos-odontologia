package com.clinica.turnos.web.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clinica.turnos.config.SecurityConfig;
import com.clinica.turnos.security.CustomUserDetailsService;
import com.clinica.turnos.security.LoginRateLimitFilter;
import com.clinica.turnos.security.RateLimitService;
import com.clinica.turnos.security.RoleRedirectAuthenticationSuccessHandler;
import com.clinica.turnos.security.SecurityJsonHandlers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HealthController.class)
@Import(SecurityConfig.class)
class HealthControllerTest {

    @MockBean RateLimitService rateLimitService;
    @MockBean LoginRateLimitFilter loginRateLimitFilter;
    @MockBean RoleRedirectAuthenticationSuccessHandler roleRedirectHandler;
    @MockBean SecurityJsonHandlers securityJsonHandlers;
    @MockBean CustomUserDetailsService customUserDetailsService;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void stubHandlers() throws Exception {
        // Provide minimal handlers to avoid NPE from mocked SecurityJsonHandlers in SecurityConfig
        Mockito.lenient().when(securityJsonHandlers.failureHandler()).thenReturn((req, res, ex) -> res.sendError(401));
        Mockito.lenient().when(securityJsonHandlers.entryPoint()).thenReturn((req, res, ex) -> res.sendError(401));
        Mockito.lenient().when(securityJsonHandlers.accessDeniedHandler()).thenReturn((req, res, ex) -> res.sendError(403));
        // Mock filter pass-through
        Mockito.lenient().doAnswer(inv -> {
            jakarta.servlet.http.HttpServletRequest rq = inv.getArgument(0);
            jakarta.servlet.http.HttpServletResponse rs = inv.getArgument(1);
            jakarta.servlet.FilterChain chain = inv.getArgument(2);
            chain.doFilter(rq, rs);
            return null;
        }).when(loginRateLimitFilter).doFilter(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void healthReturnsOkWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void healthDoesNotRequireAuth() throws Exception {
        // Sin sesión ni token debe dar 200, no 401/302
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }
}
