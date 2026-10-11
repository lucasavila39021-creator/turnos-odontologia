package com.clinica.turnos.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * Repositorio base genérico — placeholder para C-05..C-08.
 * No genera bean concreto.
 */
@NoRepositoryBean
public interface BaseRepository<T, ID> extends JpaRepository<T, ID> {
}
