# Tasks — C-04 Gestion Usuarios Admin

## 1. Repository + excepciones base

- [x] 1.1 Añadir `long countByRolAndActivoTrue(Rol rol)` a `UsuarioRepository` y verificar con test de conteo (2 admins activos → 2, tras desactivar 1 → 1).
- [x] 1.2 Crear `LastAdminException(RuntimeException)` en `application/exception` (o `web/exception`) y verificar que compila y es capturable por el handler.
- [x] 1.3 Añadir `PATCH` a `allowedMethods` del CORS en `SecurityConfig` y verificar preflight `OPTIONS /api/usuarios` retorna `Allow: PATCH`.

## 2. DTOs con Bean Validation

- [x] 2.1 Crear/extender `CreateUsuarioRequest(username,password,rol,email)` con `@NotBlank/@Size/@Pattern/@NotNull/@Email` + test unitario de validación (password 7 chars falla, rol null falla, email inválido falla).
- [x] 2.2 Crear `UpdateUsuarioRequest(email,rol,activo)` todo-nullable + `@AssertTrue al menos un campo` y verificar test: body vacío falla 400, `{"rol":"SECRETARIA"}` pasa.
- [x] 2.3 Verificar `UsuarioResponse.from()` no expone `passwordHash` (test de serialización JSON sin campo `password_hash`/`password`).

## 3. UsuarioService + regla último-admin

- [x] 3.1 Implementar `UsuarioService.listar(Pageable)` `@Transactional(readOnly=true)` con `map(UsuarioResponse::from)` y verificar test de paginación (size/totalElements).
- [x] 3.2 Implementar `UsuarioService.crear(dto, actorUsername)` con pre-check `existsByUsername/existsByEmail` + `encoder.encode` BCrypt(12) + `save`, y verificar test alta ok 201-equivalente (hash `$2a$12$`, `matches` true).
- [x] 3.3 Implementar `actualizar(id, dto)` + `desactivar(id)` con re-check `countByRolAndActivoTrue(ADMIN)` en TX que lanza `LastAdminException`, + log SLF4J de cambio de rol, y verificar tests: cambio rol ok, desactivar común ok, último-admin 400-equivalente (sin mutación).
- [x] 3.4 Mapear `DataIntegrityViolationException` → 409 con `fieldErrors.username/email` según constraint (`uk_usuario_username`/`uk_usuario_email`) en `GlobalExceptionHandler`, y verificar test de doble-alta concurrente: uno persiste, otro 409.

## 4. UsuarioController ADMIN-only

- [x] 4.1 Crear `UsuarioController /api/usuarios` con `@PreAuthorize("hasRole('ADMIN')")` a nivel clase: `GET` paginado, `POST` 201, `PATCH /{id}` 200, `PATCH /{id}/desactivar` 200, todos `@Valid`, y verificar MockMvc con ADMIN: cada endpoint responde el status esperado.
- [x] 4.2 Verificar RBAC: PACIENTE/ODONTOLOGO/SECRETARIA autenticados reciben 403 en los 4 endpoints, y sin sesión 401 JSON (`Accept: application/json`) / 302 (`text/html`).
- [x] 4.3 Verificar validaciones E2E vía MockMvc+H2 (o Testcontainers MySQL): username duplicado 409, email duplicado 409, password corto 400, rol inválido 400, desactivar último admin 400, degradar último admin 400, body error sin `password_hash`.

## 5. Integración y cierre

- [x] 5.1 Correr suite completa (`./mvnw test` o perfil H2) y verificar verde sin regresiones en C-02/C-03 (auth login/me/logout, unicidad, health).
- [x] 5.2 Verificar manual smoke: login admin → `GET /api/usuarios` 200 paginado, `POST` alta 201, `PATCH` cambio rol 200, `PATCH /desactivar` último-admin 400, como paciente `GET` 403.
