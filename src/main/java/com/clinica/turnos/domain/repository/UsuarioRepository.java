package com.clinica.turnos.domain.repository;

import com.clinica.turnos.domain.model.Rol;
import com.clinica.turnos.domain.model.Usuario;
import java.util.Optional;

public interface UsuarioRepository extends BaseRepository<Usuario, Long> {

    Optional<Usuario> findByUsername(String username);

    Optional<Usuario> findByUsernameIgnoreCase(String username);

    Optional<Usuario> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    long countByRolAndActivoTrue(Rol rol);
}
