# Design — C-09 turnos-consulta-agenda

## Context

Ver `proposal.md` (Why). Estado actual: `TurnoController` solo tiene `POST /api/turnos`; `TurnoService.crear()` con TX serializable; `TurnoRepository` con `findByProfesionalIdAndFechaAndEstadoOrderByHoraInicioAsc` (para sugeridos) e índices V6 (`profesional_id,fecha` / `paciente_id,fecha` / `fecha,estado`). `AuthzService` resuelve ownership de paciente (`isPropioPaciente`, `pacienteRepository.findById`) pero no de profesional; `ProfesionalRepository.findByUsuarioId` y `PacienteRepository.findByUsuarioId` ya existen (C-05/C-07). TZ fija `America/Argentina/Buenos_Aires` vía `APP_TIMEZONE`; `GlobalExceptionHandler` ya mapea 400/403/404. Sin migración nueva.

## Goals / Non-Goals

**Goals:**
- Lectura con scoping por rol resuelto en servicio (no solo `@PreAuthorize`), queries `readOnly` paginadas sobre índices V6.
- Huecos libres calculados en servicio (puro, testeable sin IO) a partir de ocupados `RESERVADO`.
- Reusar `TurnoResponse.from()` y envelope `Page` para no duplicar DTOs.

**Non-Goals:**
- Mutaciones de estado (C-10: cancelar/atendido/reprogramar) — solo lectura.
- Nueva migración o índice (reusa los 3 de V6; si EXPLAIN muestra filesort se documenta, no se migra acá).
- Cursor pagination (OFFSET `Pageable` alcanza para agenda día/semana; nota en D-04).
- UI (C-11 consume estos endpoints).

## Decisions

### D-01 — Scoping por rol en `TurnoService`, `@PreAuthorize` solo autenticado

Scoping (`PACIENTE→paciente_id propio`, `ODONTOLOGO→profesional_id propio`, `SECRETARIA/ADMIN→sin filtro`) vive en métodos `listar/detalle/agenda` del servicio leyendo `SecurityContextHolder`, no en `@PreAuthorize` (que no puede expresar "forzar filtro propio" ni "ignorar param"). Controller usa `@PreAuthorize("isAuthenticated()")` para RN-AU-01 y deja RN-AU-02/03/04 al servicio.
Alternativa descartada: `@authz` SpEL por endpoint — fragmenta la regla en 3 lugares y no cubre "ignorar `profesional` en paciente".

### D-02 — `AuthzService.isPropioProfesional` + lookups `findByUsuarioId`

Se agrega `isPropioProfesional(profesionalId)` espejo de `isPropioPaciente`, y el servicio resuelve ficha propia vía `pacienteRepository.findByUsuarioId(uid)` / `profesionalRepository.findByUsuarioId(uid)` una vez por request. Paciente pide `profesional` distinto → se ignora (200 filtrado, no 403: evita filtrar existencia de agenda ajena); odontólogo pide otro → 403 explícito (RN-AU-04, Flujo 3 "forzado a su id"). Paciente/odontólogo sin ficha vinculada → paciente: 200 vacío; odontólogo: 403 (no tiene agenda propia).
Alternativa descartada: 404 en ambos casos — rompería el contrato "paciente aislado ve vacío" de US-030 CA-2.

### D-03 — Queries derivadas + `@Query` con `Pageable`, `readOnly=true`

- Lista: derivadas `findByProfesionalIdAndFecha…`, `findByPacienteId…`, `findByFecha…`, variantes `Between` para `desde/hasta`, con y sin `estado`, `OrderByFechaAscHoraInicioAsc`, todas con `Pageable` → `Page.map(TurnoResponse::from con lookups)`. Rango semana acotado: si `hasta-desde > 31 días` → 400 (evita full scan sobre `fecha,estado`).
- Detalle: `findById` + check scoping post-lectura (paciente ajeno → 404, no 403, para no revelar existencia; odontólogo ajeno → 403).
- Agenda: reusa `findByProfesionalIdAndFechaAndEstadoOrderByHoraInicioAsc(RESERVADO)` existente + `find…(CANCELADO)` no necesario (solo RESERVADO ocupa).
- Todo `@Transactional(readOnly=true)` (registry springboot-patterns).
Alternativa descartada: Specifications/Criteria dinámico — sobredimensionado para 5 filtros fijos; Querydsl no está en el stack.

