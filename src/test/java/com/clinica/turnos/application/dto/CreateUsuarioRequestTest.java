package com.clinica.turnos.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.clinica.turnos.domain.model.Rol;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CreateUsuarioRequestTest {

    static Validator validator;

    @BeforeAll
    static void setup() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void passwordShortFalla() {
        var req = new CreateUsuarioRequest("user", "short", "a@x.com", Rol.PACIENTE);
        var violations = validator.validate(req);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @Test
    void passwordMin8Ok() {
        var req = new CreateUsuarioRequest("user", "longenough", "a@x.com", Rol.PACIENTE);
        var violations = validator.validate(req);
        assertThat(violations).noneMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @Test
    void rolNullFalla() {
        var req = new CreateUsuarioRequest("user", "longenough", "a@x.com", null);
        var violations = validator.validate(req);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("rol"));
    }

    @Test
    void emailInvalidoFalla() {
        var req = new CreateUsuarioRequest("user", "longenough", "bad-email", Rol.ADMIN);
        var violations = validator.validate(req);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    @Test
    void emailNullOk() {
        var req = new CreateUsuarioRequest("user", "longenough", null, Rol.ADMIN);
        var violations = validator.validate(req);
        assertThat(violations).noneMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    @Test
    void usernameBlankFalla() {
        var req = new CreateUsuarioRequest("", "longenough", "a@x.com", Rol.ADMIN);
        var violations = validator.validate(req);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("username"));
    }

    @Test
    void activoNullDefaultTrue() {
        var req = new CreateUsuarioRequest("user", "longenough", "a@x.com", Rol.PACIENTE, null);
        assertThat(req.activo()).isTrue();
    }

    @Test
    void usernameConCaracteresInvalidosFalla() {
        var req = new CreateUsuarioRequest("mal usuario!", "longenough", "a@x.com", Rol.PACIENTE);
        var violations = validator.validate(req);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("username"));
    }

    @Test
    void usernameCortoFalla() {
        var req = new CreateUsuarioRequest("ab", "longenough", "a@x.com", Rol.PACIENTE);
        var violations = validator.validate(req);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("username"));
    }
}
