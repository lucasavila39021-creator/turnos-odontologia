# Proposal — C-05 pacientes-abm

## Why

La agenda (C-08) necesita una ficha de paciente asignable: hoy no existe entidad `Paciente` ni endpoint para alta/búsqueda, por lo que ningún turno puede crearse ni vincularse a un `Usuario` con rol `PACIENTE` (IN-01). Este change desbloquea la barrera de agenda `C-05 + C-07 → C-08`.

## What Changes

- Nueva entidad JPA `Paciente` + `PacienteRepository` + migración `V3__paciente.sql` (tabla `paciente` + FK `usuario_id` NULLABLE UNIQUE + índices).
- Nuevo `PacienteService` con reglas RN-PA-01..04 (unicidad DNI en 2 niveles, obligatorios, email/telefono formato, sin DELETE físico).
- Nuevos endpoints REST bajo `/api/pacientes`:
  - `POST /api/pacientes` — alta (RN-PA-02).
  - `GET /api/pacientes?dni=&apellido=&page=&size=` — buscador paginado por DNI exacto / apellido prefijo (índice `apellido,nombre`).
  - `GET /api/pacientes/{id}` — ficha por id.
  - `PATCH /api/pacientes/{id}` — edición parcial.
  - `GET /api/pacientes/{id}/turnos` — historial (placeholder paginado vacío hasta C-08; respeta RBAC desde v1).
- RBAC en backend con `@PreAuthorize` + `@authz`: SECRETARIA/ADMIN CRUD completo; ODONTOLOGO `GET` lectura; PACIENTE `GET/PATCH` solo su ficha (`paciente.usuario_id = current_user.id`, RN-AU-03) con 403 al resto.
- Vinculación `paciente.usuario_id` (IN-01): campo nullable en alta/edición; validación de existencia + unicidad de link (un usuario → una ficha).
- DTOs `CreatePacienteRequest` / `UpdatePacienteRequest` / `PacienteResponse` con `@Valid` + mapper `from()`; nunca se expone entidad JPA directa.
- Manejo de errores: DNI duplicado → 409 con link a ficha existente (US-010 CA-2); obligatorios → 400 por campo; email inválido → 400 (RN-GL-01, envelope problem-details existente).

## Capabilities

### New Capabilities

- `pacientes`: ABM y consulta de ficha de paciente con unicidad de DNI, búsqueda por DNI/apellido, RBAC por rol + ownership de ficha propia, y vinculación opcional a `Usuario` (IN-01). Es el dueño durable de RN-PA-01..04 y US-010/US-011; C-08/C-09 lo referencian como FK sin modificarlo.

### Modified Capabilities

- Ninguna. `user-accounts` (entidad `Usuario`), `auth` (RBAC `@authz`) y `user-management` (ABM admin) no cambian requisitos: se reutilizan tal cual (FK hacia `Usuario.id`, `@EnableMethodSecurity` existente).

## Impact

- Código nuevo: `domain/model/Paciente.java`, `domain/repository/PacienteRepository.java`, `application/dto/*Paciente*.java`, `application/service/PacienteService.java`, `application/exception/DuplicateDniException.java`, `web/controller/PacienteController.java`, extensión de `GlobalExceptionHandler` (409 DNI) y de `AuthzService` (`isPacienteOwner`) si se requiere ownership a nivel ficha.
- Migración: `src/main/resources/db/migration/V3__paciente.sql` forward-only (tabla nueva, sin backfill; nullable FK → sin riesgo NOT NULL).
- APIs nuevas bajo `/api/pacientes` (5 endpoints); ningún endpoint existente cambia contrato.
- Dependencias: requiere C-03 archivado (auth/RBAC base + `Usuario`). Desbloquea C-08 (FK `turno.paciente_id`).
- Riesgo bajo-medio: unicidad DNI bajo concurrencia resuelta por constraint DB + pre-check servicio (patrón `UsuarioService`).
