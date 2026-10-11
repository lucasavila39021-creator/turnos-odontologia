package com.clinica.turnos.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RolTest {

    @Test
    void valueOfAdminNoFalla() {
        assertThat(Rol.valueOf("ADMIN")).isEqualTo(Rol.ADMIN);
        assertThat(Rol.valueOf("PACIENTE")).isEqualTo(Rol.PACIENTE);
        assertThat(Rol.valueOf("ODONTOLOGO")).isEqualTo(Rol.ODONTOLOGO);
        assertThat(Rol.valueOf("SECRETARIA")).isEqualTo(Rol.SECRETARIA);
    }

    @Test
    void valueOfInvalidLanzaIllegalArgumentException() {
        assertThatThrownBy(() -> Rol.valueOf("INVALID"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enumTieneCuatroValores() {
        assertThat(Rol.values()).hasSize(4);
    }
}
