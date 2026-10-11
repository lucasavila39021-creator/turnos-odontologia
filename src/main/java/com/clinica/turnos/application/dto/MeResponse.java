package com.clinica.turnos.application.dto;

import com.clinica.turnos.domain.model.Rol;
import com.clinica.turnos.domain.model.Usuario;
import com.clinica.turnos.security.CustomUserDetails;

/**
 * DTO me response — never exposes passwordHash.
 */
public record MeResponse(
        Long id,
        String username,
        String email,
        Rol rol
) {
    public static MeResponse from(Usuario u) {
        return new MeResponse(u.getId(), u.getUsername(), u.getEmail(), u.getRol());
    }

    public static MeResponse from(CustomUserDetails cud) {
        return new MeResponse(cud.getId(), cud.getUsername(), cud.getEmail(), cud.getRol());
    }
}
