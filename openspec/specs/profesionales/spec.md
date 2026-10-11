# profesionales Specification

## Purpose

Gestión de la ficha de profesional con matrícula única, especialidades asignadas y vínculo opcional a usuario odontólogo, para que la agenda pueda asignar turnos a profesionales identificados.

## Requirements

### Requirement: Alta de profesional — campos y éxito

El sistema SHALL exponer `POST /api/profesionales` que crea ficha con `nombre` (1..100), `apellido` (1..100), `matricula` (máx 30, opcional, única), `telefono`/`email` opcionales validados, `usuario_id` opcional y `especialidad_ids` (≥1, existen y activas). Éxito → 201 con `ProfesionalResponse`.

#### Scenario: Alta ok con 2 especialidades

- **WHEN** `ADMIN` envía `POST /api/profesionales` con nombre, apellido, matrícula y `especialidad_ids` de 2 especialidades activas
- **THEN** responde 201 con esos valores, `activo=true` y ambas especialidades en la respuesta.

#### Scenario: Email inválido devuelve 400

- **WHEN** se envía `POST /api/profesionales` con `email` sin formato válido
- **THEN** responde 400 con `fieldErrors.email` y no crea la ficha.

### Requirement: Alta sin especialidad devuelve 400 (RN-PR-01)

El sistema SHALL responder 400 con `fieldErrors.especialidad_ids` cuando `especialidad_ids` esté ausente, vacío o solo con ids inexistentes/inactivos; nunca crea la ficha.

#### Scenario: Sin especialidades devuelve 400

- **WHEN** se envía `POST /api/profesionales` sin `especialidad_ids` o con lista vacía
- **THEN** responde 400 con `fieldErrors.especialidad_ids` y no crea fila.

#### Scenario: Especialidad inexistente devuelve 400

- **WHEN** se envía `POST /api/profesionales` con un id de especialidad que no existe
- **THEN** responde 400 y no crea la ficha.

### Requirement: Matrícula duplicada devuelve 409 (RN-PR-02)

El sistema SHALL responder 409 con `fieldErrors.matricula` cuando la `matricula` (trim, case preservado, comparación exacta) ya exista, incluida carrera concurrente (red final: constraint `UNIQUE`).

#### Scenario: Matrícula duplicada devuelve 409

- **WHEN** existe matrícula "M-123" y se envía `POST /api/profesionales` con la misma matrícula
- **THEN** responde 409 con `fieldErrors.matricula` y no crea fila nueva.

### Requirement: Lista paginada con filtros por especialidad y activo

El sistema SHALL exponer `GET /api/profesionales?especialidad=&activo=&page=&size=&sort=` que retorna `Page<ProfesionalResponse>` (default `size=20`, `sort=apellido,asc`); filtros combinables AND; sin resultados → 200 con `content:[]`.

#### Scenario: Filtro por especialidad y activo

- **WHEN** un rol autenticado llama `GET /api/profesionales?especialidad={id}&activo=true` existiendo un activo y un inactivo vinculados
- **THEN** responde 200 solo con el activo vinculado en `content`.

### Requirement: Consulta y edición con re-asignación

El sistema SHALL exponer `GET /api/profesionales/{id}` (200 o 404) y `PATCH /api/profesionales/{id}` parcial (nombre/apellido/telefono/email/matricula/usuario_id/`especialidad_ids` reemplaza el set; validaciones del alta; `especialidad_ids` vacío → 400; éxito → 200).

#### Scenario: Re-asignar especialidades ok

- **WHEN** `ADMIN` envía `PATCH /api/profesionales/{id}` con `especialidad_ids` de 1 especialidad activa distinta
- **THEN** responde 200 con solo esa especialidad vinculada.

#### Scenario: Id inexistente devuelve 404

- **WHEN** se llama `GET /api/profesionales/999999`
- **THEN** responde 404 y no crea ni modifica filas.

### Requirement: Desactivar con guard de turnos futuros (RN-PR-04)

El sistema SHALL exponer `PATCH /api/profesionales/{id}/desactivar` que pone `activo=false` salvo con turnos futuros `RESERVADO` (→ 400, ficha intacta); reactivar vía `PATCH /{id}` con `{"activo":true}` siempre ok; el check consulta la tabla `turno` (`fecha >= hoy AND estado='RESERVADO'`), cerrando el TODO(C-08).

#### Scenario: Desactivar sin turnos ok

- **WHEN** `ADMIN` llama `PATCH /api/profesionales/{id}/desactivar` sin turnos futuros
- **THEN** responde 200 con `activo=false`.

#### Scenario: Desactivar con turno futuro devuelve 400

- **WHEN** `ADMIN` llama `PATCH /api/profesionales/{id}/desactivar` existiendo un turno futuro RESERVADO del profesional
- **THEN** responde 400 y la ficha sigue `activo=true`.

### Requirement: Vinculación a usuario odontólogo

El sistema SHALL validar que `usuario_id` exista, tenga rol `ODONTOLOGO` (otro rol → 400) y no esté vinculado a otra ficha (→ 409); columna `UNIQUE NULLABLE`, `ON DELETE SET NULL`.

#### Scenario: Usuario no-odontólogo devuelve 400

- **WHEN** se envía `POST /api/profesionales` con `usuario_id` de un `PACIENTE`
- **THEN** responde 400 con `fieldErrors.usuario_id` y no crea la ficha.

#### Scenario: Link duplicado devuelve 409

- **WHEN** se informa un `usuario_id` ya vinculado a otra ficha
- **THEN** responde 409 y no modifica fichas.

### Requirement: RBAC ADMIN escritura, resto lectura

El sistema SHALL hacer cumplir en backend: `ADMIN` → CRUD completo; `SECRETARIA`/`ODONTOLOGO` → solo `GET`, 403 ante `POST/PATCH`; `PACIENTE` → 403 ante `POST/PATCH` y `GET` lista/detalle salvo regla explícita; no autenticado → 401/302.

#### Scenario: Paciente POST devuelve 403

- **WHEN** `PACIENTE` envía `POST /api/profesionales` con cuerpo válido
- **THEN** responde 403 y no crea la ficha.

#### Scenario: Secretaria puede listar

- **WHEN** `SECRETARIA` llama `GET /api/profesionales`
- **THEN** responde 200 con la página.
