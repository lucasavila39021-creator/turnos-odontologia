# Tasks — C-05 pacientes-abm

## 1. Migración V3 + entidad y repositorio

- [x] 1.1 Crear `src/main/resources/db/migration/V3__paciente.sql` (tabla `paciente` InnoDB `utf8mb4_0900_ai_ci`, `BIGINT UNSIGNED AUTO_INCREMENT`, `uk_paciente_dni`, `uk_paciente_usuario`, `idx_paciente_apellido(apellido,nombre)`, FK `usuario_id → usuario.id ON DELETE SET NULL ON UPDATE CASCADE`, `CHECK` dni no vacío) y verificar `mvn -q flyway:migrate` o arranque en dev aplica V3 sin errores y `SHOW INDEX FROM paciente` lista los 3 índices.
- [x] 1.2 Crear `domain/model/Paciente.java` (`extends BaseEntity`, columnas según D-01, `fecha_nacimiento LocalDate`, `usuarioId Long` nullable, validaciones Bean Validation, `toString` sin PII sensible) y verificar compilación `mvn -q compile`.
- [x] 1.3 Crear `domain/repository/PacienteRepository.java` (`findByDni`, `findByUsuarioId`, `existsByDni`, `existsByUsuarioId`, `Page findByApellidoStartingWithIgnoreCase`, `findByDniAndApellidoStartingWithIgnoreCase`) y verificar test de repositorio con Testcontainers MySQL: persiste y recupera por DNI/usuario_id.

## 2. DTOs + servicio + errores

- [x] 2.1 Crear `CreatePacienteRequest` / `UpdatePacienteRequest` / `PacienteResponse.from()` (records, `@Valid`: `@NotBlank/@Size` nombre-apellido-dni, `@Email` email, `@Past` fecha_nacimiento, `dni` ausente de Update) y verificar test de validación: cada campo inválido produce `fieldErrors` (nunca 500).
- [x] 2.2 Crear `DuplicateDniException` (con `pacienteId`) + extensión de `GlobalExceptionHandler` (409 con `pacienteId/pacienteUri`) y verificar test: excepción mapea a 409 con link `/api/pacientes/{id}`.
- [x] 2.3 Implementar `PacienteService` (constructor injection `PacienteRepository + UsuarioRepository`; `@Transactional` escrituras, `readOnly` lecturas; crear con trim DNI + pre-check + `catch DataIntegrityViolationException` con re-resolución de link; actualizar parcial con `dni` inmutable 400, email blanco→null, validación IN-01 rol/unicidad; `Page.map(PacienteResponse::from)`) y verificar tests de servicio: alta ok, DNI dup 409 con link, email inválido 400, vincular usuario inexistente 400, doble link 409, link rol no-PACIENTE 400.

## 3. Controller + RBAC

- [x] 3.1 Extender `AuthzService` con `isPacienteOwner(Long pacienteId)` (`findByUsuarioId(currentUserId)` vs `#id`) y verificar tests: owner true/false, anónimo false.
- [x] 3.2 Crear `PacienteController` (`POST /api/pacientes` 201; `GET /api/pacientes?dni=&apellido=&page=&size=` 200 `Page`; `GET /{id}` 200/404; `PATCH /{id}` 200/400/404; `GET /{id}/turnos` 200 página vacía; `@PreAuthorize` según D-04; thin controller, `@Valid`) y verificar tests MockMvc por rol: secretaria-todos 200, paciente-403 en ficha ajena y en lista, paciente 200 en propia, odontólogo GET 200 + POST 403, no autenticado 401/302, DELETE 404/405.
- [x] 3.3 Verificar búsqueda por apellido usa índice: test de integración con 3 fichas (`EXPLAIN` sin `ALL/filesort` o aserción de orden `apellido,nombre` + prefijo case-insensitive con tildes) y `GET ?dni=` exacto.

## 4. Integración y verificación final

- [x] 4.1 Tests de integración end-to-end (Testcontainers MySQL, Fig `H2` prohibido por hard rules): alta → búsqueda apellido → ficha → PATCH → historial vacío → DNI dup 409 con link → paciente-403 → validación email-400, y verificar `mvn -q test -Dtest='*Paciente*'` en verde.
- [x] 4.2 Verificar `openspec validate --change c-05-pacientes-abm` en verde y `openspec status --change c-05-pacientes-abm` con los 4 artefactos `done`; dejar el change listo para `/opsx-apply`.
