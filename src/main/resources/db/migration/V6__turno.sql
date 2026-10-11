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
