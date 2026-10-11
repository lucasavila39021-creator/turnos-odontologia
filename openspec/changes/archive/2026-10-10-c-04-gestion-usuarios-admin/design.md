# Design — C-04 Gestion Usuarios Admin

## Context

Ver `proposal.md` (Why). Estado actual verificado en código: `Usuario`/`Rol`/`UsuarioRepository` existen (C-02) con `uk_usuario_username`/`uk_usuario_email`; `SecurityConfig` con `@EnableMethodSecurity`, sesión `JSESSIONID` + CSRF cookie, `authenticated()` por defecto y CORS sin `PATCH`; `AuthzService(@authz)` con `hasRole/isAdmin`; `GlobalExceptionHandler` RFC7807 cubre 401/403/429/400-validación pero no 409; DTOs `CreateUsuarioRequest`/`UsuarioResponse.from()` existen (reusar/extender); sin tabla nueva ni migración. Patrón vigente: thin controller → service `@Transactional` → repository, DTOs `@Valid`, `ResponseEntity` + `Page.map()`.

## Goals / Non-Goals

**Goals:**
- ABM admin completo con unicidad pre-chequeada + constraint DB como red, último-admin atómico en TX, RBAC método-nivel, 409 mapeado RFC7807.
- Cero migración, cero cambio de comportamiento en auth existente.

**Non-Goals:**
- Cambio/reset de password por admin, reactivación dedicada (`activo=true` se hace vía `PATCH` genérico), vistas SSR/Thymeleaf (solo API JSON; UI en C-11), multi-rol, borrado físico (RN-GL-03), auditoría en tabla (solo `BaseEntity` + log).

## Decisions

1. **Nueva capa `UsuarioService` + `UsuarioController`, sin tocar `AuthController`.**
   Por qué: `AuthController` es flujo público login/me/logout con rate-limit; mezclar ABM admin acoplaría seguridad pública con gestión. Alternativa (meter métodos en `AuthController`) rechazada: viola thin-controller y ensancha superficie CSRF/rate-limit.
2. **`@PreAuthorize("hasRole('ADMIN')")` a nivel clase `UsuarioController` + `deny-by-default` existente.**
   Por qué: regla del registry (`springboot-security`); un solo punto, imposible olvidar un método. Alternativa `@authz.isAdmin()` equivalente — se usa `hasRole` por convención del codebase C-03; `SecurityFilterChain.anyRequest().authenticated()` sigue como red.
3. **DTOs: reusar `UsuarioResponse.from()`, crear `CreateUsuarioRequest` (nuevo, no confundir con `CreateUsuarioRequest` de C-02 si ya existe para seed — extenderlo) y `UpdateUsuarioRequest` con campos todos-nullable + `@AssertTrue` de al-menos-un-campo.**
   Validación: `username: @NotBlank @Size(max=50) @Pattern("^[a-zA-Z0-9._-]{3,50}$")`; `password: @NotBlank @Size(min=8,max=100)`; `rol: @NotNull`; `email: @Email @Size(max=120)` nullable. Por qué: RN-GL-01 mensajes por campo; username inmutable en update evita colisión con login case-insensitive. Alternativa `PUT` full-replace rechazada: PATCH parcial es lo que pide CHANGES y evita sobre-escribir password por accidente.
4. **Unicidad en dos niveles: `existsByUsernameIgnoreCase/existsByEmail` pre-check (400-limpio) + catch `DataIntegrityViolationException` → 409.**
   Por qué: pre-check da `fieldErrors` preciso; catch cubre carrera concurrente (dos altas mismo username). Nuevo `@ExceptionHandler(DataIntegrityViolationException)` en `GlobalExceptionHandler` que inspecciona `constraintName`/mensaje (`uk_usuario_username` vs `uk_usuario_email`) y responde 409 `{"title":"Conflict","fieldErrors":{...}}`. Alternativa solo-DB rechazada: el mensaje SQL crudo filtraría detalle interno.
5. **Último-admin: `countByRolAndActivoTrue(Rol.ADMIN)` re-chequeado dentro de la misma `@Transactional` de escritura; si `==1` y el target es ese admin y la op lo desactiva/degrada → `LastAdminException` → 400.**
   Por qué: simple, sin lock pesimista en el caso común; la ventana de carrera (dos desactivaciones simultáneas del penúltimo par) se acepta en v1 y se documenta; si el test de concurrencia la evidencia se escala a `SELECT ... FOR UPDATE` / `PESSIMISTIC_WRITE` en el test. Alternativa constraint DB parcial rechazada: MySQL no tiene partial unique index declarativo simple para esto.
6. **Listado: `Pageable` Spring (`page/size/sort`, `size` cap 100) + `repository.findAll(pageable).map(UsuarioResponse::from)` con `@Transactional(readOnly=true)`.**
   Por qué: convención registry (`Page via map()`); `readOnly` evita flush accidental. Sin filtros por rol/username en v1 (fuera de scope CHANGES).
7. **CORS: agregar `PATCH` a `allowedMethods` en `SecurityConfig.corsConfigurationSource`.**
   Por qué: hoy solo GET/POST/PUT/DELETE/OPTIONS — los dos PATCH admin fallarían preflight desde SPA. Cambio de una línea, sin relajar orígenes.
8. **Sin migración Flyway.**
   Por qué: tabla `usuario` + índices ya existen (C-02); `database-migrations` forward-only prohíbe DDL innecesario. Solo se añade derived query `long countByRolAndActivoTrue(Rol rol)` (usa `idx_usuario_rol` existente).

## Risks / Trade-offs

- [Riesgo] Carrera doble-alta mismo username → ambos pre-checks pasan → Mitigación: catch `DataIntegrityViolationException` → 409; el perdedor recibe 409 limpio.
- [Riesgo] Carrera doble-desactivación últimos dos admins → quedan 0 → Mitigación v1: re-check en TX + test de concurrencia documentado; escalar a lock pesimista solo si el test falla.
- [Riesgo] `email` NULL múltiple colisiona en pre-check → Mitigación: pre-check solo si `email != null && !blank`; DB ya permite múltiples NULL.
- [Trade-off] PATCH ignora `username` en vez de 400 → se elige 400 explícito si `username` presente para no silenciar errores de cliente.
- [Trade-off] Log de cambio de rol sin PII completa → solo ids y roles, nunca password/email en el mensaje.

## Migration Plan

- Deploy: solo código, sin Flyway ( `validate` pasa sin cambios); revertible con rollback de artefacto.
- Rollback: revertir commit; ningún dato cambia de forma irreversible (desactivaciones son `activo=false` reversibles vía PATCH).
- Post-deploy: `curl` admin `GET /api/usuarios` 200, `POST` duplicado 409, intento último-admin 400.

## Open Questions

- Ninguna que cambie specs/enfoque/tasks. Detalle menor diferido a apply: mensaje exacto en español del 400 último-admin (propuesta: `"No se puede desactivar o degradar al último administrador activo"`).
