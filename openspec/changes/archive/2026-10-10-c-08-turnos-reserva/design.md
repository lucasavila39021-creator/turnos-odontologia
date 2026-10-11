# Design: C-08 Turnos Reserva

## Context

Ver `proposal.md` (Why). Estado actual: `Paciente`/`Profesional`/`Especialidad`/`Usuario` existen con patrón Service+DTO+`saveAndFlush`+pre-check+constraint (C-05/C-06/C-07); `V1..V5` aplicadas; `ProfesionalService.tieneTurnosFuturosReservados` es defensivo (`false` + `TODO(C-08)`); `APP_TURNO_DURACION_MIN/HORARIO_DESDE/HASTA`, `TimeZoneConfig` America/Argentina/Buenos_Aires y `GlobalExceptionHandler` problem-details ya existen. Sin tabla `turno` ni `POST /api/turnos`.

## Goals / Non-Goals

**Goals:**
- Reserva atómica con no-superposición profesional+paciente bajo concurrencia (RN-TU-01/05).
- Validaciones RN-TU-02/03/04 + RN-PR-05 + RN-TU-09 con errores 400/404/409/403 precisos y `sugeridos`.
- V6 forward-only + cerrar TODO C-07 con guard real.

**Non-Goals:**
- Consulta de agenda/huecos (`GET /api/turnos`, C-09); cancelar/atendido/reprogramar (C-10); horarios por profesional/día (pregunta Alta fuera de v1); UI (C-11).

## Decisions

### D-01 — Entidad `Turno` + índices equality-first, CHECK DB

`Turno` (`paciente/profesional/especialidad FK NOT NULL`, `fecha DATE`, `hora_inicio/hora_fin TIME`, `estado ENUM default RESERVADO`, `motivo_cancelacion NULLABLE`, `created_by FK NULLABLE`, timestamps). Índices ` (profesional_id,fecha,hora_inicio)`, `(paciente_id,fecha)`, `(fecha,estado)` (equality-first, leftmost-prefix para solape y agenda C-09). CHECK `hora_fin > hora_inicio`. Alternativa índice parcial solo-RESERVADO descartada: MySQL 8.0 no soporta índices parciales; el filtro `estado='RESERVADO'` vive en la query.

### D-02 — `TurnoService.crear` en TX SERIALIZABLE + doble nivel

`@Transactional(isolation=SERIALIZABLE)` en `crear`: orden fijo (1. existen paciente/profesional/especialidad+activo → 404/400; 2. RN-PR-05 `existsByProfesionalAndEspecialidad`; 3. calcula `hora_fin`; 4. RN-TU-03/04; 5. solape profesional; 6. solape paciente; 7. `saveAndFlush`). Query solape: `WHERE {prof|pac}_id=? AND fecha=? AND estado='RESERVADO' AND hora_inicio < :fin AND hora_fin > :inicio` (bordes adyacentes permitidos). Pre-check en servicio + constraint lógico; la TX serializable + gap-locks MySQL (REPEATABLE READ) cierra la carrera: el segundo commit espera o falla → 409. Alternativa `PESSIMISTIC_WRITE` por rango descartada: bloquea más y complica H2; serializable + retry es el patrón del registry. H2 en tests no da gap-locks reales → test de hilos valida lógica, no aislamiento MySQL (nota explícita).

### D-03 — `hora_fin` calculada en servicio, nunca del cliente

`TurnoCreateRequest` solo trae `hora_inicio`; `hora_fin` se calcula (`plusMinutes(APP_TURNO_DURACION_MIN)`) y se persiste para consultas por rango. Alternativa aceptar `hora_fin` del cliente descartada: viola RN-TU-02 y abre huecos irregulares.

### D-04 — 409 con `sugeridos` calculados en servicio

Al detectar solape, el servicio genera hasta 3 slots libres próximos (step `APP_TURNO_DURACION_MIN` desde `hora_inicio` pedida hasta `HASTA`, excluyendo solapados) y `TurnoOcupadoException(sugeridos)` → 409 `{"error":"Horario ocupado","sugeridos":[...]}`. Cálculo en servicio (no DB) reutilizable por C-09. Sin libres → lista vacía, nunca 500.

### D-05 — RBAC ownership vía `@authz` + 403 antes de validar negocio

`POST /api/turnos` con `@PreAuthorize("hasAnyRole('PACIENTE','SECRETARIA','ADMIN')")` (ODONTOLOGO cae a 403 por denegación) + check `authz.isPropioPaciente(pacienteId)` para PACIENTE (`paciente.usuario_id == current_user.id`, paciente sin link → 403). Orden: auth primero (no filtrar existencia de fichas ajenas), luego 404 FKs, luego 400 RN, luego 409. `created_by = current_user.id` auditable.

### D-06 — `V6__turno.sql` forward-only, tabla nueva

`CREATE TABLE turno` (`BIGINT UNSIGNED AUTO_INCREMENT`, `utf8mb4_0900_ai_ci`, FKs `paciente/profesional/especialidad → RESTRICT`, `created_by → SET NULL`, 3 índices D-01, CHECK hora, `ENUM` estado). Tabla nueva → `NOT NULL` sin default seguro. Rollback = no-op documentado (drop solo en dev).

### D-07 — Handoff C-07: guard RN-PR-04 real

`ProfesionalService.tieneTurnosFuturosReservados` pasa a `turnoRepository.existsByProfesionalIdAndFechaGreaterThanEqualAndEstado(id, hoy, RESERVADO)` (usa índice `(profesional_id,fecha)`); se elimina el TODO y se habilita el test `@Disabled("C-08")` de C-07. Sin cambio de firma.

## Risks / Trade-offs

- [Carrera concurrente en MySQL] → Mitigación: TX serializable + orden consistente + reintento ante deadlock 1213 con backoff; test hilos H2 + nota de gap-locks.
- [Borde TZ (turno hoy pero hora pasada)] → Mitigación: comparar `LocalDateTime` en `America/Argentina/Buenos_Aires` vía `TimeZoneConfig`/`APP_TIMEZONE`, nunca UTC implícito.
- [Sugeridos costosos] → Mitigación: máximo 3, loop acotado `DESDE→HASTA`, reutiliza query solape; volumen v1 bajo.
- [RN-TU-10 anticipación] → No configurado en v1; si aparece, se suma como 400 sin cambiar firma.

## Migration Plan

1. Deploy: Flyway `V6` crea `turno` vacía (sin backfill, sin downtime); `flyway info` verde.
2. Verificar: `POST /api/turnos` 201 + `PATCH /api/profesionales/{id}/desactivar` con turno futuro → 400.
3. Rollback: forward-only; en dev `DROP TABLE turno`; en prod fix-forward `V7` si el DDL falla.

## Open Questions

- Ninguna que cambie specs o tasks; las 3 Altas de `10_preguntas_abiertas.md` quedan fuera de v1 por diseño.
