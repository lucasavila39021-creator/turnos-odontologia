-- V2__usuario.sql — C-02 core-models-usuario (CRITICO)
-- Forward-only, inmutable tras deploy. Tabla usuario con unicidad, ENUM rol, activo default, timestamps.

CREATE TABLE usuario (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(120) NULL,
    rol ENUM('PACIENTE','ODONTOLOGO','SECRETARIA','ADMIN') NOT NULL,
    activo TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_usuario_username (username),
    UNIQUE KEY uk_usuario_email (email),
    INDEX idx_usuario_rol (rol)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
