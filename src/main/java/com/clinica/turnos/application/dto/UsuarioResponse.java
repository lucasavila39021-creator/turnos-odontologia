package com.clinica.turnos.application.dto;

import com.clinica.turnos.domain.model.Rol;
import com.clinica.turnos.domain.model.Usuario;
import java.time.LocalDateTime;

/**
 * DTO de respuesta — nunca expone passwordHash.
 */
public record UsuarioResponse(
        Long id,
        String username,
        String email,
        Rol rol,
        Boolean activo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static UsuarioResponse from(Usuario u) {
        return new UsuarioResponse(
                u.getId(),
                u.getUsername(),
                u.getEmail(),
                u.getRol(),
                u.getActivo(),
                u.getCreatedAt(),
                u.getUpdatedAt()
        );
    }
}
