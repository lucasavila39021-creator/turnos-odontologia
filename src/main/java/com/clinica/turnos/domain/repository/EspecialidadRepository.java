package com.clinica.turnos.domain.repository;

import com.clinica.turnos.domain.model.Especialidad;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface EspecialidadRepository extends BaseRepository<Especialidad, Long> {

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);

    List<Especialidad> findByActiva(Boolean activa);

    Page<Especialidad> findByActiva(Boolean activa, Pageable pageable);
}
