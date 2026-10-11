# Design — C-06 especialidades-abm

## Context

Ver `proposal.md` (Why) y `specs/especialidades/spec.md` (contrato). Estado observado en repo:

- Patrón probado (C-02/C-04/C-05): `domain/model` + `BaseEntity` (`@MappedSuperclass`, `id` IDENTITY, `created_at/updated_at` con `@PrePersist/@PreUpdate`, TZ `America/Argentina/Buenos_Aires` en `TimeZoneConfig`), `BaseRepository`, servicio con pre-check + red final en constraint DB (`catch DataIntegrityViolationException`), controller thin con DTOs record + `from()` + `@Valid`, `Page.map(Response::from)`.
- Seguridad: `SecurityConfig` + `@EnableMethodSecurity`, sesión server-side, handlers JSON 401/403 (`SecurityJsonHandlers`, `GlobalExceptionHandler`).
- Migraciones: `V1__init.sql`, `V2__usuario.sql`, `V3__paciente.sql` (InnoDB `utf8mb4_0900_ai_ci`, `BIGINT UNSIGNED AUTO_INCREMENT`). Este change crea `V4__especialidad.sql` — tabla nueva, sin backfill.
- Restricción clave: las tablas `profesional` / `profesional_especialidad` **no existen aún** (las crea C-07). La regla de no-desactivar debe diseñarse para no acoplarse a tablas inexistentes.

## Goals / Non-Goals

**Goals:**

- Replicar el patrón `Controller/Service/Repository/DTO` para `Especialidad` sin patrones nuevos salvo el estrictamente necesario.
- Unicidad de nombre robusta bajo concurrencia (pre-check + constraint DB), case-insensitive.
- Regla de no-desactivar implementada como lógica desacoplada testeable, con integración real en C-07.
- Contrato `GET /{id}/profesionales` congelado desde v1 aunque responda vacío hasta C-07.

**Non-Goals:**

- No se toca `Profesional/Turno/Paciente/Usuario` (C-07/C-08).
- No `GET /{id}` individual ni `DELETE` físico (RN-GL-03; fuera de scope CHANGES).
- No UI (C-11), no seed de especialidades (C-12).

## Decisions

### D-01 — Entidad `Especialidad extends BaseEntity`

`Especialidad` mapea tabla `especialidad`: `nombre VARCHAR(80) NOT NULL UNIQUE`, `descripcion VARCHAR(255)`, `activa BOOLEAN NOT NULL DEFAULT true`. Hereda `id` + `created_at/updated_at` de `BaseEntity` (el `updated_at` extra vs. el scope es consistencia gratuita con `Paciente`/`Usuario`; sin `mappedBy`, sin asociaciones JPA).

*Alternativa descartada:* entidad sin `BaseEntity` con solo `created_at` — rompería la convención de auditoría mínima por ahorrar una columna.

### D-02 — Unicidad nombre en 2 niveles (pre-check normalizado + `UNIQUE` DB)

`EspecialidadService.crear/actualizar` normaliza con `trim` y pre-chequea con `existsByNombreIgnoreCase(nombreTrim)` → `DuplicateEspecialidadException` → 409. Red final: `UNIQUE(nombre)` sobre columna con colación `utf8mb4_0900_ai_ci` (case-insensitive a nivel DB) + `catch DataIntegrityViolationException` para la carrera concurrente — patrón idéntico a `UsuarioService`/`PacienteService`. En PATCH, el pre-check excluye la propia fila (`existsByNombreIgnoreCaseAndIdNot`).

*Alternativa descartada:* índice funcional `UNIQUE(LOWER(nombre))` — innecesario porque `ai_ci` ya colapsa caso; documentar que también colapsa acentos (deseable en mostrador).

### D-03 — RBAC simple sin extensión de `@authz`

- `POST`, `PATCH`: `@PreAuthorize("hasRole('ADMIN')")`.
- `GET` lista y `GET /{id}/profesionales`: solo autenticado (cubre `SecurityConfig`; sin anotación específica — el default autenticado aplica).

A diferencia de C-05, no hay ownership ni scoping por rol: el catálogo es lectura global. Sin métodos nuevos en `AuthzService`.

