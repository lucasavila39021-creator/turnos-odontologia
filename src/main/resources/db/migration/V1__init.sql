-- V1__init.sql — Bootstrap Flyway (C-01)
-- No crea tablas de dominio. C-02 creará V2__usuario con tabla usuario.
-- Este archivo deja rastro versionado y valida que Flyway está operativo.
-- Política: forward-only, nunca editar migración deployada. Nueva columna → nullable o con DEFAULT.

-- Flyway gestiona flyway_schema_history automáticamente; este script es intencionalmente vacío
-- salvo comentario y validación de charset.

-- Verificación de charset (no-op si ya existe)
-- SELECT 1;
