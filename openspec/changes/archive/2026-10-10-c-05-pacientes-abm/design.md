# Design — C-05 pacientes-abm

## Context

Ver `proposal.md` (Why) y `specs/pacientes/spec.md` (contrato). Estado actual observado en repo:

- `Usuario` (`domain/model/Usuario.java`) extiende `BaseEntity` (`@MappedSuperclass` con `id` IDENTITY + `created_at/updated_at` via `@PrePersist/@PreUpdate`, `LocalDateTime` coherente con TZ `America/Argentina/Buenos_Aires` de `TimeZoneConfig`); repositorio `UsuarioRepository extends BaseRepository`; servicio `UsuarioService` con pre-check `existsBy...IgnoreCase` + red final en constraint DB (`DataIntegrityViolationException`), paginación `Page.map(Response::from)`.
- Seguridad: `SecurityConfig` con `@EnableMethodSecurity`, sesión server-side, CSRF con cookies, handlers JSON 401/403 problem-details (`SecurityJsonHandlers`, `GlobalExceptionHandler`); permiso reutilizable `@authz` (`AuthzService` con `isAdmin/hasRole/isOwner`).
- Migraciones Flyway: `V1__init.sql`, `V2__usuario.sql` (InnoDB `utf8mb4_0900_ai_ci`, `BIGINT UNSIGNED AUTO_INCREMENT`, `UNIQUE` + `INDEX` nombrados). Este change crea `V3__paciente.sql` — tabla nueva, sin backfill, FK nullable → riesgo de migración mínimo.
- `UsuarioController` es el patrón a replicar (thin controller → service con `@Transactional`, DTOs record + `from()`, `@Valid`).

## Goals / Non-Goals

**Goals:**

- Replicar el patrón probado `UsuarioController/Service/Repository/DTO` para `Paciente` sin introducir patrones nuevos.
- Unicidad DNI robusta bajo concurrencia (pre-check + constraint DB).
- RBAC con ownership de ficha propia reutilizando `@authz` (extensión mínima `isPacienteOwner`).
- Contrato `GET /{id}/turnos` congelado desde v1 aunque responda vacío hasta C-08.

**Non-Goals:**

- No se toca `Profesional/Especialidad/Turno` (C-06/C-07/C-08).
- No `DELETE` físico ni `activo` lógico en v1 (RN-GL-03; sin borrado).
- No normalización case-insensitive de DNI más allá de `trim` (el DNI es identificador exacto; la colación `ai_ci` no afecta dígitos).
- No autocomplete ni UI (C-11).

## Decisions

### D-01 — Entidad `Paciente extends BaseEntity` + `usuario_id` FK NULLABLE UNIQUE

`Paciente` mapea tabla `paciente`: `nombre/apellido VARCHAR(100) NOT NULL`, `dni VARCHAR(20) NOT NULL UNIQUE`, `telefono VARCHAR(30)`, `email VARCHAR(120)`, `fecha_nacimiento DATE`, `usuario_id BIGINT NULL UNIQUE` FK → `usuario.id ON DELETE SET NULL ON UPDATE CASCADE`, índices `uk_paciente_dni(dni)`, `uk_paciente_usuario(usuario_id)`, `idx_paciente_apellido(apellido,nombre)`. Relación como columna FK simple (`Long usuarioId`, sin `@OneToOne` bidireccional) — igual que `Usuario` evita asociaciones: sin `mappedBy`, sin cascadas JPA, sin N+1. `SET NULL` preserva la ficha clínica si se borra el usuario (la ficha es dato sanitario, la cuenta es acceso).

*Alternativa descartada:* `@OneToOne Usuario` bidireccional — acopla ciclo de vida y complica `SET NULL`; innecesario en v1.

### D-02 — Unicidad DNI en 2 niveles (pre-check servicio + constraint DB)

`PacienteService.crear` hace `existsByDni(dniTrim)` → `DuplicateDniException(pacienteId existente)` → 409 con link; el `catch DataIntegrityViolationException` cubre la carrera concurrente (la DB gana, patrón idéntico a `UsuarioService.crear`). `dni` se normaliza solo con `trim` (sin lowercasing: dígitos/guiones no tienen caso; evita falsos colapsos).

*Alternativa descartada:* solo constraint DB — perdería el link a ficha existente (US-010 CA-2 exige `pacienteId` en el 409, que el pre-check provee).

### D-03 — Búsqueda por índice existente (`dni` exacto + `apellido` prefijo)

`PacienteRepository`: `findByDni(String)`, `Page<Paciente> findByApellidoStartingWithIgnoreCase(String, Pageable)`, y combinado `findByDniAndApellidoStartingWithIgnoreCase`. Sin `@Query` nativa ni fulltext: el `INDEX(apellido,nombre)` cubre el prefijo (`LIKE 'x%'` usa rango sobre índice, sin `filesort`); la columna `UNIQUE(dni)` cubre el exacto. Paginación Spring `Page.map(PacienteResponse::from)`.

*Alternativa descartada:* especificación/criteria dinámica — sobrediseño para 2 filtros AND en v1.

### D-04 — RBAC por rol + ownership vía `@authz.isPacienteOwner(#id)`

