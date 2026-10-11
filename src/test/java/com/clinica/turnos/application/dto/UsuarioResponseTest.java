package com.clinica.turnos.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.clinica.turnos.domain.model.Rol;
import com.clinica.turnos.domain.model.Usuario;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class UsuarioResponseTest {

    @Test
    void fromNoExponePasswordHash() throws Exception {
        Usuario u = new Usuario("ana", "$2a$12$hashSecretoQueNoDebeExponerse123456789012", "ana@x.com", Rol.PACIENTE, true);
        u.setId(1L);
        // Simulate PrePersist
        try {
            var m = com.clinica.turnos.domain.model.BaseEntity.class.getDeclaredMethod("onCreate");
            m.setAccessible(true);
            m.invoke(u);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        UsuarioResponse resp = UsuarioResponse.from(u);
        assertThat(resp.id()).isEqualTo(1L);
        assertThat(resp.username()).isEqualTo("ana");
        assertThat(resp.email()).isEqualTo("ana@x.com");
        assertThat(resp.rol()).isEqualTo(Rol.PACIENTE);
        assertThat(resp.activo()).isTrue();
        assertThat(resp.createdAt()).isNotNull();
        // JSON serialization no debe contener password_hash
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        String json = mapper.writeValueAsString(resp);
        assertThat(json).doesNotContain("password");
        assertThat(json).doesNotContain("$2a$12$");
        assertThat(json).contains("ana");
        assertThat(json).contains("PACIENTE");
    }

    @Test
    void fromMapeaTodosLosCampos() {
        Usuario u = new Usuario("test", "$2a$12$hash", null, Rol.ADMIN, false);
        u.setId(42L);
        try {
            var m = com.clinica.turnos.domain.model.BaseEntity.class.getDeclaredMethod("onCreate");
            m.setAccessible(true);
            m.invoke(u);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        UsuarioResponse r = UsuarioResponse.from(u);
        assertThat(r.id()).isEqualTo(42L);
        assertThat(r.activo()).isFalse();
        assertThat(r.email()).isNull();
    }
}
