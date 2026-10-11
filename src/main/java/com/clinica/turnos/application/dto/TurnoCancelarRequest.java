package com.clinica.turnos.application.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Size;

/**
 * Cancelar turno (C-10, RN-TU-06/07): motivo opcional ≤255.
 * Body puede omitirse o ir vacío → motivo null.
 */
public record TurnoCancelarRequest(
        @Size(max = 255, message = "motivo hasta 255 caracteres")
        @JsonAlias({"motivo_cancelacion", "motivoCancelacion"}) String motivo
) {
}
