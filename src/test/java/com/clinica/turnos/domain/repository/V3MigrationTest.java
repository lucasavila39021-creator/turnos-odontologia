package com.clinica.turnos.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class V3MigrationTest {

    @Test
    void v3ContieneTablaPacienteConConstraints() throws Exception {
        Path p = Path.of("src/main/resources/db/migration/V3__paciente.sql");
        assertThat(Files.exists(p)).isTrue();
        String sql = Files.readString(p);
        assertThat(sql).contains("CREATE TABLE paciente");
        assertThat(sql).contains("utf8mb4_0900_ai_ci");
        assertThat(sql).contains("uk_paciente_dni");
        assertThat(sql).contains("uk_paciente_usuario");
        assertThat(sql).contains("idx_paciente_apellido");
        assertThat(sql).contains("fk_paciente_usuario");
        assertThat(sql).contains("ON DELETE SET NULL");
        assertThat(sql).contains("ENGINE=InnoDB");
        assertThat(sql).contains("BIGINT UNSIGNED AUTO_INCREMENT");
    }

    @Test
    void v1V2NoEditadas() throws Exception {
        assertThat(Files.readString(Path.of("src/main/resources/db/migration/V1__init.sql")))
                .doesNotContain("CREATE TABLE paciente");
        assertThat(Files.readString(Path.of("src/main/resources/db/migration/V2__usuario.sql")))
                .doesNotContain("CREATE TABLE paciente");
    }
}
