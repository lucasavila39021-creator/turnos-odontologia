# Proposal: C-13 Turnos por Sillón

## Why

Hoy la reserva valida solapes por profesional (RN-TU-01) y por paciente (RN-TU-05), pero no por el recurso físico: dos profesionales distintos pueden ocupar el mismo sillón a la misma hora. La base de desarrollo lo demuestra —los turnos 1 y 2, de los profesionales 1 y 2, están `RESERVADO` el 2026-11-03 de 10:00 a 10:30 (`mysql_query`)— y el sistema lo acepta. Sin sillón, la agenda puede comprometer dos prestaciones sobre un único box.

## What Changes

- Nueva tabla `sillon` (`id`, `nombre` UNIQUE, `activo`) y columna `turno.sillon_id BIGINT UNSIGNED NULL` con FK `→ sillon` (`ON DELETE RESTRICT ON UPDATE CASCADE`), sin backfill de turnos existentes.
- Nueva regla **RN-TU-11 — No superposición por sillón**: *No pueden existir dos turnos `RESERVADO` para el mismo sillón que se solapen en tiempo el mismo día, aunque sean de profesionales distintos. Validación en servicio + transacción DB; si hay carrera, gana el primero (constraint lógico, no solo índice). Turnos sin sillón no participan. — Justificación: el sillón es el recurso físico compartido entre profesionales.*
- `sillon_id` obligatorio en toda alta nueva, tanto `POST /api/turnos` como el alta SSR (`TurnoMvcController` → `TurnoForm`); ausente o inválido → 400. La columna queda nullable en DB solo por los turnos legacy.
- `TurnoService.crear` y `reprogramar` validan RN-TU-11 después de RN-TU-01 y RN-TU-05; el 409 conserva el envelope `{"error":"Horario ocupado","sugeridos":[...]}` y los `sugeridos` filtran por profesional **y** sillón.
- `reprogramar` conserva el `sillon_id` del turno original; si el original es legacy (`sillon_id NULL`), el request debe informar `sillon_id`.
- Índice `idx_turno_sillon_fecha(sillon_id, fecha, hora_inicio)`, espejo de `idx_turno_prof_fecha`; cubre RN-TU-11 y la FK.
- Seed dev: `Sillón 1` y `Sillón 2` activos en `DevDataInitializer` y en `scripts/dev-db-nativo.sql`; en el script los dos turnos de las 10:00 comparten sillón para demostrar el 409 y queda un turno legacy sin sillón.

## Capabilities

### New Capabilities

- (ninguna — el cambio extiende la capacidad existente `turnos`)

### Modified Capabilities

- `turnos`: la creación y la reprogramación incorporan asignación de sillón y no-superposición por sillón (RN-TU-11); el 409 y los `sugeridos` pasan a considerar también el recurso físico.

## Impact

- Nuevo: entidad `Sillon` + `SillonRepository`, `V7__sillon.sql`, `idx_turno_sillon_fecha`, `SillonResponse`, seed de sillones.
- Modificado: `Turno` (+`sillonId`), `TurnoRepository` (`existsSolapeSillon`), `TurnoCreateRequest` (+`sillonId @NotNull`), `TurnoReprogramarRequest` (+`sillonId` condicional), `TurnoResponse` (+`sillon`), `TurnoService.crear/doCrear/reprogramar/doReprogramar/calcularSugeridos/mapearTurnos`, `TurnoForm`, `TurnoMvcController`, `turno-form.html`, `DevDataInitializer`, `scripts/dev-db-nativo.sql`, `knowledge-base/05_reglas_de_negocio.md` (RN-TU-11).
- Sin breaking de endpoints existentes más allá de que `sillon_id` pasa a ser obligatorio en altas nuevas; los turnos legacy sin sillón siguen siendo legibles y no participan de RN-TU-11.
- Fuera de alcance: ABM de sillones, multi-sede, disponibilidad de agenda/`libres` por sillón, mover turnos viejos a un sillón.
