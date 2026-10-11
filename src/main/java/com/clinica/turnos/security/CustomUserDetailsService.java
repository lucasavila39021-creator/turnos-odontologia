package com.clinica.turnos.security;

import com.clinica.turnos.domain.model.Usuario;
import com.clinica.turnos.domain.repository.UsuarioRepository;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads Usuario case-insensitive — C-03 auth D-03.
 * Throws DisabledException if !activo (mapped to 401 generic).
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public CustomUserDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalized = username == null ? "" : username.trim();
        Usuario usuario = usuarioRepository.findByUsernameIgnoreCase(normalized)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + normalized));
        if (!usuario.isActivo()) {
            throw new DisabledException("Usuario inactivo: " + normalized);
        }
        return new CustomUserDetails(
                usuario.getId(),
                usuario.getUsername(),
                usuario.getPasswordHash(),
                usuario.getEmail(),
                usuario.getRol(),
                usuario.isActivo()
        );
    }
}
