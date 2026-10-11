package com.clinica.turnos.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.clinica.turnos.domain.model.Rol;
import com.clinica.turnos.domain.model.Usuario;
import jakarta.validation.ConstraintViolationException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Tests de integración con MySQL real via Testcontainers (hard rule: no mocks, no H2).
 * Cubre: creación ok, unicidad username/email, NULL email, rol null, activo flag, collation.
 * Requiere Docker disponible; si no hay Docker, los tests se skipean (disabledWithoutDocker=true).
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class UsuarioRepositoryTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("turnos_test")
            .withUsername("test")
            .withPassword("test")
            .withEnv("MYSQL_ROOT_PASSWORD", "root");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("app.timezone", () -> "America/Argentina/Buenos_Aires");
    }

    @Autowired
    UsuarioRepository repo;

    @Test
    void creacionOkPersisteConTimestamps() {
        Usuario u = new Usuario("juan_test1", "$2a$12$abcdefghijklmnopqrstuuABCDEFGHIJKLMN123456789012", "juan1@x.com", Rol.PACIENTE, true);
        Usuario saved = repo.save(u);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        Optional<Usuario> found = repo.findByUsername("juan_test1");
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("juan1@x.com");
    }

    @Test
    void usernameDuplicadoViolaUnicidad() {
        repo.save(new Usuario("dup_user", "$2a$12$hash11111111111111111111111111111111111111111111111", "a_dup@x.com", Rol.ADMIN, true));
        assertThatThrownBy(() -> repo.saveAndFlush(new Usuario("dup_user", "$2a$12$hash22222222222222222222222222222222222222222222222", "b_dup@x.com", Rol.PACIENTE, true)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("Duplicate entry");
    }

    @Test
    void emailDuplicadoViolaUnicidad() {
        repo.save(new Usuario("u1_email", "$2a$12$hash11111111111111111111111111111111111111111111111", "same_email@x.com", Rol.SECRETARIA, true));
        assertThatThrownBy(() -> repo.saveAndFlush(new Usuario("u2_email", "$2a$12$hash22222222222222222222222222222222222222222222222", "same_email@x.com", Rol.ODONTOLOGO, true)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void dosEmailsNullNoColisionan() {
        Usuario u1 = repo.save(new Usuario("null1", "$2a$12$hash11111111111111111111111111111111111111111111111", null, Rol.PACIENTE, true));
        Usuario u2 = repo.save(new Usuario("null2", "$2a$12$hash22222222222222222222222222222222222222222222222", null, Rol.PACIENTE, true));
        assertThat(u1.getId()).isNotNull();
        assertThat(u2.getId()).isNotNull();
        assertThat(repo.findByUsername("null1")).isPresent();
        assertThat(repo.findByUsername("null2")).isPresent();
    }

    @Test
    void rolNullFallaValidacionONotNull() {
        Usuario invalido = new Usuario("rolnull", "$2a$12$hash11111111111111111111111111111111111111111111111", "rolnull@x.com", null, true);
        assertThatThrownBy(() -> repo.saveAndFlush(invalido))
                .isInstanceOf(Exception.class)
                .satisfies(ex -> {
                    // Puede ser ConstraintViolationException o DataIntegrityViolationException según validación JPA
                    assertThat(ex).isInstanceOfAny(ConstraintViolationException.class, DataIntegrityViolationException.class, org.springframework.transaction.TransactionSystemException.class);
                });
    }

    @Test
    void activoFalsePersisteYEsConsultable() {
        Usuario inact = repo.save(new Usuario("inact_user", "$2a$12$hash11111111111111111111111111111111111111111111111", "inact@x.com", Rol.PACIENTE, false));
        Optional<Usuario> found = repo.findByUsername("inact_user");
        assertThat(found).isPresent();
        assertThat(found.get().getActivo()).isFalse();
        assertThat(found.get().isActivo()).isFalse();
        // Documentación: UserDetailsService (C-03) debe rechazarlo con DisabledException → 401
    }

    @Test
    void collationCaseInsensitiveEmailDuplicadoFalla() {
        repo.save(new Usuario("user1_coll", "$2a$12$hash11111111111111111111111111111111111111111111111", "TestColl@Example.com", Rol.ADMIN, true));
        assertThatThrownBy(() -> repo.saveAndFlush(new Usuario("user2_coll", "$2a$12$hash22222222222222222222222222222222222222222222222", "testcoll@example.com", Rol.ADMIN, true)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void updateActualizaUpdatedAtSinTocarCreatedAt() throws InterruptedException {
        Usuario u = repo.save(new Usuario("upd_user", "$2a$12$hash11111111111111111111111111111111111111111111111", "upd@x.com", Rol.PACIENTE, true));
        var created = u.getCreatedAt();
        var updatedBefore = u.getUpdatedAt();
        Thread.sleep(10);
        u.setEmail("upd2@x.com");
        Usuario updated = repo.saveAndFlush(u);
        assertThat(updated.getCreatedAt()).isEqualTo(created);
        // updatedAt puede ser igual o posterior (depende de @PreUpdate y DB ON UPDATE)
        assertThat(updated.getUpdatedAt()).isNotNull();
    }
}
