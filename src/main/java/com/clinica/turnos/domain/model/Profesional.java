package com.clinica.turnos.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Ficha de profesional — tabla profesional (V5__profesional.sql, C-07).
 * Matrícula única opcional (RN-PR-02), link opcional a Usuario ODONTOLOGO,
 * flag activo con guard RN-PR-04 (ver ProfesionalService).
 * N:M con especialidades vía {@link ProfesionalEspecialidad} (intermedia explícita).
 * Nunca expone datos sensibles en toString (solo id + activo + timestamps).
 */
@Entity
@Table(name = "profesional", uniqueConstraints = {
        @UniqueConstraint(name = "uk_profesional_matricula", columnNames = "matricula"),
        @UniqueConstraint(name = "uk_profesional_usuario", columnNames = "usuario_id")
})
public class Profesional extends BaseEntity {

    @NotBlank
    @Size(max = 100)
    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @NotBlank
    @Size(max = 100)
    @Column(name = "apellido", nullable = false, length = 100)
    private String apellido;

    @Size(max = 30)
    @Column(name = "matricula", length = 30, unique = true)
    private String matricula;

    @Size(max = 30)
    @Column(name = "telefono", length = 30)
    private String telefono;

    @Email
    @Size(max = 120)
    @Column(name = "email", length = 120)
    private String email;

    @Column(name = "usuario_id", unique = true)
    private Long usuarioId;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    protected Profesional() {
        // JPA requires protected no-args constructor
        super();
    }

    public Profesional(String nombre, String apellido, String matricula, String telefono,
                       String email, Long usuarioId, Boolean activo) {
        this.nombre = nombre;
        this.apellido = apellido;
        this.matricula = matricula;
        this.telefono = telefono;
        this.email = email;
        this.usuarioId = usuarioId;
        this.activo = activo != null ? activo : true;
    }

    public Profesional(String nombre, String apellido) {
        this(nombre, apellido, null, null, null, null, true);
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getApellido() {
        return apellido;
    }

    public void setApellido(String apellido) {
        this.apellido = apellido;
    }

    public String getMatricula() {
        return matricula;
    }

    public void setMatricula(String matricula) {
        this.matricula = matricula;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(Long usuarioId) {
        this.usuarioId = usuarioId;
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
        return "Profesional{" +
                "id=" + getId() +
                ", activo=" + activo +
                ", createdAt=" + getCreatedAt() +
                ", updatedAt=" + getUpdatedAt() +
                '}';
    }
}