- `GET` lista: `hasAnyRole('SECRETARIA','ADMIN','ODONTOLOGO')` (paciente 403 a lista global — evita enumeración de fichas).
- `POST`: `hasAnyRole('SECRETARIA','ADMIN')`.
- `GET /{id}`, `GET /{id}/turnos`: `hasAnyRole('SECRETARIA','ADMIN','ODONTOLOGO') OR @authz.isPacienteOwner(#id)`.
- `PATCH /{id}`: `hasAnyRole('SECRETARIA','ADMIN') OR @authz.isPacienteOwner(#id)`.
- `isPacienteOwner(id)`: resuelve `CustomUserDetails.id` → `pacienteRepository.findByUsuarioId(currentUserId)` y compara con `#id`. Nuevo método en `AuthzService` con `PacienteRepository` inyectado (constructor injection, sin ciclo: repository no depende de seguridad). ODONTOLOGO lee cualquier ficha (necesita datos del paciente del día — Flujo 3 paso 5); el scoping "solo sus pacientes del día" se aplica en C-09 sobre turnos, no acá.

*Alternativa descartada:* filtrar lista por ownership para PACIENTE — se opta por 403 a lista (más simple, sin filtración por omisión).

### D-05 — IN-01: `usuario_id` validado en servicio, no en FK sola

Al informar `usuario_id`: `usuarioRepository.existsById` (→ 400 si no existe), `pacienteRepository.existsByUsuarioId` excluyendo la propia ficha en PATCH (→ 409 si ya vinculado), y `usuario.rol == PACIENTE` (→ 400 si otro rol). La FK DB garantiza integridad referencial; la unicidad de link y el rol los garantiza el servicio (la DB no conoce roles). Desvinculación con `null` explícito en PATCH.

### D-06 — `V3__paciente.sql` forward-only, tabla nueva, FK nullable

Sigue convención `V{n}__` Flyway, InnoDB `utf8mb4_0900_ai_ci`, `BIGINT UNSIGNED AUTO_INCREMENT`, `DATETIME` defaults, `DATE` para nacimiento, `CHECK` de no-vacío en `dni` (`CHAR_LENGTH(TRIM(dni)) > 0`). Sin `NOT NULL` sin default sobre datos existentes (tabla nueva → sin backfill). Rollback = no-op en v1 (tabla sin consumidores previos; `flyway repair` + drop manual solo en dev).

### D-07 — Historial `GET /{id}/turnos` como stub paginado con contrato congelado

El controller responde `Page.empty(pageable)` hasta C-08 (sin entidad `Turno` aún). Congela ruta, params `page/size`, orden documentado y RBAC — C-08 solo cambia el cuerpo a query real sobre `turno(paciente_id, fecha)`. Evita que C-09/C-11 dependan de una ruta inventada tarde.

### D-08 — DTOs record + `from()` + `@Valid`, `dni` inmutable en PATCH

`CreatePacienteRequest` (todos los campos, `dni` `@NotBlank @Size(max=20)`), `UpdatePacienteRequest` (todos `Optional`/nullable salvo `dni` ausente del record — si el JSON trae `dni` distinto se rechaza 400 explícito en servicio por compatibilidad con clientes que reenvían la ficha completa), `PacienteResponse.from()`. `GlobalExceptionHandler` se extiende con `DuplicateDniException → 409` reutilizando el envelope problem-details existente. Sin exponer entidad JPA.

## Risks / Trade-offs

- [Carrera en alta con mismo DNI] → Mitigación: constraint `UNIQUE(dni)` es red final; pre-check da el 409 con link en el caso común, `catch DataIntegrityViolationException` re-resuelve el `pacienteId` para el link en el caso de carrera.
- [`@authz` con repository inyectado amplía superficie] → Mitigación: solo lectura `findByUsuarioId`; sin escritura; testeado con 403 por rol.
- [Colación `ai_ci` y búsqueda de apellido con tildes] → Mitigación: `ai_ci` colapsa acentos en el prefijo (Góm ≈ Gom) — aceptable y deseable para mostrador; documentado en tests.
- [`fecha_nacimiento` DATE vs DATETIME] → Mitigación: `LocalDate` + columna `DATE`, sin TZ (fecha civil, no instante; RN-GL-02 no aplica a fechas de nacimiento).
- [Stub historial vacío puede confundirse con "sin turnos"] → Mitigación: documentado en spec + task de C-08 lo reemplaza; mismo código 200.

## Migration Plan

1. Aplicar `V3__paciente.sql` via Flyway (`migrate`; `validate` en prod). Tabla nueva → despliegue sin downtime, sin backfill, sin dual-write.
2. Desplegar código (entidad + servicio + controller + handler + authz). Sin flags.
3. Verificación post-deploy: `SELECT COUNT(*) FROM paciente` (=0), `SHOW INDEX FROM paciente` (3 índices), smoke `POST/GET /api/pacientes` con usuario secretaria dev.
4. Rollback: solo código (la tabla vacía sin consumidores es inocua); no se revierte migración en prod (forward-only). En dev: `flyway repair` + `DROP TABLE paciente`.

## Open Questions

Ninguna que cambie specs, enfoque o tasks. Nota diferible: si a futuro el DNI admite letras/prefijos internacionales, se evaluará normalización (`UPPER(TRIM())` + índice funcional) — fuera de v1.
