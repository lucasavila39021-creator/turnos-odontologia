# Tasks — C-06 especialidades-abm

## 1. Migración V4 + entidad y repositorio

- [x] 1.1 Crear `src/main/resources/db/migration/V4__especialidad.sql` (tabla `especialidad` InnoDB `utf8mb4_0900_ai_ci`, `BIGINT UNSIGNED AUTO_INCREMENT`, `nombre VARCHAR(80) NOT NULL`, `descripcion VARCHAR(255)`, `activa BOOLEAN NOT NULL DEFAULT TRUE`, `uk_especialidad_nombre(nombre)`, `CHECK` nombre no vacío tras trim, `created_at/updated_at DATETIME`) y verificar que el arranque en dev aplica V4 sin errores y `SHOW INDEX FROM especialidad` lista el índice único.
- [x] 1.2 Crear `domain/model/Especialidad.java` (`extends BaseEntity` según D-01, Bean Validation `@NotBlank @Size(max=80)` nombre, `@Size(max=255)` descripcion, `toString` sin volcar descripción completa) y verificar compilación `mvn -q compile`.
- [x] 1.3 Crear `domain/repository/EspecialidadRepository.java` (`existsByNombreIgnoreCase`, `existsByNombreIgnoreCaseAndIdNot`, `findByActiva`) y verificar test de repositorio con Testcontainers MySQL: persiste dos especialidades, el pre-check case-insensitive detecta "ortodoncia" vs "Ortodoncia" y `findByActiva(false)` filtra.

## 2. DTOs + puerto + servicio + errores

- [x] 2.1 Crear `CreateEspecialidadRequest` / `UpdateEspecialidadRequest` / `EspecialidadResponse.from()` (records, `@Valid`: nombre `@NotBlank @Size(max=80)`, descripcion `@Size(max=255)`, activa `Boolean` nullable en Update) y verificar test de validación: cada campo inválido produce `fieldErrors` (nunca 500).
- [x] 2.2 Crear `DuplicateEspecialidadException` + `EspecialidadConProfesionalesException` y extender `GlobalExceptionHandler` (409 con `fieldErrors.nombre` / 400 con mensaje claro, envelope problem-details existente) y verificar test: cada excepción mapea al código esperado.
- [x] 2.3 Crear `EspecialidadUsagePort` + `NoOpEspecialidadUsage` (D-04) e implementar `EspecialidadService` (constructor injection; `@Transactional` escrituras, `readOnly` lecturas; crear con trim + pre-check + `catch DataIntegrityViolationException`; PATCH parcial con trim, 409 excluyendo propia fila, blanco→null en descripcion, guard no-desactivar vía puerto; `Page.map(EspecialidadResponse::from)`) y verificar tests de servicio con Testcontainers MySQL: alta ok, dup case-insensitive 409, edición 409 contra otra fila, desactivar ok con `NoOp`, y guard-400 con fake del puerto que retorna `true`.

## 3. Controller + RBAC

- [x] 3.1 Crear `EspecialidadController` (`POST /api/especialidades` 201; `GET /api/especialidades?activa=&page=&size=` 200 `Page` orden `nombre,asc`; `PATCH /{id}` 200/400/404/409; `GET /{id}/profesionales` 200 página vacía / 404 si no existe; `@PreAuthorize("hasRole('ADMIN')")` en POST/PATCH; thin controller, `@Valid`) y verificar tests MockMvc por rol: admin CRUD 200/201, secretaria POST 403 y PATCH 403, paciente GET 200 y POST 403, odontólogo GET 200, no autenticado 401/302.
- [x] 3.2 Verificar unicidad DB como red final: test de integración que fuerza la carrera (inserción directa duplicada normalizada) y asserts 409 via `DataIntegrityViolationException` mapeada, más `EXPLAIN` de `findByActiva` sin `ALL`.

## 4. Integración, validación y handoff a C-07

- [x] 4.1 Tests de integración end-to-end (Testcontainers MySQL, H2 prohibido por hard rules): alta → lista con filtro `activa` → PATCH descripcion → PATCH renombrar a existente 409 → desactivar ok 200 → reactivar ok → secretaria-403 → validación nombre-vacío-400, y verificar `mvn -q test -Dtest='*Especialidad*'` en verde.
- [x] 4.2 Dejar handoff explícito a C-07 en `NoOpEspecialidadUsage` (javadoc: reemplazar por implementación real sobre `profesional_especialidad` + test integración del 400 con profesional activo cautivo) y verificar `openspec validate --change c-06-especialidades-abm` en verde y `openspec status --change c-06-especialidades-abm` con los 4 artefactos `done`; dejar el change listo para `/opsx-apply`.
