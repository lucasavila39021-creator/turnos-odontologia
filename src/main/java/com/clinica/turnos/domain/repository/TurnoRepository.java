package com.clinica.turnos.domain.repository;

import com.clinica.turnos.domain.model.EstadoTurno;
import com.clinica.turnos.domain.model.Turno;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio de turnos (C-08, RN-TU-01/05, RN-PR-04).
 * Solape por bordes: adyacente (fin == inicio) NO es solape.
 * Solo estado RESERVADO bloquea; CANCELADO/ATENDIDO liberan.
 */
public interface TurnoRepository extends BaseRepository<Turno, Long> {

    /**
     * Solape de profesional ese día: existe RESERVADO con
     * hora_inicio &lt; :fin AND hora_fin &gt; :inicio.
     */
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN TRUE ELSE FALSE END FROM Turno t "
            + "WHERE t.profesionalId = :profesionalId AND t.fecha = :fecha "
            + "AND t.estado = :estado "
            + "AND t.horaInicio < :fin AND t.horaFin > :inicio")
    boolean existsSolapeProfesional(@Param("profesionalId") Long profesionalId,
                                    @Param("fecha") LocalDate fecha,
                                    @Param("inicio") LocalTime inicio,
                                    @Param("fin") LocalTime fin,
                                    @Param("estado") EstadoTurno estado);

    /**
     * Solape de paciente ese día (aunque sea otro profesional).
     */
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN TRUE ELSE FALSE END FROM Turno t "
            + "WHERE t.pacienteId = :pacienteId AND t.fecha = :fecha "
            + "AND t.estado = :estado "
            + "AND t.horaInicio < :fin AND t.horaFin > :inicio")
    boolean existsSolapePaciente(@Param("pacienteId") Long pacienteId,
                                 @Param("fecha") LocalDate fecha,
                                 @Param("inicio") LocalTime inicio,
                                 @Param("fin") LocalTime fin,
                                 @Param("estado") EstadoTurno estado);

    /**
     * Guard RN-PR-04: turnos futuros reservados del profesional
     * (fecha &gt;= hoy). Usa índice (profesional_id, fecha).
     */
    boolean existsByProfesionalIdAndFechaGreaterThanEqualAndEstado(
            Long profesionalId, LocalDate fecha, EstadoTurno estado);

    /**
     * Turnos reservados del profesional ese día (para sugeridos C-08,
     * agenda C-09). Ordenados por hora de inicio.
     */
    List<Turno> findByProfesionalIdAndFechaAndEstadoOrderByHoraInicioAsc(
            Long profesionalId, LocalDate fecha, EstadoTurno estado);

    /**
     * Consultas paginadas de lectura (C-09, solo SELECT sobre índices V6).
     * Todas ordenan por fecha+hora_inicio ASC; el Pageable aporta el OFFSET.
     */
    Page<Turno> findByProfesionalIdAndFechaOrderByFechaAscHoraInicioAsc(
            Long profesionalId, LocalDate fecha, Pageable pageable);

    Page<Turno> findByProfesionalIdAndFechaAndEstadoOrderByFechaAscHoraInicioAsc(
            Long profesionalId, LocalDate fecha, EstadoTurno estado, Pageable pageable);

    Page<Turno> findByPacienteIdAndFechaOrderByFechaAscHoraInicioAsc(
            Long pacienteId, LocalDate fecha, Pageable pageable);

    Page<Turno> findByPacienteIdAndFechaAndEstadoOrderByFechaAscHoraInicioAsc(
            Long pacienteId, LocalDate fecha, EstadoTurno estado, Pageable pageable);

    Page<Turno> findByFechaOrderByFechaAscHoraInicioAsc(
            LocalDate fecha, Pageable pageable);

    Page<Turno> findByFechaAndEstadoOrderByFechaAscHoraInicioAsc(
            LocalDate fecha, EstadoTurno estado, Pageable pageable);

    Page<Turno> findByProfesionalIdAndFechaBetweenOrderByFechaAscHoraInicioAsc(
            Long profesionalId, LocalDate desde, LocalDate hasta, Pageable pageable);

    Page<Turno> findByProfesionalIdAndFechaBetweenAndEstadoOrderByFechaAscHoraInicioAsc(
            Long profesionalId, LocalDate desde, LocalDate hasta,
            EstadoTurno estado, Pageable pageable);

    Page<Turno> findByPacienteIdAndFechaBetweenOrderByFechaAscHoraInicioAsc(
            Long pacienteId, LocalDate desde, LocalDate hasta, Pageable pageable);

    Page<Turno> findByPacienteIdAndFechaBetweenAndEstadoOrderByFechaAscHoraInicioAsc(
            Long pacienteId, LocalDate desde, LocalDate hasta,
            EstadoTurno estado, Pageable pageable);

    Page<Turno> findByFechaBetweenOrderByFechaAscHoraInicioAsc(
            LocalDate desde, LocalDate hasta, Pageable pageable);

    Page<Turno> findByFechaBetweenAndEstadoOrderByFechaAscHoraInicioAsc(
            LocalDate desde, LocalDate hasta, EstadoTurno estado, Pageable pageable);

    /**
     * Historial de la ficha del paciente (C-11, ficha + historial):
     * todos los estados, más recientes primero. Solo lectura.
     */
    List<Turno> findByPacienteIdOrderByFechaDescHoraInicioDesc(Long pacienteId);
}
