-- V4__especialidad.sql — C-06 especialidades-abm
-- Forward-only, inmutable tras deploy. Tabla especialidad con nombre único case-insensitive, flag activa, timestamps.
-- Tabla nueva sin datos: columnas NOT NULL sin DEFAULT son seguras (no hay backfill).
-- Rollback = no-op (forward-only; en dev DROP TABLE especialidad).

CREATE TABLE especialidad (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(80) NOT NULL,
    descripcion VARCHAR(255) NULL,
    activa BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_especialidad_nombre UNIQUE KEY (nombre),
    INDEX idx_especialidad_activa (activa),
    CONSTRAINT chk_especialidad_nombre_no_vacio CHECK (CHAR_LENGTH(TRIM(nombre)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
