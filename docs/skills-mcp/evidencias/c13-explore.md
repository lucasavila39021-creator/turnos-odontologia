# C-13 `turnos-por-sillon` — resumen de exploración

> Salida del `/opsx-explore` guiado por `docs/skills-mcp/evidencias/prompt-c13-explore.md`.

## Hallazgos de la base (`mysql_query`)

- `SHOW TABLES`: 6 tablas (`usuario`, `paciente`, `profesional`, `especialidad`, `profesional_especialidad`, `turno`); **no existe `sillon`**.
- `SHOW CREATE TABLE turno`: no hay `sillon_id`; PK, `idx_turno_prof_fecha(profesional_id,fecha,hora_inicio)`, `idx_turno_pac_fecha`, `idx_turno_fecha_estado` y CHECK `hora_fin > hora_inicio`. MySQL creó solo `fk_turno_especialidad` y `fk_turno_created_by`: no figuran en `V6__turno.sql`.
- `SELECT ... FROM turno`: turnos 1 (prof 1) y 2 (prof 2), 2026-11-03 10:00-10:30, ambos `RESERVADO`; es exactamente el choque de sillón que hoy se acepta. El turno 4 está `CANCELADO` (debe seguir liberando).

## Decisiones recomendadas

1. Modelo: `sillon(id, nombre UNIQUE, activo)` + `turno.sillon_id BIGINT UNSIGNED NULL` FK `ON DELETE RESTRICT ON UPDATE CASCADE`, sin backfill.
2. Índice: `idx_turno_sillon_fecha(sillon_id, fecha, hora_inicio)`, espejo de `idx_turno_prof_fecha`; al empezar por `sillon_id` también cubre la FK y evita el índice automático de MySQL.
3. Legado: `sillon_id NULL` no participa de RN-TU-11 (la query filtra por sillón) y sigue rigiendo RN-TU-01/05; no se mueven turnos viejos.
4. Obligatorio: sí para toda alta nueva (API y SSR) vía servicio/Bean Validation → 400; la columna queda NULL en DB por los legacy (forward-only). Si fuera opcional, se esquivaría RN-TU-11 omitiendo el campo.
5. RN-TU-11: "No pueden existir dos turnos `RESERVADO` para el mismo sillón que se solapen en tiempo el mismo día, aunque sean de profesionales distintos. Validación en servicio + transacción DB; si hay carrera, gana el primero (constraint lógico, no solo índice). Turnos sin sillón no participan. — *Justificación: el sillón es el recurso físico compartido entre profesionales.*"
6. 409: mismo envelope `{error:"Horario ocupado", sugeridos}`, evaluando profesional → paciente → sillón; `sugeridos` debe filtrar por profesional **y** sillón, o sugiere huecos que vuelven a chocar. No agregar campos nuevos.
7. Concurrencia: misma TX SERIALIZABLE; lock JVM profesional + lock JVM sillón en orden fijo (prof → sillón) y `SELECT ... FOR UPDATE` sobre `sillon`, como ya se hace con `profesional`. Cancelar ya libera porque las queries solo miran `RESERVADO`.
8. Seed: `Sillón 1` y `Sillón 2` activos en `DevDataInitializer` y en `scripts/dev-db-nativo.sql`; en el script asignar ambos turnos de las 10:00 al mismo sillón (demuestra el 409) y dejar un turno legacy con `sillon_id NULL`.

## Preguntas abiertas

- El alta SSR (`TurnoMvcController` → `TurnoForm` → `turno-form.html`) también crea turnos, pero CHANGES solo nombra `POST /api/turnos`. ¿Entra en C-13? (recomendado: sí, comparte `TurnoCreateRequest`).
- `reprogramar` crea un turno nuevo con el mismo profesional: ¿conserva el `sillon_id` del original y qué pasa si el original es legacy `NULL`? (recomendado: conservar; si es NULL, exigir `sillon_id` en ese request).
- ¿La agenda y `libres` siguen siendo por profesional, sin mostrar disponibilidad de sillón? (recomendado: fuera de alcance, solo validar al crear).
- ¿ABM de sillones queda fuera de alcance? (CHANGES dice que sí; el seed alcanza para dev).

## Decisiones confirmadas por Lucas

- El alta SSR entra en C-13.
- `reprogramar` conserva el `sillon_id` del original; si el original es `NULL`, se exige `sillon_id` en ese request.
- Agenda y `libres` por sillón quedan fuera de alcance.
- ABM de sillones fuera de alcance; seed para dev.

Estas decisiones cierran las preguntas abiertas de la sección anterior.