### D-04 — Huecos en servicio puro `HuecosCalculator`

Función pura `List<LocalTime> libres(desde, hasta, duracionMin, ocupados)` genera slots `[DESDE, HASTA-duracion]` step y excluye los que solapan `hora_inicio < fin && hora_fin > inicio` con ocupados (misma semántica de bordes que C-08: adyacente libre). `ocupados` sale de la query RESERVADO; respuesta `AgendaResponse(fecha, ocupados: List<TurnoResponse>, libres: List<LocalTime>)`. Sin paginación en agenda (día acotado: 20 slots con 30min/08-18).
Nota mysql: `ORDER BY hora_inicio` usa índice `(profesional_id,fecha,hora_inicio)` leftmost; `OFFSET` aceptable (<100 filas/día); cursor solo si C-11 pide scroll infinito (no v1).

### D-05 — DTOs y validación de fecha

Nuevos `TurnoQueryParams` (record con `@DateTimeFormat(iso=DATE)`), `AgendaResponse`, `HuecosResponse` como alias de agenda (CHANGES.md pide ambos nombres: `HuecosResponse` = `libres`, `AgendaResponse` = envelope completo). `fecha/desde/hasta` se parsean con `LocalDate.parse` en formato ISO; `DateTimeParseException` → `GlobalExceptionHandler` 400 `fieldErrors.fecha` (extender handler existente, no try/catch por controller). `Page<TurnoResponse>` se expone como `TurnoListResponse` (type alias / wrapper con `content,page,size,totalElements`) para contrato estable. `estado` query param → `EstadoTurno` enum, valor inválido → 400 estándar de Spring (ya `fieldErrors` vía handler).

### D-06 — Sin migración, TZ y 401/403

Sin `V7`: solo SELECTs sobre índices V6. `fecha default hoy` = `LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires"))` (RN-GL-02), nunca `systemDefault`. Controller `@PreAuthorize("isAuthenticated()")` → 401 sin sesión vía entry point existente (C-03); 403 odontólogo-otra-agenda vía `TurnoForbiddenException` → handler 403 existente.

## Risks / Trade-offs

- [Risk] N+1 al mapear `Page<Turno>` a `TurnoResponse` (lookups paciente/profesional/especialidad por fila) → Mitigación: batch `findAllById` por página (≤ size 20 default) y mapear en memoria; documentado en tasks.
- [Risk] `hasta-desde` amplio degrada a scan sobre índice `fecha,estado` → Mitigación: cap 31 días con 400 + `size` max 100.
- [Risk] Paciente con `profesional` ignorado silenciosamente confunde a frontend → Mitigación: documentado en spec + header `X-Scope: paciente-propio` (opcional, decide implementador; no cambia spec).
- [Risk] Dos fichas (paciente+profesional) para mismo usuario (SU-01 permite link separado) → Mitigación: scoping elige por rol activo del `Authentication`, no por existencia de ficha.

## Migration Plan

Sin migración. Deploy: solo código; rollback: revert commit (endpoints nuevos, sin estado). Verificación post-deploy: `GET /api/turnos?fecha=hoy` por rol + agenda de un profesional con ocupados conocidos.

## Open Questions

- Ninguna que cambie specs/enfoque/tasks. Detalle menor (nombre exacto `HuecosResponse` vs anidado en `AgendaResponse`) queda a criterio del implementador con ambos nombres exportados.
