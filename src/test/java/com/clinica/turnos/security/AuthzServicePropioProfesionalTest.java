package com.clinica.turnos.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.clinica.turnos.domain.model.Profesional;
import com.clinica.turnos.domain.model.Rol;
import com.clinica.turnos.domain.repository.ProfesionalEspecialidadRepository;
import com.clinica.turnos.domain.repository.ProfesionalRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * C-09 task 1.2 (RED→GREEN): AuthzService.isPropioProfesional espejo de
 * isPropioPaciente vía profesionalRepository.findByUsuarioId.
 * Ficha ajena o inexistente → false.
 */
@SpringBootTest
class AuthzServicePropioProfesionalTest {

    @Autowired AuthzService authzService;
    @Autowired ProfesionalRepository profesionalRepository;
    @Autowired ProfesionalEspecialidadRepository vinculoRepository;

    private Long fichaId;
    private final Long otroUsuarioId = 987654L;

    private void comoUsuario(Long uid, Rol rol) {
        CustomUserDetails cud = new CustomUserDetails(
                uid, "u" + uid, "hash", "u" + uid + "@x.local", rol, true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        cud, null, List.of(() -> "ROLE_" + rol.name())));
    }

    @BeforeEach
    void setUp() {
        vinculoRepository.deleteAll();
        profesionalRepository.deleteAll();
        fichaId = profesionalRepository.save(
                new Profesional("Ana", "Gómez", "M-900", null, null, 42L, true)).getId();
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void fichaPropiaTrue() {
        comoUsuario(42L, Rol.ODONTOLOGO);
        assertThat(authzService.isPropioProfesional(fichaId)).isTrue();
    }

    @Test
    void fichaAjenaFalse() {
        comoUsuario(otroUsuarioId, Rol.ODONTOLOGO);
        assertThat(authzService.isPropioProfesional(fichaId)).isFalse();
    }

    @Test
    void fichaInexistenteFalse() {
        comoUsuario(42L, Rol.ODONTOLOGO);
        assertThat(authzService.isPropioProfesional(999999L)).isFalse();
    }

    @Test
    void sinAutenticacionFalse() {
        SecurityContextHolder.clearContext();
        assertThat(authzService.isPropioProfesional(fichaId)).isFalse();
    }
}
