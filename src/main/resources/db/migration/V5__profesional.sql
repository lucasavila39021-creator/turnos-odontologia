-- V5__profesional.sql — C-07 profesionales-abm
-- Forward-only, inmutable tras deploy. Tablas profesional + profesional_especialidad (N:M explícita).
-- Tablas nuevas sin datos: columnas NOT NULL sin DEFAULT son seguras (no hay backfill).
-- Rollback = no-op (forward-only; en dev DROP TABLE profesional_especialidad, profesional).

CREATE TABLE profesional (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    apellido VARCHAR(100) NOT NULL,
    matricula VARCHAR(30) NULL,
    telefono VARCHAR(30) NULL,
    email VARCHAR(120) NULL,
    usuario_id BIGINT UNSIGNED NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_profesional_matricula UNIQUE KEY (matricula),
    CONSTRAINT uk_profesional_usuario UNIQUE KEY (usuario_id),
    CONSTRAINT fk_profesional_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuario (id) ON DELETE SET NULL ON UPDATE CASCADE,
    INDEX idx_profesional_apellido (apellido, nombre),
    INDEX idx_profesional_activo (activo),
    CONSTRAINT chk_profesional_nombre_no_vacio CHECK (CHAR_LENGTH(TRIM(nombre)) > 0),
    CONSTRAINT chk_profesional_apellido_no_vacio CHECK (CHAR_LENGTH(TRIM(apellido)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE profesional_especialidad (
    profesional_id BIGINT UNSIGNED NOT NULL,
    especialidad_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (profesional_id, especialidad_id),
    CONSTRAINT fk_pe_profesional FOREIGN KEY (profesional_id)
        REFERENCES profesional (id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_pe_especialidad FOREIGN KEY (especialidad_id)
        REFERENCES especialidad (id) ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_pe_especialidad (especialidad_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
