package com.clinica.turnos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clinica.turnos.domain.repository.UsuarioRepository;
import com.clinica.turnos.security.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * C-12 task 1.3: perfil prod/test NUNCA ejecuta el seed dev.
 * Sin perfil `dev` no hay bean DevDataInitializer y admin/admin123 → 401.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProdSinSeedTest {

    @Autowired MockMvc mockMvc;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired RateLimitService rateLimitService;
    @Autowired ApplicationContext applicationContext;

    @BeforeEach
    void setUp() {
        rateLimitService.clear();
        usuarioRepository.deleteAll();
    }

    @Test
    void seedEsSoloPerfilDev() {
        Profile profile = AnnotationUtils.findAnnotation(
                DevDataInitializer.class, Profile.class);
        assertThat(profile).isNotNull();
        assertThat(profile.value()).containsExactly("dev");
    }

    @Test
    void sinPerfilDevNoHayBeanSeed() {
        assertThat(applicationContext.getBeansOfType(DevDataInitializer.class))
                .isEmpty();
    }

    @Test
    void adminSeedNoLogueaEnProd() throws Exception {
        assertThat(usuarioRepository.findByUsername("admin")).isEmpty();
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Credenciales inválidas"))
                .andExpect(jsonPath("$.error").value("Credenciales inválidas"))
                .andExpect(jsonPath("$.message").value("Credenciales inválidas"));
    }
}
