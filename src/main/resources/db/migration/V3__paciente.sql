-- V3__paciente.sql — C-05 pacientes-abm
-- Forward-only, inmutable tras deploy. Tabla paciente con DNI único, FK opcional a usuario (IN-01) e índice de búsqueda por apellido.
-- Tabla nueva sin datos: columnas NOT NULL sin DEFAULT son seguras (no hay backfill).

CREATE TABLE paciente (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    apellido VARCHAR(100) NOT NULL,
    dni VARCHAR(20) NOT NULL,
    telefono VARCHAR(30) NULL,
    email VARCHAR(120) NULL,
    fecha_nacimiento DATE NULL,
    usuario_id BIGINT UNSIGNED NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_paciente_dni UNIQUE KEY (dni),
    CONSTRAINT uk_paciente_usuario UNIQUE KEY (usuario_id),
    CONSTRAINT fk_paciente_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuario (id) ON DELETE SET NULL ON UPDATE CASCADE,
    INDEX idx_paciente_apellido (apellido, nombre),
    CONSTRAINT chk_paciente_dni_no_vacio CHECK (CHAR_LENGTH(TRIM(dni)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
