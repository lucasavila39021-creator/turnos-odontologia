# Design: C-13 Turnos por Sillón

## Context

Ver `proposal.md` (Why). Estado actual confirmado con `mysql_query`: la base `turnos_db` tiene 6 tablas y **no existe `sillon`**; `turno` no tiene `sillon_id` y ya trae `idx_turno_prof_fecha(profesional_id, fecha, hora_inicio)`, `idx_turno_pac_fecha`, `idx_turno_fecha_estado` y CHECK `hora_fin > hora_inicio`. MySQL creó índices automáticos `fk_turno_especialidad` y `fk_turno_created_by` que no están en `V6__turno.sql`.

El código vigente (C-08/C-10) usa `TurnoService.crear/reprogramar` con TX `SERIALIZABLE`, un `TransactionTemplate`, un lock JVM por profesional (`ConcurrentHashMap<Long,Object> locks`) y `ProfesionalRepository.findByIdForUpdate` (`PESSIMISTIC_WRITE`). El comentario actual asume "sin anidamiento → sin deadlock"; este change introduce un segundo lock y debe preservar esa garantía con un orden fijo. El alta SSR (`TurnoMvcController` → `TurnoForm` → `turno-form.html`) reutiliza `TurnoCreateRequest` y también crea turnos.

## Goals / Non-Goals

**Goals:**
- Asignar sillón a todo turno nuevo (API y SSR) y validar RN-TU-11 en creación y reprogramación.
- Mantener el 409 existente (`{"error":"Horario ocupado","sugeridos":[...]}`) con sugeridos coherentes con profesional **y** sillón.
- Migración forward-only sin backfill, compatible con turnos legacy sin sillón.
- Concurrencia 1-gana también por sillón, sin introducir deadlocks.

**Non-Goals:**
- ABM de sillones, multi-sede, disponibilidad de agenda/`libres` por sillón (siguen por profesional), mover turnos viejos a un sillón.
- Cambiar el envelope de error ni agregar campos nuevos a la respuesta de conflicto.
- Cambiar RN-TU-01/05 ni el comportamiento de cancelar/atendido.

## Decisions

### D-01 — Modelo `Sillon` + `turno.sillon_id` nullable

`Sillon` extiende `BaseEntity` con `nombre VARCHAR(80) NOT NULL UNIQUE`, `activo BOOLEAN NOT NULL DEFAULT TRUE` y timestamps (mismo patrón que `Especialidad`). `Turno` suma `Long sillonId` mapeado a `sillon_id`, **sin `@NotNull` en la entidad**: la columna debe aceptar los turnos legacy. La obligatoriedad vive en el DTO (`@NotNull` en `TurnoCreateRequest`), no en JPA. `SillonRepository` expone `findByIdForUpdate` (`PESSIMISTIC_WRITE`, espejo de `ProfesionalRepository`) y `existsByNombreIgnoreCase` para el seed idempotente. Alternativa `@ManyToOne` descartada: el resto del modelo usa FKs como `Long` para evitar LAZY/N+1 y mantener el servicio dueño de los 404.

### D-02 — `V7__sillon.sql` forward-only, sin backfill

La migración: 1. `CREATE TABLE sillon` (`BIGINT UNSIGNED AUTO_INCREMENT`, `utf8mb4_0900_ai_ci`, `uk_sillon_nombre`, `idx_sillon_activo`); 2. `ALTER TABLE turno ADD COLUMN sillon_id BIGINT UNSIGNED NULL`; 3. FK `fk_turno_sillon → sillon(id) ON DELETE RESTRICT ON UPDATE CASCADE`; 4. `INDEX idx_turno_sillon_fecha(sillon_id, fecha, hora_inicio)`. No hay `UPDATE` de turnos existentes: quedan `NULL` y fuera de RN-TU-11. El índice compuesto empieza por `sillon_id`, por lo que también cubre la FK y evita el índice automático que MySQL crearía (diferencia ya observada en H17). Rollback = no-op documentado; en dev, `DROP TABLE sillon` exige quitar antes la FK/columna.

### D-03 — Orden de validación y mapeo de errores

`crear`/`doCrear` mantiene el orden de C-08 y agrega el sillón: 1. ownership del paciente (403); 2. FKs existen → 404 (`paciente`, `profesional`, `especialidad`, `sillon`); 3. flags `activo` → 400 (`profesional`, `especialidad`, `sillon`); 4. RN-PR-05; 5. `hora_fin`; 6. RN-TU-03/04; 7. RN-TU-01; 8. RN-TU-05; 9. **RN-TU-11**; 10. `saveAndFlush`. `sillon_id` ausente lo corta antes Bean Validation (`@NotNull` → 400 `fieldErrors.sillon_id`); inexistente → 404; inactivo → 400. El 409 sigue siendo `TurnoOcupadoException` → mismo envelope; no se agregan campos. Alternativa validar RN-TU-11 antes que profesional/paciente descartada: el orden actual es el contrato de errores observado por C-08/C-10 y no conviene alterarlo.

