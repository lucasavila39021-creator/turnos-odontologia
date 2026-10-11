package com.clinica.turnos.security;

import com.clinica.turnos.domain.model.Rol;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * UserDetails backed by Usuario entity — C-03 auth.
 * Never exposes passwordHash in toString.
 */
public class CustomUserDetails implements UserDetails {

    private final Long id;
    private final String username;
    private final String passwordHash;
    private final String email;
    private final Rol rol;
    private final boolean activo;

    public CustomUserDetails(Long id, String username, String passwordHash, String email, Rol rol, boolean activo) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.email = email;
        this.rol = rol;
        this.activo = activo;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public Rol getRol() {
        return rol;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + rol.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return activo;
    }

    @Override
    public String toString() {
        return "CustomUserDetails{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", rol=" + rol +
                ", activo=" + activo +
                '}';
    }
}
