# Design: C-07 Profesionales ABM

## Context

Ver `proposal.md` (Why). Estado actual: `Especialidad`/`Paciente`/`Usuario` existen con patrón Service+DTO+`saveAndFlush`+pre-check+constraint DB (ver `EspecialidadService`, `PacienteService`); `V1..V4` aplicadas; `EspecialidadController#profesionales` es stub vacío; `EspecialidadUsagePort` tiene bean `NoOpEspecialidadUsage` con handoff explícito a C-07. Sin tablas `profesional`/`profesional_especialidad` ni tabla `turno` (llega en C-08).

## Goals / Non-Goals

**Goals:**
- ABM profesional con N:M real, validaciones RN-PR-01/02/04 y RBAC ADMIN-escritura.
- Cerrar handoff C-06: guard especialidad real + `GET /{id}/profesionales` real.
- Migración V5 forward-only + tests H2 coherentes con patrones C-05/C-06.

**Non-Goals:**
- Agenda/disponibilidad por horario (C-08+); horarios por profesional/día (pregunta Alta abierta, fuera de v1).
- Multi-rol usuario, soft-delete profesional (solo flag `activo`), borrado físico.
- Cambiar contrato o RBAC de endpoints existentes.

## Decisions

### D-01 — Intermedia explícita `ProfesionalEspecialidad` con `@EmbeddedId`

Entidad intermedia explícita (`profesional_id`, `especialidad_id`, PK compuesta `@EmbeddedId`) en vez de `@ManyToMany`. Alternativa `@ManyToMany` descartada: oculta la FK para el guard de C-06/C-08 (cautivos, RN-PR-05) y dificulta queries `COUNT`/`EXISTS` por especialidad. Sigue patrón Catálogo N:M de `04` y deja puerta a columnas futuras (fecha_vinculación) sin remodelar.

### D-02 — Validaciones en `ProfesionalService` con doble nivel (pre-check + constraint)

Alta/edición validan en servicio dentro de TX (`especialidad_ids` no vacío, existen+activas vía `findAllById`+filtro `activa`, matrícula `existsByMatricula`, link usuario) y la DB cierra carreras (`UNIQUE matricula`, `UNIQUE usuario_id`, PK compuesta). Mismo patrón `EspecialidadService`/`PacienteService`; `DataIntegrityViolationException` → 409 con re-resolución para mensaje exacto.

### D-03 — Check usuario-odontólogo en servicio (rol + unicidad)

`validarLink(usuarioId, excluirId)`: existe (404/400) → no vinculado a otra ficha (409 vía `findByUsuarioId`) → `rol == ODONTOLOGO` (400 si no). Orden importa: unicidad antes que rol para no filtrar info de rol ante link duplicado. Igual a IN-01 de pacientes con rol cambiado.

### D-04 — RN-PR-04 defensivo + `TODO(C-08)`

Sin tabla `turno`, el guard se implementa como método `tieneTurnosFuturosReservados(id)` que hoy retorna `false` con `TODO(C-08: query real)` y el endpoint responde 200; el test "desactivar con turnos 400" se adapta a "desactivar sin turnos 200 + ficha inactiva" más un test `@Disabled("C-08")` con el escenario real esbozado. Alternativa stub-sin-código descartada: el contrato del check queda compilando y visible.

### D-05 — Handoff C-06: `JdbcEspecialidadUsage` real + test 400

Nuevo `@Component @Primary` (o reemplazo con mismo nombre de bean) implementa `tieneProfesionalesCautivos(id)`: `EXISTS` profesional activo vinculado a E cuya única especialidad activa sea E (JPQL/`JdbcTemplate` count). Elimina `NoOpEspecialidadUsage`. Test integración: profesional activo solo-E + `PATCH activa=false` → 400, fila intacta; desactivar con profesional multi-especialidad → 200.

### D-06 — Migración `V5__profesional.sql` forward-only, tablas nuevas

`CREATE TABLE profesional` (`BIGINT UNSIGNED AUTO_INCREMENT`, `utf8mb4_0900_ai_ci`, `UNIQUE matricula` nullable, `UNIQUE usuario_id` nullable, `INDEX(apellido)`, FK `usuario_id → usuario.id ON DELETE SET NULL`) + `profesional_especialidad` (PK compuesta, FKs con índices, `ON DELETE CASCADE` lado profesional / `RESTRICT` lado especialidad). Tablas nuevas → `NOT NULL` sin default seguro. Rollback = no-op documentado.

### D-07 — Controladores thin + excepciones mapeadas

`ProfesionalController` replica `EspecialidadController`: `@PreAuthorize("hasRole('ADMIN')")` en POST/PATCH, GET autenticado, `@Valid` DTOs (`CreateProfesionalRequest`/`UpdateProfesionalRequest`/`ProfesionalResponse.from()`). Nuevas `DuplicateMatriculaException`→409, `InvalidProfesionalException`→400, `ProfesionalConTurnosException`→400 en `GlobalExceptionHandler` (mismo envelope problem-details que C-05/C-06).

## Risks / Trade-offs

- [Carrera matrícula/link] → Mitigación: constraints `UNIQUE` + catch `DataIntegrityViolationException` → 409 (patrón probado C-05/C-06).
- [Re-asignar especialidades deja profesional sin activas] → Mitigación: rechazar set vacío o todo-inactivo con 400 antes de persistir, dentro de la misma TX.
- [`GET /{id}/profesionales` ahora lee join] → Mitigación: índice FK + paginación; volumen v1 bajo, sin N+1 (fetch join o proyección).
- [RN-PR-04 real llega en C-08] → Mitigación: check defensivo + TODO + test `@Disabled` para no olvidar.
- [TZ] → Mitigación: sin fechas en este change; `TimeZoneConfig` existente intacto.

## Migration Plan

1. Deploy: Flyway `V5` crea 2 tablas vacías (sin backfill, sin downtime).
2. Verificar: `flyway info` + `GET /api/profesionales` 200 vacío + `GET /{id}/profesionales` real.
3. Rollback: forward-only; en dev `DROP TABLE profesional_especialidad, profesional`; en prod fix-forward `V6` si el DDL falla.

## Open Questions

- Ninguna que cambie specs o tasks; las 3 Altas de `10_preguntas_abiertas.md` (horario por profesional/día, sedes, paciente sin cuenta) quedan fuera de v1 por diseño.
