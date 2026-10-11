package com.clinica.turnos.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Ficha de paciente — tabla paciente (V3__paciente.sql, C-05).
 * DNI único (RN-PA-03), link opcional a Usuario vía usuario_id (IN-01).
 * Nunca expone datos sensibles en toString (solo id + timestamps).
 */
@Entity
@Table(name = "paciente", uniqueConstraints = {
        @UniqueConstraint(name = "uk_paciente_dni", columnNames = "dni"),
        @UniqueConstraint(name = "uk_paciente_usuario", columnNames = "usuario_id")
})
public class Paciente extends BaseEntity {

    @NotBlank
    @Size(max = 100)
    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @NotBlank
    @Size(max = 100)
    @Column(name = "apellido", nullable = false, length = 100)
    private String apellido;

    @NotBlank
    @Size(max = 20)
    @Column(name = "dni", nullable = false, length = 20, unique = true)
    private String dni;

    @Size(max = 30)
    @Column(name = "telefono", length = 30)
    private String telefono;

    @Email
    @Size(max = 120)
    @Column(name = "email", length = 120)
    private String email;

    @Past
    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;

    @Column(name = "usuario_id", unique = true)
    private Long usuarioId;

    protected Paciente() {
        // JPA requires protected no-args constructor
        super();
    }

    public Paciente(String nombre, String apellido, String dni, String telefono,
                    String email, LocalDate fechaNacimiento, Long usuarioId) {
        this.nombre = nombre;
        this.apellido = apellido;
        this.dni = dni;
        this.telefono = telefono;
        this.email = email;
        this.fechaNacimiento = fechaNacimiento;
        this.usuarioId = usuarioId;
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

    public String getDni() {
        return dni;
    }

    public void setDni(String dni) {
        this.dni = dni;
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

    public LocalDate getFechaNacimiento() {
        return fechaNacimiento;
    }

    public void setFechaNacimiento(LocalDate fechaNacimiento) {
        this.fechaNacimiento = fechaNacimiento;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(Long usuarioId) {
        this.usuarioId = usuarioId;
    }

    @Override
    public String toString() {
        return "Paciente{" +
                "id=" + getId() +
                ", createdAt=" + getCreatedAt() +
                ", updatedAt=" + getUpdatedAt() +
                '}';
    }
}
