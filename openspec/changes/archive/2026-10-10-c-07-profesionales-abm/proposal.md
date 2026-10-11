# Proposal: C-07 Profesionales ABM

## Why

Sin ficha de profesional con especialidades no hay agenda asignable: C-08 (turnos-reserva, barrera) necesita `profesional_id` + `profesional_especialidad` existentes. C-06 dejó el guard de no-desactivar especialidad en NoOp y `GET /{id}/profesionales` como stub vacío; este change los vuelve reales.

## What Changes

- Entidades `Profesional` + `ProfesionalEspecialidad` (intermedia explícita, PK compuesta) + repositories + `V5__profesional.sql` forward-only.
- `ProfesionalService` con TX: crear (≥1 especialidad), listar con filtros, obtener, editar parcial + re-asignar especialidades, desactivar con guard RN-PR-04.
- Endpoints `GET /api/profesionales?especialidad=&activo=`, `POST`, `GET /{id}`, `PATCH /{id}`, `PATCH /{id}/desactivar`; `GET /api/especialidades/{id}/profesionales` pasa de stub a real.
- Reemplazo `NoOpEspecialidadUsage` por implementación real sobre `profesional_especialidad` (handoff C-06) + test 400.
- Validaciones: RN-PR-01 (400 vacío), RN-PR-02 matrícula 409, especialidades existen+activas, `usuario_id` rol ODONTOLOGO y no vinculado 409. RN-PR-04 defensivo + TODO C-08 (tabla turno aún no existe).
- RBAC: ADMIN CRUD; resto solo GET; PACIENTE 403 en POST/PATCH.

## Capabilities

### New Capabilities

- `profesionales`: ABM de profesional con especialidades, filtros, validaciones RN-PR-01/02/04 y RBAC ADMIN-escritura.

### Modified Capabilities

- `especialidades`: el requirement "Profesionales por especialidad" pasa de stub vacío a listado real; el guard no-desactivar pasa de NoOp a real.

## Impact

- Nuevo: `domain/model/Profesional{,Id}`, repositories, `ProfesionalService`, `ProfesionalController`, DTOs, `V5__profesional.sql`, excepciones (`DuplicateMatricula`, `ProfesionalConTurnos`, `InvalidProfesional`).
- Modificado: `EspecialidadController#profesionales` (stub → query real), bean `EspecialidadUsagePort` (NoOp → real), `GlobalExceptionHandler` (mapeos 400/409 nuevos).
- Sin breaking: ningún endpoint existente cambia de firma; solo el stub devuelve datos reales.
