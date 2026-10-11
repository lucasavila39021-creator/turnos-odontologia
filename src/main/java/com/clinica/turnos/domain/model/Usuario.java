package com.clinica.turnos.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Entidad Usuario — tabla usuario (V2__usuario.sql).
 * No expone passwordHash en toString ni JSON.
 */
@Entity
@Table(name = "usuario", uniqueConstraints = {
        @UniqueConstraint(name = "uk_usuario_username", columnNames = "username"),
        @UniqueConstraint(name = "uk_usuario_email", columnNames = "email")
})
public class Usuario extends BaseEntity {

    @NotBlank
    @Size(max = 50)
    @Column(name = "username", nullable = false, length = 50, unique = true)
    private String username;

    @NotBlank
    @JsonIgnore
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Email
    @Size(max = 120)
    @Column(name = "email", length = 120, unique = true)
    private String email;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "rol", nullable = false, length = 20)
    private Rol rol;

    @NotNull
    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    protected Usuario() {
        // JPA requires protected no-args constructor
        super();
    }

    public Usuario(String username, String passwordHash, String email, Rol rol, Boolean activo) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.email = email;
        this.rol = rol;
        this.activo = activo != null ? activo : true;
    }

    public Usuario(String username, String passwordHash, String email, Rol rol) {
        this(username, passwordHash, email, rol, true);
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Rol getRol() {
        return rol;
    }

    public void setRol(Rol rol) {
        this.rol = rol;
    }

    public Boolean getActivo() {
        return activo;
    }

    public boolean isActivo() {
        return Boolean.TRUE.equals(activo);
    }

    public void setActivo(Boolean activo) {
        this.activo = activo;
    }

    @Override
    public String toString() {
        return "Usuario{" +
                "id=" + getId() +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", rol=" + rol +
                ", activo=" + activo +
                ", createdAt=" + getCreatedAt() +
                ", updatedAt=" + getUpdatedAt() +
                '}';
    }
}
