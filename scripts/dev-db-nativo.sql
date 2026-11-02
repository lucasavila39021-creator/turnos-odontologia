-- =====================================================================
-- dev-db-nativo.sql — Base de desarrollo SIN Docker (MySQL nativo en Windows)
-- Proyecto: turnos-odontologia · TP Skills + MCP (Metodología I)
--
-- Por qué existe: Docker Desktop no instala en Windows 10 LTSC 1809.
-- Qué hace: crea la base turnos_db, aplica las migraciones V2..V6 del
-- proyecto (copiadas sin cambios desde src/main/resources/db/migration)
-- y carga datos 100 % FICTICIOS para explorar el esquema con el MCP.
--
-- NO crea usuarios ni contraseñas (eso se hace a mano, fuera del repo).
-- Re-ejecutable: borra y recrea turnos_db cada vez. SOLO para desarrollo.
-- =====================================================================

DROP DATABASE IF EXISTS turnos_db;
CREATE DATABASE turnos_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE turnos_db;

-- ---------------------------------------------------------------------
-- Migración V2__usuario.sql (copia literal)
-- ---------------------------------------------------------------------
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

-- ---------------------------------------------------------------------
-- Migración V3__paciente.sql (copia literal)
-- ---------------------------------------------------------------------
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

-- ---------------------------------------------------------------------
-- Migración V4__especialidad.sql (copia literal)
-- ---------------------------------------------------------------------
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

-- ---------------------------------------------------------------------
-- Migración V5__profesional.sql (copia literal)
-- ---------------------------------------------------------------------
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

-- ---------------------------------------------------------------------
-- Migración V6__turno.sql (copia literal)
-- ---------------------------------------------------------------------
-- V6__turno.sql — C-08 turnos-reserva
-- Forward-only, inmutable tras deploy. Tabla turno con FKs NOT NULL, 3 índices,
-- CHECK hora_fin > hora_inicio, ENUM estado default RESERVADO.
-- Tabla nueva sin datos: columnas NOT NULL sin DEFAULT son seguras (no hay backfill).
-- Rollback = no-op (forward-only; en dev DROP TABLE turno).

CREATE TABLE turno (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    paciente_id BIGINT UNSIGNED NOT NULL,
    profesional_id BIGINT UNSIGNED NOT NULL,
    especialidad_id BIGINT UNSIGNED NOT NULL,
    fecha DATE NOT NULL,
    hora_inicio TIME NOT NULL,
    hora_fin TIME NOT NULL,
    estado ENUM('RESERVADO','CANCELADO','ATENDIDO') NOT NULL DEFAULT 'RESERVADO',
    motivo_cancelacion VARCHAR(255) NULL,
    created_by BIGINT UNSIGNED NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_turno_paciente FOREIGN KEY (paciente_id)
        REFERENCES paciente (id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_turno_profesional FOREIGN KEY (profesional_id)
        REFERENCES profesional (id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_turno_especialidad FOREIGN KEY (especialidad_id)
        REFERENCES especialidad (id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_turno_created_by FOREIGN KEY (created_by)
        REFERENCES usuario (id) ON DELETE SET NULL ON UPDATE CASCADE,
    INDEX idx_turno_prof_fecha (profesional_id, fecha, hora_inicio),
    INDEX idx_turno_pac_fecha (paciente_id, fecha),
    INDEX idx_turno_fecha_estado (fecha, estado),
    CONSTRAINT chk_turno_hora CHECK (hora_fin > hora_inicio)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- =====================================================================
-- DATOS FICTICIOS (nombres, DNI y matrículas inventados)
-- =====================================================================

INSERT INTO especialidad (nombre, descripcion) VALUES
  ('Odontología General', 'Consultas y controles generales'),
  ('Ortodoncia', 'Corrección de la posición dental'),
  ('Endodoncia', 'Tratamientos de conducto'),
  ('Cirugía', 'Extracciones y cirugía bucal'),
  ('Periodoncia', 'Tratamiento de encías');

INSERT INTO profesional (nombre, apellido, matricula, email) VALUES
  ('Ana',   'Prueba',  'MP-0001', 'ana.prueba@ejemplo.test'),
  ('Bruno', 'Ficticio','MP-0002', 'bruno.ficticio@ejemplo.test');

-- Ana: General + Ortodoncia · Bruno: General + Cirugía
INSERT INTO profesional_especialidad (profesional_id, especialidad_id) VALUES
  (1, 1), (1, 2), (2, 1), (2, 4);

INSERT INTO paciente (nombre, apellido, dni, telefono, email) VALUES
  ('Carla',  'Ejemplo', '90000001', '000-0000001', 'carla@ejemplo.test'),
  ('Diego',  'Demo',    '90000002', '000-0000002', 'diego@ejemplo.test'),
  ('Elena',  'Muestra', '90000003', NULL,           NULL);

-- Turnos para mañana (fecha relativa: el script siempre genera turnos futuros).
-- Incluye a propósito casos útiles para el C-13:
--   * dos turnos de profesionales DISTINTOS a las 10:00 (hoy el sistema lo permite:
--     es exactamente el choque de sillón que el C-13 debe impedir)
--   * un turno contiguo (10:30, justo cuando termina el de las 10:00)
--   * un turno CANCELADO (no debería bloquear horario)
INSERT INTO turno (paciente_id, profesional_id, especialidad_id, fecha, hora_inicio, hora_fin, estado, motivo_cancelacion) VALUES
  (1, 1, 1, CURDATE() + INTERVAL 1 DAY, '10:00', '10:30', 'RESERVADO', NULL),
  (2, 2, 1, CURDATE() + INTERVAL 1 DAY, '10:00', '10:30', 'RESERVADO', NULL),
  (3, 1, 2, CURDATE() + INTERVAL 1 DAY, '10:30', '11:00', 'RESERVADO', NULL),
  (1, 2, 4, CURDATE() + INTERVAL 1 DAY, '11:00', '11:30', 'CANCELADO', 'Paciente avisó que no puede (dato ficticio)');

-- Verificación rápida (debe mostrar 6 tablas y los conteos)
SHOW TABLES;
SELECT 'especialidad' AS tabla, COUNT(*) AS filas FROM especialidad
UNION ALL SELECT 'profesional', COUNT(*) FROM profesional
UNION ALL SELECT 'paciente', COUNT(*) FROM paciente
UNION ALL SELECT 'turno', COUNT(*) FROM turno;
