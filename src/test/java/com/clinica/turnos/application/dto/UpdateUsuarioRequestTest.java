package com.clinica.turnos.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.clinica.turnos.domain.model.Rol;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class UpdateUsuarioRequestTest {

    static Validator validator;

    @BeforeAll
    static void setup() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void bodyVacioFalla() {
        var req = new UpdateUsuarioRequest(null, null, null);
        var violations = validator.validate(req);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("alMenosUnCampo"));
    }

    @Test
    void soloRolPasa() {
        var req = new UpdateUsuarioRequest(null, Rol.SECRETARIA, null);
        assertThat(validator.validate(req)).isEmpty();
    }

    @Test
    void soloActivoPasa() {
        var req = new UpdateUsuarioRequest(null, null, false);
        assertThat(validator.validate(req)).isEmpty();
    }

    @Test
    void emailInvalidoFalla() {
        var req = new UpdateUsuarioRequest("no-es-email", null, null);
        var violations = validator.validate(req);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    @Test
    void emailValidoPasa() {
        var req = new UpdateUsuarioRequest("nuevo@clinica.local", null, null);
        assertThat(validator.validate(req)).isEmpty();
    }
}