*Alternativa descartada:* reutilizar `@authz.isAdmin()` — equivalente pero menos idiomático que `hasRole('ADMIN')` directo usado en `UsuarioController`.

### D-04 — Regla no-desactivar vía puerto `EspecialidadUsagePort` (tablas C-07 aún no existen)

El guard no puede hacer `JOIN` a tablas inexistentes. `EspecialidadService` depende de la interfaz `EspecialidadUsagePort { boolean tieneProfesionalesCautivos(Long especialidadId) }` (lógica pura: "¿existe profesional activo cuya única especialidad activa sea esta?"). C-06 provee `NoOpEspecialidadUsage` (retorna `false` — sin profesionales posibles, desactivar siempre permitido) y el servicio lanza `EspecialidadConProfesionalesException` → 400 cuando el puerto retorna `true`. C-07 reemplaza el bean por la implementación JDBC/JPQL real + el test de integración 400.

*Alternativa descartada:* `JdbcTemplate` con `catch BadSqlGrammarException` (tabla ausente → permitir) — frágil, oculta errores reales de SQL y deja deuda silenciosa; el puerto hace explícito el contrato pendiente con C-07.

### D-05 — `V4__especialidad.sql` forward-only, tabla nueva

Convención Flyway `V{n}__`, InnoDB `utf8mb4_0900_ai_ci`, `BIGINT UNSIGNED AUTO_INCREMENT`, `DATETIME` defaults, `activa BOOLEAN NOT NULL DEFAULT TRUE`, `uk_especialidad_nombre(nombre)`, `CHECK (CHAR_LENGTH(TRIM(nombre)) > 0)`. Sin `NOT NULL` sin default sobre datos existentes (tabla nueva → sin backfill). Rollback = no-op (forward-only; en dev `DROP TABLE especialidad`).

### D-06 — `GET /{id}/profesionales` como stub paginado con contrato congelado

Controller responde `Page.empty(pageable)` (404 si la especialidad no existe). Congela ruta, params `page/size`, orden documentado y RBAC — C-07 lo reemplaza por query real sobre `profesional_especialidad`. Igual que D-07 de C-05 con `/{id}/turnos`.

### D-07 — DTOs record + `from()` + `@Valid`, `GlobalExceptionHandler` extendido

`CreateEspecialidadRequest(nombre @NotBlank @Size(max=80), descripcion @Size(max=255))`, `UpdateEspecialidadRequest` (los 3 campos nullable), `EspecialidadResponse.from()`. Handler: `DuplicateEspecialidadException → 409`, `EspecialidadConProfesionalesException → 400`, reutilizando el envelope problem-details existente. Sin exponer entidad JPA.

## Risks / Trade-offs

- [Colación `ai_ci` colapsa acentos en unicidad] → Mitigación: aceptable para catálogo clínico ("Cirugía" ≈ "cirugia"); documentado en tests.
- [`NoOp` permite desactivar todo en C-06] → Mitigación: sin profesionales existentes el 400 es inalcanzable por definición; C-07 cierra el hueco con implementación real + test de integración (task explícita de handoff).
- [Sin `GET /{id}`] → Mitigación: en scope CHANGES; el PATCH 404 cubre existencia; C-07/C-11 usan lista + filtro si necesitan una fila.
- [Nombre con solo espacios pasa Bean Validation `@NotBlank` pero DB `CHECK`] → Mitigación: servicio hace `trim` antes de validar/persistir; doble red (servicio + CHECK).

## Migration Plan

1. Aplicar `V4__especialidad.sql` via Flyway (`migrate`; `validate` en prod). Tabla nueva → sin downtime, sin backfill, sin dual-write.
2. Desplegar código (entidad + repo + DTOs + puerto + servicio + controller + handler). Sin flags.
3. Verificación post-deploy: `SELECT COUNT(*) FROM especialidad` (=0), `SHOW INDEX FROM especialidad` (`uk_especialidad_nombre`), smoke `POST/GET/PATCH /api/especialidades` con admin dev.
4. Rollback: solo código (tabla vacía sin consumidores es inocua); no se revierte migración en prod.

## Open Questions

Ninguna que cambie specs, enfoque o tasks. Handoff diferido a C-07: implementar `JdbcEspecialidadUsage` real + test integración del 400 con profesionales activos (ver tasks 4.2).
