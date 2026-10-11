# Tasks: C-07 Profesionales ABM

## 1. Migración y dominio

- [x] 1.1 Crear `V5__profesional.sql` (tablas `profesional` + `profesional_especialidad`, UNIQUE/INDEX/FKs, forward-only) y verificar con `flyway info` + arranque en H2/MySQL dev sin errores.
- [x] 1.2 Crear entidades `Profesional` + `ProfesionalEspecialidad` (`@EmbeddedId`) + `ProfesionalRepository`/`ProfesionalEspecialidadRepository` con `existsByMatricula`/`findByUsuarioId` y verificar con test de persistencia H2 (save + find).
- [x] 1.3 Crear DTOs `CreateProfesionalRequest`/`UpdateProfesionalRequest`/`ProfesionalResponse.from()` con `@Valid` y verificar con test de validación (nombre ausente → 400).

## 2. Servicio y reglas

- [x] 2.1 Implementar `ProfesionalService` (crear/listar/obtener/editar+reasignar/desactivar, TX, doble nivel pre-check+constraint, `validarLink` ODONTOLOGO) y verificar con tests de servicio H2 (alta 2 esp ok, re-asignar reemplaza set).
- [x] 2.2 Implementar guard RN-PR-04 defensivo (`tieneTurnosFuturosReservados` → false + `TODO(C-08)`) con `ProfesionalConTurnosException` cableada y verificar con test (desactivar sin turnos → 200 + `activo=false`).
- [x] 2.3 Crear excepciones `DuplicateMatriculaException`/`InvalidProfesionalException`/`ProfesionalConTurnosException` + mapeos 400/409 en `GlobalExceptionHandler` y verificar con test MockMvc del envelope problem-details.

## 3. Handoff C-06 (guard especialidad real)

- [x] 3.1 Reemplazar `NoOpEspecialidadUsage` por implementación real sobre `profesional_especialidad` (cautivo = activo cuya única especialidad activa es E) y verificar con test integración 400 (`PATCH especialidad activa=false` con profesional cautivo → 400, fila intacta; multi-esp → 200).
- [x] 3.2 Volver real `GET /api/especialidades/{id}/profesionales` (paginado, orden `apellido,nombre`, 404 si no existe) y verificar con test (2 vinculados → 200 ordenados; sin vinculados → `content:[]`).

## 4. Controller y RBAC

- [x] 4.1 Crear `ProfesionalController` (`GET ?especialidad=&activo=`, `POST` 201, `GET {id}`, `PATCH {id}`, `PATCH {id}/desactivar`; POST/PATCH `@PreAuthorize ADMIN`) y verificar con MockMvc (alta 2 esp 201, sin esp 400, matrícula dup 409, usuario no-odontólogo 400, PACIENTE POST 403, SECRETARIA GET 200).
- [x] 4.2 Tests E2E de RBAC (ADMIN CRUD / resto GET / no-auth 401) y validación final con `mvn test` verde + `openspec validate --change c-07-profesionales-abm` sin errores.
