package com.clinica.turnos.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class UsuarioValidationTest {

    static Validator validator;

    @BeforeAll
    static void setup() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void usuarioValidoNoTieneViolaciones() {
        Usuario u = new Usuario("ana", "$2a$12$hashhashhashhashhashhashhashhashhashhashhash12", "ana@clinica.local", Rol.PACIENTE, true);
        var violations = validator.validate(u);
        assertThat(violations).isEmpty();
    }

    @Test
    void rolNullFallaValidacion() {
        Usuario u = new Usuario("ana", "$2a$12$hash", "ana@clinica.local", null, true);
        var violations = validator.validate(u);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("rol"));
    }

    @Test
    void usernameBlankFalla() {
        Usuario u = new Usuario("", "$2a$12$hash", "a@x.com", Rol.ADMIN, true);
        var violations = validator.validate(u);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("username"));
    }

    @Test
    void usernameMayor50Falla() {
        Usuario u = new Usuario("a".repeat(51), "$2a$12$hash", "a@x.com", Rol.ADMIN, true);
        var violations = validator.validate(u);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("username"));
    }

    @Test
    void emailInvalidoFalla() {
        Usuario u = new Usuario("ana", "$2a$12$hash", "bad-email", Rol.PACIENTE, true);
        var violations = validator.validate(u);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    @Test
    void emailNullNoFalla() {
        Usuario u = new Usuario("ana", "$2a$12$hash", null, Rol.PACIENTE, true);
        var violations = validator.validate(u);
        assertThat(violations).noneMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    @Test
    void usuarioNoExponePasswordHashEnToString() {
        Usuario u = new Usuario("ana", "$2a$12$hashSecreto", "ana@x.com", Rol.PACIENTE, true);
        String str = u.toString();
        assertThat(str).doesNotContain("$2a$12$hashSecreto");
        assertThat(str).doesNotContain("password");
    }

    @Test
    void usuarioConstructorProtectedParaJpa() throws Exception {
        var ctor = Usuario.class.getDeclaredConstructor();
        assertThat(ctor.getModifiers()).isEqualTo(java.lang.reflect.Modifier.PROTECTED);
    }

    @Test
    void usuarioNoUsaAutowired() {
        boolean hasAutowired = java.util.Arrays.stream(Usuario.class.getDeclaredFields())
                .anyMatch(f -> f.isAnnotationPresent(org.springframework.beans.factory.annotation.Autowired.class));
        assertThat(hasAutowired).isFalse();
        boolean classAutowired = Usuario.class.isAnnotationPresent(org.springframework.beans.factory.annotation.Autowired.class);
        assertThat(classAutowired).isFalse();
    }
}
