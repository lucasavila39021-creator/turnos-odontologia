package com.clinica.turnos.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Turno reservado — tabla turno (V6__turno.sql, C-08).
 * FKs como Long (mismo patrón que Paciente/Profesional.usuarioId): evita
 * LAZY/N+1 en la query de solape y mantiene el servicio dueño de los 404.
 * hora_fin calculada en servicio (RN-TU-02), nunca del cliente.
 * toString no vuelca ids de paciente/profesional (solo id + fecha + estado).
 */
@Entity
@Table(name = "turno", indexes = {
        @Index(name = "idx_turno_prof_fecha",
                columnList = "profesional_id, fecha, hora_inicio"),
        @Index(name = "idx_turno_pac_fecha",
                columnList = "paciente_id, fecha"),
        @Index(name = "idx_turno_fecha_estado",
                columnList = "fecha, estado")
})
public class Turno extends BaseEntity {

    @NotNull
    @Column(name = "paciente_id", nullable = false)
    private Long pacienteId;

    @NotNull
    @Column(name = "profesional_id", nullable = false)
    private Long profesionalId;

    @NotNull
    @Column(name = "especialidad_id", nullable = false)
    private Long especialidadId;

    @NotNull
    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @NotNull
    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @NotNull
    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoTurno estado = EstadoTurno.RESERVADO;

    @Size(max = 255)
    @Column(name = "motivo_cancelacion", length = 255)
    private String motivoCancelacion;

    @Column(name = "created_by")
    private Long createdBy;

    protected Turno() {
        // JPA requires protected no-args constructor
        super();
    }

    public Turno(Long pacienteId, Long profesionalId, Long especialidadId,
                 LocalDate fecha, LocalTime horaInicio, LocalTime horaFin,
                 EstadoTurno estado, Long createdBy) {
        this.pacienteId = pacienteId;
        this.profesionalId = profesionalId;
        this.especialidadId = especialidadId;
        this.fecha = fecha;
        this.horaInicio = horaInicio;
        this.horaFin = horaFin;
        this.estado = estado != null ? estado : EstadoTurno.RESERVADO;
        this.createdBy = createdBy;
    }

    public Turno(Long pacienteId, Long profesionalId, Long especialidadId,
                 LocalDate fecha, LocalTime horaInicio, LocalTime horaFin) {
        this(pacienteId, profesionalId, especialidadId, fecha, horaInicio, horaFin,
                EstadoTurno.RESERVADO, null);
    }

    public Long getPacienteId() {
        return pacienteId;
    }

    public void setPacienteId(Long pacienteId) {
        this.pacienteId = pacienteId;
    }

    public Long getProfesionalId() {
        return profesionalId;
    }

    public void setProfesionalId(Long profesionalId) {
        this.profesionalId = profesionalId;
    }

    public Long getEspecialidadId() {
        return especialidadId;
    }

    public void setEspecialidadId(Long especialidadId) {
        this.especialidadId = especialidadId;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public LocalTime getHoraInicio() {
        return horaInicio;
    }

    public void setHoraInicio(LocalTime horaInicio) {
        this.horaInicio = horaInicio;
    }

    public LocalTime getHoraFin() {
        return horaFin;
    }

    public void setHoraFin(LocalTime horaFin) {
        this.horaFin = horaFin;
    }

    public EstadoTurno getEstado() {
        return estado;
    }

    public void setEstado(EstadoTurno estado) {
        this.estado = estado;
    }

    public String getMotivoCancelacion() {
        return motivoCancelacion;
    }

    public void setMotivoCancelacion(String motivoCancelacion) {
        this.motivoCancelacion = motivoCancelacion;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    @Override
    public String toString() {
        return "Turno{" +
                "id=" + getId() +
                ", fecha=" + fecha +
                ", estado=" + estado +
                ", createdAt=" + getCreatedAt() +
                '}';
    }
}
