package com.clinica.turnos.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Catálogo de especialidades odontológicas — tabla especialidad (V4__especialidad.sql, C-06).
 * Nombre único normalizado (trim + case-insensitive, RN-PR-03), flag activa con regla
 * de no-desactivar (ver EspecialidadService + EspecialidadUsagePort).
 * toString no vuelca descripcion completa (solo id + nombre + activa + timestamps).
 */
@Entity
@Table(name = "especialidad", uniqueConstraints = {
        @UniqueConstraint(name = "uk_especialidad_nombre", columnNames = "nombre")
}, indexes = {
        @Index(name = "idx_especialidad_activa", columnList = "activa")
})
public class Especialidad extends BaseEntity {

    @NotBlank
    @Size(max = 80)
    @Column(name = "nombre", nullable = false, length = 80, unique = true)
    private String nombre;

    @Size(max = 255)
    @Column(name = "descripcion", length = 255)
    private String descripcion;

    @Column(name = "activa", nullable = false)
    private Boolean activa = true;

    protected Especialidad() {
        // JPA requires protected no-args constructor
        super();
    }

    public Especialidad(String nombre, String descripcion, Boolean activa) {
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.activa = activa != null ? activa : true;
    }

    public Especialidad(String nombre, String descripcion) {
        this(nombre, descripcion, true);
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public Boolean getActiva() {
        return activa;
    }

    public boolean isActiva() {
        return Boolean.TRUE.equals(activa);
    }

    public void setActiva(Boolean activa) {
        this.activa = activa;
    }

    @Override
    public String toString() {
        return "Especialidad{" +
                "id=" + getId() +
                ", nombre='" + nombre + '\'' +
                ", activa=" + activa +
                ", createdAt=" + getCreatedAt() +
                ", updatedAt=" + getUpdatedAt() +
                '}';
    }
}
