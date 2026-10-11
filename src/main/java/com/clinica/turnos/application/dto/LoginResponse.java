package com.clinica.turnos.application.dto;

import com.clinica.turnos.domain.model.Rol;
import com.clinica.turnos.domain.model.Usuario;
import com.clinica.turnos.security.CustomUserDetails;

/**
 * DTO login response — never exposes passwordHash.
 */
public record LoginResponse(
        Long id,
        String username,
        String email,
        Rol rol
) {
    public static LoginResponse from(Usuario u) {
        return new LoginResponse(u.getId(), u.getUsername(), u.getEmail(), u.getRol());
    }

    public static LoginResponse from(CustomUserDetails cud) {
        return new LoginResponse(cud.getId(), cud.getUsername(), cud.getEmail(), cud.getRol());
    }
}
