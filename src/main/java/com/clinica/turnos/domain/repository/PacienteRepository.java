package com.clinica.turnos.domain.repository;

import com.clinica.turnos.domain.model.Paciente;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PacienteRepository extends BaseRepository<Paciente, Long> {

    Optional<Paciente> findByDni(String dni);

    Optional<Paciente> findByUsuarioId(Long usuarioId);

    boolean existsByDni(String dni);

    boolean existsByUsuarioId(Long usuarioId);

    Page<Paciente> findByApellidoStartingWithIgnoreCase(String apellido, Pageable pageable);

    Page<Paciente> findByDniAndApellidoStartingWithIgnoreCase(String dni, String apellido, Pageable pageable);
}
