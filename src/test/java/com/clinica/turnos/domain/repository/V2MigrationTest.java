package com.clinica.turnos.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class V2MigrationTest {

    @Test
    void v2ContieneUtf8mb4EnumUniqueIndex() throws Exception {
        Path p = Path.of("src/main/resources/db/migration/V2__usuario.sql");
        assertThat(Files.exists(p)).isTrue();
        String sql = Files.readString(p);
        assertThat(sql).contains("utf8mb4");
        assertThat(sql).contains("utf8mb4_0900_ai_ci");
        assertThat(sql).contains("ENUM('PACIENTE','ODONTOLOGO','SECRETARIA','ADMIN')");
        assertThat(sql).contains("uk_usuario_username");
        assertThat(sql).contains("uk_usuario_email");
        assertThat(sql).contains("idx_usuario_rol");
        assertThat(sql).contains("ENGINE=InnoDB");
        assertThat(sql).contains("BIGINT UNSIGNED AUTO_INCREMENT");
        // No NOT NULL sin DEFAULT en tabla nueva: todas las NOT NULL tienen default o son PK
        // Verifica que V2 no contiene admin123 (seed no en migración prod)
        assertThat(sql).doesNotContain("admin123");
        assertThat(sql).doesNotContain("$2a$");
    }

    @Test
    void v1NoEditada() throws Exception {
        Path v1 = Path.of("src/main/resources/db/migration/V1__init.sql");
        String sql = Files.readString(v1);
        // V1 debe seguir siendo bootstrap vacío, no debe contener usuario table
        assertThat(sql).doesNotContain("CREATE TABLE usuario");
    }
}