### D-04 — `existsSolapeSillon` y sugeridos por profesional + sillón

`TurnoRepository` suma `existsSolapeSillon(sillonId, fecha, inicio, fin, estado)` con la misma query de bordes (`hora_inicio < :fin AND hora_fin > :inicio`, solo `RESERVADO`). `calcularSugeridos` recibe también `sillonId` y descarta un candidato si hay solape de profesional **o** de sillón; así el 409 no sugiere huecos que volverían a chocar. Para turnos legacy (`sillon_id NULL`) la query de sillón no aplica: no bloquean ni son bloqueados. `mapearTurnos` resuelve el sillón en batch (una query por página, mismo patrón que paciente/profesional/especialidad) y `TurnoResponse` agrega `SillonInfo(id, nombre)` nullable.

### D-05 — Concurrencia: dos locks JVM y dos locks de fila, siempre profesional → sillón

Se agrega `ConcurrentHashMap<Long,Object> locksSillon`. `crear` toma primero el lock JVM del profesional y, dentro, el del sillón; `doCrear` hace `profesionalRepository.findByIdForUpdate` y luego `sillonRepository.findByIdForUpdate`. `reprogramar`/`doReprogramar` replican el mismo orden. Todos los caminos usan el orden fijo **profesional → sillón**, por lo que no puede formarse un ciclo de espera: dos TX de profesionales distintos y mismo sillón se serializan en la fila del sillón; dos TX del mismo profesional se serializan en la fila del profesional. Se actualiza el comentario de la clase que hoy afirma "sin anidamiento → sin deadlock". Alternativa lock único global por sillón descartada: serializa de más y no cubre la carrera por profesional.

### D-06 — Reprogramar conserva sillón y exige uno si el original es legacy

`TurnoReprogramarRequest` suma `sillonId` opcional (`@JsonAlias("sillon_id")`). Regla: si el original tiene `sillon_id`, se conserva; si el request trae otro distinto → 400 `sillon_id` (no se ignora en silencio). Si el original es legacy (`NULL`), el request debe informarlo; ausente → 400. El sillón efectivo se valida (404/400) y se usa tanto en RN-TU-11 como en la fila nueva. El resto del flujo mantiene cancelar-en-memoria + validar + crear en una TX, con rollback total ante 409. Alternativa aceptar un sillón distinto al original descartada: contradice la decisión confirmada de conservar el recurso.

### D-07 — Alta SSR y seed

`TurnoForm` suma `sillonId`; `TurnoMvcController` lo parsea como obligatorio, lo pasa en `TurnoCreateRequest` y `cargarApoyos` agrega la lista de sillones activos. `turno-form.html` suma el select de sillón con su `fieldErrors['sillonId']`. `DevDataInitializer` crea `Sillón 1` y `Sillón 2` activos de forma idempotente y asigna los turnos seed a `Sillón 1`. `scripts/dev-db-nativo.sql` replica `V7`: crea los dos sillones, asigna **ambos turnos de las 10:00 al mismo sillón** para evidenciar el 409, y deja el turno CANCELADO de las 11:00 con `sillon_id NULL` como caso legacy. Alternativa sembrar sillones por migración descartada: datos dev no van en Flyway.

## Risks / Trade-offs

- [Deadlock por dos locks] → Mitigación: orden fijo profesional → sillón en JVM y en DB; test de concurrencia por sillón con profesionales distintos.
- [Turnos legacy sin sillón] → Mitigación: columna nullable, sin backfill y queries de sillón que excluyen `NULL`; test explícito de no bloqueo.
- [Sugeridos incoherentes] → Mitigación: `calcularSugeridos` filtra por profesional y sillón; test de sugeridos en 409 por sillón.
- [Alta SSR rota por campo nuevo] → Mitigación: actualizar `TurnoForm`, controller y template en la misma tarea; test MVC del formulario sin sillón → 400.
- [Sillón inactivo con turnos futuros] → Fuera de alcance (no hay ABM); el seed solo crea activos y el alta rechaza inactivos.

## Migration Plan

1. Deploy: Flyway `V7` crea `sillon` y agrega `turno.sillon_id` nullable + FK + `idx_turno_sillon_fecha`; sin backfill ni downtime.
2. Verificar: `SHOW CREATE TABLE turno` con `sillon_id`/FK/índice; alta con sillón → 201; mismo sillón y horario con otro profesional → 409; turno legacy → no bloquea.
3. Rollback: forward-only; en dev quitar FK/índice/columna y `DROP TABLE sillon`; en prod fix-forward `V8` si el DDL falla.

## Open Questions

- Ninguna que cambie specs, approach o tasks. Las decisiones de alcance (SSR incluido, reprogramar conserva sillón, agenda/`libres` por profesional, ABM fuera) ya fueron confirmadas en `docs/skills-mcp/evidencias/c13-explore.md`.
