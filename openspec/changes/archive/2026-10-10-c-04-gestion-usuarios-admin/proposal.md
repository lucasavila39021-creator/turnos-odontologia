# Proposal — C-04 Gestion Usuarios Admin

## Why

Sin ABM de usuarios solo-ADMIN (US-002, RN-AU-05/06) el plantel y los permisos quedan congelados en el seed dev `admin/admin123` de C-02: no hay forma gobernada de dar de alta odontólogos/secretarias, cambiar roles ni desactivar cuentas. C-04 cierra la rama admin independiente del GATE 3 (C-04 ∥ C-05 ∥ C-06) y desbloquea la operación real antes de C-11.

## What Changes

- `GET /api/usuarios` paginado (`page/size/sort`, default `size=20`) solo ADMIN — lista `UsuarioResponse` sin `password_hash`.
- `POST /api/usuarios` alta ADMIN con `username` único, `email` único si presente, `rol` enum obligatorio, `password` min 8 chars hasheado BCrypt(12).
- `PATCH /api/usuarios/{id}` edición ADMIN de `email`, `rol`, `activo` (username inmutable; password no se cambia por este endpoint en v1).
- `PATCH /api/usuarios/{id}/desactivar` atajo ADMIN que pone `activo=false` (idempotente).
- Regla "no desactivar / degradar último ADMIN activo": `COUNT WHERE rol=ADMIN AND activo=true > 1` antes de `activo=false` o cambio de rol del último; si viola → 400 problem-details.
- RBAC `require_role(ADMIN)` en los 4 endpoints (`@PreAuthorize("hasRole('ADMIN')")`, deny-by-default ya existente en `SecurityConfig`); resto de roles → 403 via `SecurityJsonHandlers`.
- Validaciones Bean Validation + unicidad DB (`uk_usuario_username`, `uk_usuario_email` de C-02): duplicado → 409 problem-details con `fieldErrors`; rol inválido / password corto / email mal formato → 400.
- Auditoría: `created_at/updated_at` de `BaseEntity` + log SLF4J estructurado de cambios de rol (`log.info("usuario rol cambiado id={} by={} {}->{}")`); sin tabla nueva, sin migración (reusa tabla `usuario` C-02).
- Tests: alta ok 201, username duplicado 409, email duplicado 409, cambio rol ok, desactivar último admin 400, degradar rol último admin 400, `GET /api/usuarios` como PACIENTE/SECRETARIA/ODONTOLOGO → 403, paginado ok.

## Capabilities

### New Capabilities

- `user-management`: ABM de usuarios y roles solo-ADMIN sobre la entidad `Usuario` existente (listar paginado, alta, edición parcial, desactivar, regla último-admin, auditoría). Frontera durable que podrá absorber en el futuro cambio de password admin y reactivación sin crear otra capability.

### Modified Capabilities

- (ninguna — `user-accounts` ya garantiza unicidad/rol/BCrypt/activo a nivel persistencia y `auth` ya provee `@EnableMethodSecurity` + 401/403/400 RFC7807; C-04 solo los consume, no cambia sus REQUIREMENTS).

## Impact

- Código nuevo: `UsuarioService` (application), `UsuarioController` (`/api/usuarios`, thin), DTOs `CreateUsuarioRequest`/`UpdateUsuarioRequest`/`UsuarioResponse.from()` (extender existente), `LastAdminProtection` (lógica en service), mapeos 409 en `GlobalExceptionHandler` (`DataIntegrityViolationException` → 409), método `countByRolAndActivoTrue` en `UsuarioRepository`.
- Sin migración DB (reusa `V2__usuario.sql`); sin cambio en `SecurityConfig` salvo verificar `PATCH` incluido en CORS `allowedMethods` (hoy solo GET/POST/PUT/DELETE/OPTIONS — agregar PATCH).
- APIs afectadas: ninguna existente cambia comportamiento; `POST /api/auth/login` y `GET /api/auth/me` intactos.
- Dependencias: requiere C-03 archivado (sesión + `@EnableMethodSecurity` + `AuthzService` + `GlobalExceptionHandler`); converge en C-11 (menú ADMIN + gestión).
- Riesgo principal: condición de carrera desactivando últimos admins concurrentes — mitigado con `@Transactional` + re-check `COUNT` dentro de TX (documentado en design; lock pesimista solo si el test de concurrencia lo exige).
