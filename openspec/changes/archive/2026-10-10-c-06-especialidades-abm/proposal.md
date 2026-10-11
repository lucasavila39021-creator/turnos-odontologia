# Proposal — C-06 especialidades-abm

## Why

El catálogo de especialidades (Ortodoncia, Endodoncia, etc.) es prerrequisito de C-07 profesionales-abm y de la barrera C-08 turnos-reserva (RN-PR-05 exige que el turno referencie una especialidad del profesional). Sin este ABM, no hay agenda asignable.

## What Changes

- Nueva entidad `Especialidad` + tabla `especialidad` vía migración `V4__especialidad.sql` (forward-only, tabla nueva).
- Endpoints: `GET /api/especialidades` (filtro `activa`), `POST /api/especialidades`, `PATCH /api/especialidades/{id}`, `GET /api/especialidades/{id}/profesionales` (stub paginado vacío hasta C-07, contrato congelado).
- Validación nombre único normalizado (trim + case-insensitive, 409) en 2 niveles: pre-check servicio + `UNIQUE(nombre)` DB.
- Regla no-desactivar: `PATCH activa=false` → 400 si hay profesionales activos cuya única especialidad activa es esta.
- RBAC: ADMIN CRUD; resto solo `GET`.

## Capabilities

### New Capabilities

- `especialidades`: catálogo de especialidades — alta, lista con filtro, edición parcial, regla de no-desactivar, RBAC y endpoint de profesionales por especialidad.

### Modified Capabilities

- Ninguna (no se tocan specs existentes).

## Impact

- Nuevo código: `domain/model/Especialidad.java`, `domain/repository/EspecialidadRepository.java`, `application/dto/*Especialidad*`, `application/service/EspecialidadService.java`, `web/controller/EspecialidadController.java`, extensión de `GlobalExceptionHandler` (409 duplicado, 400 no-desactivar).
- Nueva migración `V4__especialidad.sql`; sin cambios en tablas existentes.
- Desbloquea C-07 (requiere especialidades existentes y activas). Sin breaking changes.
