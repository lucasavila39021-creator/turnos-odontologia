package com.clinica.turnos.domain.repository;

import com.clinica.turnos.domain.model.Profesional;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProfesionalRepository extends BaseRepository<Profesional, Long> {

    boolean existsByMatricula(String matricula);

    Optional<Profesional> findByMatricula(String matricula);

    Optional<Profesional> findByUsuarioId(Long usuarioId);

    boolean existsByUsuarioId(Long usuarioId);

    Page<Profesional> findByActivo(Boolean activo, Pageable pageable);

    /**
     * Lock de fila del profesional para serializar reservas concurrentes del
     * mismo hueco (C-08 D-02): el segundo creador espera, re-lee el solape y
     * recibe 409. Complementa la TX SERIALIZABLE (gap-locks en MySQL).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Profesional p WHERE p.id = :id")
    Optional<Profesional> findByIdForUpdate(@Param("id") Long id);

    /**
     * Lista con filtros combinables AND (C-07): especialidad vía EXISTS sobre
     * la intermedia, activo directo. Orden lo aporta el Pageable.
     */
    @Query("SELECT p FROM Profesional p WHERE (:activo IS NULL OR p.activo = :activo) "
            + "AND (:espId IS NULL OR EXISTS (SELECT 1 FROM ProfesionalEspecialidad pe "
            + "WHERE pe.profesional = p AND pe.especialidad.id = :espId))")
    Page<Profesional> findByFiltros(@Param("espId") Long especialidadId,
                                    @Param("activo") Boolean activo,
                                    Pageable pageable);

    /**
     * Profesionales vinculados a una especialidad (C-07 handoff 3.2).
     */
    @Query("SELECT p FROM Profesional p WHERE EXISTS (SELECT 1 FROM ProfesionalEspecialidad pe "
            + "WHERE pe.profesional = p AND pe.especialidad.id = :espId)")
    Page<Profesional> findByEspecialidadId(@Param("espId") Long especialidadId, Pageable pageable);
}
