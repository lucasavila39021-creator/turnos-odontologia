package com.clinica.turnos.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class IdxDbgTest {
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void dumpIndexes() {
        // H2 2.x: INFORMATION_SCHEMA.INDEXES no expone COLUMN_NAME/NON_UNIQUE
        // (eso era H2 1.x); el detalle por columna vive en INDEX_COLUMNS.
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT INDEX_NAME, TABLE_NAME, INDEX_TYPE_NAME FROM INFORMATION_SCHEMA.INDEXES "
                        + "WHERE TABLE_NAME = 'ESPECIALIDAD'");
        System.out.println("IDXDBG-ROWS=" + rows);
        assertThat(rows).isNotEmpty();
        List<Map<String, Object>> cols = jdbcTemplate.queryForList(
                "SELECT INDEX_NAME, COLUMN_NAME FROM INFORMATION_SCHEMA.INDEX_COLUMNS "
                        + "WHERE TABLE_NAME = 'ESPECIALIDAD' ORDER BY INDEX_NAME, ORDINAL_POSITION");
        System.out.println("IDXDBG-COLS=" + cols);
        assertThat(cols).isNotEmpty();
    }
}
