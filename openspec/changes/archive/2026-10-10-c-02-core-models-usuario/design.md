# Design — C-02 core-models-usuario

## Context

C-01 archivado (2026-10-10, `openspec/changes/archive/2026-10-10-c-01-foundation-setup`) dejó monolito compilable, `HealthController`, `TimeZoneConfig` (`America/Argentina/Buenos_Aires`), `SecurityConfig` con `BCryptPasswordEncoder(12)` y `V1__init.sql` vacío bootstrap, `application.properties` en `validate` y `docker-compose` con MySQL 8.0 `utf8mb4_0900_ai_ci`. `openspec list` vacío implica GATE 1 superado y C-02 único desbloqueado. `CHANGES.md` y `knowledge-base/04 §Usuario`, `05 RN-AU-05/06`, `08 §Patrones` congelan stack **Java Spring Boot 17 / 3.2.5 + Maven + MySQL**; no hay alternativa Python en paralelo. `Usuario` es la raíz del modelo: toda FK `created_by` y `paciente.usuario_id` / `profesional.usuario_id` futuras depende de ella, y su unicidad (`username`, `email`) y `rol ENUM` determinan seguridad y RBAC. Governance CRITICO exige diseño conservador, migración inmutable y tests con DB real.

## Goals / Non-Goals

**Goals:**
- Crear `Usuario` persistible con `username`/`email` únicos, `rol` validado (4 valores), `activo` default true, `password_hash` bcrypt(12), y `created_at/updated_at` auditables, listo para `UserDetailsService` en C-03 sin refactors.
- Proveer `V2__usuario.sql` forward-only, con charset/collation correctos, PK `BIGINT UNSIGNED AUTO_INCREMENT`, índices `UNIQUE(username)`, `UNIQUE(email)`, `INDEX(rol)`, sin `NOT NULL` sin default.
- Dejar `BaseEntity`/`AuditMixin` reutilizable para `Paciente`, `Profesional`, `Turno` y repositorios base (`BaseRepository`, `UsuarioRepository`).
- Seed `admin/admin123` hasheado solo en perfil `dev`, nunca en prod, documentado y no hardcodeado en `V2`.
- Tests de integración con Testcontainers MySQL que prueban creación, unicidad (409 semántica), rol inválido (400), y `activo=false` no autenticable (semántica preparatoria para C-03).

**Non-Goals:**
- Endpoints HTTP (`POST /api/usuarios`, `POST /login`) — son C-03/C-04.
- Lógica de Paciente/Profesional/Especialidad/Turno ni sus tablas — C-05..C-08.
- Multi-rol por usuario (RN-AU-06 fija un único rol en v1) ni jerarquía de permisos — C-03/C-04.
- Soft delete, auditoría `updated_by`, o `UnitOfWork` distribuido — si no aporta, se deja como interface vacía/placeholder.
- Frontend, CI nuevo, o cambios en `platform-foundation` spec.

## Decisions

### D-01 — `BaseEntity` como `@MappedSuperclass` + `AuditingEntityListener` vs `@Embeddable AuditMixin`
- **Decisión:** `BaseEntity` abstracta `@MappedSuperclass` con `id @Id @GeneratedValue(IDENTITY) Long`, `createdAt @Column(name="created_at", nullable=false, updatable=false) LocalDateTime`, `updatedAt @Column(name="updated_at", nullable=false) LocalDateTime`, callbacks `@PrePersist`/`@PreUpdate` o `AuditingEntityListener`. `Usuario` extiende `BaseEntity`. Alternativa `AuditMixin @Embeddable` se evalúa pero se descarta por verbosidad en cada entidad.
- **Rationale:** `springboot-patterns` (capas, DTOs) recomienda superclase para `created_at` y evita repetir columnas; C-08 necesita `created_at` en Turno sin duplicar. `IDENTITY` mapea `AUTO_INCREMENT` MySQL.
- **Trade-off:** Herencia de una tabla por clase vs composición; `MappedSuperclass` no genera tabla propia, ideal para audit fields.

### D-02 — PK `BIGINT UNSIGNED AUTO_INCREMENT` y charset `utf8mb4_0900_ai_ci`
- **Decisión:** `id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY` en SQL; Java `Long` (signed, pero rango suficiente). Tabla `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci` explícito.
- **Rationale:** Compact rule `mysql`: evitar UUID como PK clustered (fragmenta índice), usar `BIGINT UNSIGNED` para OLTP; `utf8mb4` obligatorio para MySQL 8.0; `REPEATABLE READ` default aceptado (no hay contención alta aún, C-08 usará `READ COMMITTED` si contiende).
- **Alternativas:** `UUID` PK — descartado por perf y compatibilidad con FKs numéricas.

### D-03 — `rol` como `ENUM` MySQL + `enum Rol` Java + Bean Validation
- **Decisión:** Columna `rol ENUM('PACIENTE','ODONTOLOGO','SECRETARIA','ADMIN') NOT NULL` en `V2` + Java `public enum Rol { PACIENTE, ODONTOLOGO, SECRETARIA, ADMIN }` con `@Enumerated(EnumType.STRING)` y `@NotNull`. `V2` incluye `CHECK` implícito del ENUM. DTO usa `@NotNull Rol rol`.
- **Rationale:** `knowledge-base/04` y `05 RN-AU-06` congelan 4 roles; ENUM en DB impide valor fuera de lista incluso si se bypasea validación. `mysql` rule: prefer lookup table over ENUM en general, pero para 4 valores estables y auditables el ENUM es aceptable y más simple que join. Se documenta que cambio de roles en v2 requerirá migración `ALTER TABLE ... MODIFY COLUMN rol ENUM(...)` forward-only (expand-contract si crece).
- **Alternativas:** Lookup table `rol(id, nombre)` + FK — más normalizado pero overkill v1; `VARCHAR` + `CHECK` — equivalente pero menos estricto en MySQL <8.0.17.
- **Riesgo:** `ENUM` case-sensitive; se valida `@NotNull` y mensaje 400 si string inválido (`MethodArgumentNotValid`).

### D-04 — Unicidad `username` y `email` con índices `UNIQUE` y handling de `NULL`
- **Decisión:** `username VARCHAR(50) NOT NULL UNIQUE`, `email VARCHAR(120) UNIQUE` (nullable → múltiples NULL permitidos en MySQL, que es deseado porque `knowledge-base/04` marca email como opcional/UNIQUE si presente). Índices `uk_usuario_username`, `uk_usuario_email`. `INDEX idx_usuario_rol (rol)` para filtros por rol (C-04 lista por rol).
- **Rationale:** `CHANGES.md` exige UNIQUE en ambos; `mysql` composite rule: equality-first, pero acá son índices simples. `email` nullable UNIQUE evita colisión de NULLs y respeta RN sin forzar email obligatorio. `username` NOT NULL con `@NotBlank @Size(max=50)` + `UNIQUE` cubre 409.
- **Trade-off:** MySQL permite múltiples NULL en UNIQUE, correcto; si se quisiera email obligatorio se haría `NOT NULL` pero se mantiene nullable para compatibilidad con Paciente sin cuenta (SU-01).
- **Error handling:** Violación se traduce a `DataIntegrityViolationException` → C-04 la mapea a 409 `{"error":"username ya existe"}` via `@ControllerAdvice`; en C-02 solo se testea excepción a nivel repo.

### D-05 — `password_hash VARCHAR(255) NOT NULL` con `BCryptPasswordEncoder(12)`
- **Decisión:** Columna `password_hash VARCHAR(255) NOT NULL` (BCrypt produce ~60 chars, 255 da margen para Argon2 futuro). Bean `PasswordEncoder` ya existe en `SecurityConfig`; `Usuario` no almacena plaintext nunca. Setter `setPassword(String raw)` hashea fuera de entidad (en service de C-04), entidad solo guarda `passwordHash`.
- **Rationale:** `springboot-security` compact rule: `BCryptPasswordEncoder(12)` strength 12; secrets via env, nunca en código. Longitud 255 estándar.
- **Alternativas:** `CHAR(60)` exacto — frágil si cambia algoritmo.

### D-06 — `activo BOOLEAN DEFAULT true` (TINYINT(1)) y semántica `activo=false` no loguea
- **Decisión:** `activo TINYINT(1) NOT NULL DEFAULT 1` (MySQL BOOLEAN alias), Java `Boolean activo` o `boolean activo` con `Builder.Default true` y `@Column(nullable=false)`. `UsuarioRepository.findByUsername(String)` retorna incluso inactivos; la capa `UserDetailsService` (C-03) hará `if (!usuario.isActivo()) throw DisabledException` → 401. En C-02 se testea que `activo=false` persiste y `findByUsername` lo distingue.
- **Rationale:** RN-GL-03 (soft-state) y tests C-02 "activo=false no loguea" (preparación). Default `true` evita `NOT NULL` sin default en tabla con datos (rule `database-migrations`).
- **Alternativas:** Soft delete con `deleted_at` — no en v1.

### D-07 — Migración `V2__usuario.sql` forward-only, sin DML, y seed dev separado
- **Decisión:** `V2__usuario.sql` solo DDL (CREATE TABLE + INDEX). Seed `admin/admin123` en `src/main/resources/db/migration/dev/V2_1__seed_admin.sql` con `spring.flyway.locations=classpath:db/migration,classpath:db/migration/dev` activo solo en `dev`, o en `DevDataInitializer implements ApplicationRunner` con `@Profile("dev")` que hace `if (!repo.existsByUsername("admin")) repo.save(new Usuario("admin", encoder.encode("admin123"), "admin@clinica.local", Rol.ADMIN, true))`. `V2` nunca hardcodea hash en prod path.
- **Rationale:** `database-migrations`: never mix DDL and DML in one migration; `V2` inmutable tras deploy. `VDev` o initializer evita exponer credenciales en historial prod. Hash bcrypt se genera con `BCrypt(12)` al iniciar, no se commitea plaintext más allá de `admin123` en doc dev.
- **Trade-off:** `ApplicationRunner` no es Flyway — se audita menos; se prefiere `VDev` Flyway si `locations` por perfil es soportado, sino runner con log `seed admin created`.
- **Rollback:** Nunca editar `V2`; fix requiere `V3__alter_usuario_fix.sql` (expand-contract si cambia ENUM).

### D-08 — Repositorios: `BaseRepository` genérico + `UsuarioRepository`
- **Decisión:** `BaseRepository<T,ID> extends JpaRepository<T,ID>` (puede ser interface vacía con `@NoRepositoryBean`) + `UsuarioRepository extends BaseRepository<Usuario, Long>` con `Optional<Usuario> findByUsername(String)`, `existsByUsername`, `existsByEmail`. `UnitOfWork` solo si agrega transacciones cruzadas; si no, se omite y se usa `@Transactional` en services (C-04).
- **Rationale:** `springboot-patterns`: Constructor injection, `JpaRepository`, `@Transactional` en service writes. `HikariCP` ya configurado en `application.properties`.
- **Alternativas:** No crear `BaseRepository` — válido, pero se deja placeholder para C-05..C-08 reutilizar.

### D-09 — DTOs y validación Bean Validation
- **Decisión:** `CreateUsuarioRequest(@NotBlank @Size(max=50) username, @NotBlank @Size(min=8) password, @Email @Size(max=120) email, @NotNull Rol rol)` y `UsuarioResponse(id, username, email, rol, activo, createdAt, updatedAt)` con `static UsuarioResponse from(Usuario)`. Validación en DTO, no en entidad, con `@Valid` en controller (C-04). Entidad también lleva `@Column(nullable=false)` como defensa en profundidad.
- **Rationale:** Hard rule "NUNCA exponer entidad JPA directo en API → usar DTOs con @Valid y mapper from()". Mantiene `springboot-security` DTO validation.
- **Nota C-02:** DTOs pueden crearse aquí como preparación aunque endpoints lleguen en C-03/C-04; no rompe nada.

## Risks / Trade-offs

- **CRITICO — Unicidad bajo concurrencia** → Dos `INSERT` simultáneos con mismo `username` pueden ambos pasar `existsBy` check. Mitigación: índice `UNIQUE` en DB es la verdad; capa service (C-04) debe capturar `DataIntegrityViolationException` y mapear a 409. Test de concurrencia se deja para C-04/C-08 pero C-02 ya lo documenta.
- **ENUM rigidez** → Añadir rol en v2 requiere `ALTER TABLE` que puede bloquear. Mitigación: `ALGORITHM=INPLACE` en MySQL 8.0 para ENUM add value es online; documentar expand-contract si se migra a lookup table.
- **Email nullable UNIQUE** → MySQL permite múltiples NULL, pero si se quisiera unicidad case-insensitive, `utf8mb4_0900_ai_ci` ya colaciona case-insensitive ( `Test@X` == `test@x` ). Validar con test que segundo NULL no colisiona y que duplicado con distinta capitalización colisiona (collation).
- **Seed admin en dev** → Riesgo de que `VDev` se ejecute en prod si `locations` mal configurado. Mitigación: `DevDataInitializer @Profile("dev")` + `spring.flyway.locations` condicional + `.env` no commiteado + README advierte `admin123` solo dev.
- **BIGINT signed vs UNSIGNED** → Java `Long` signed, MySQL `UNSIGNED` puede overflow >2^63-1 (improbable). Mitigación: usar `BIGINT` sin `UNSIGNED` si se quiere parity exacta; se elige `BIGINT UNSIGNED` por convention `mysql` pero se acepta `BIGINT` si el equipo prefiere signed para evitar cast.
- **BCrypt strength 12 perf** → 12 es lento (~250ms) pero seguro; tests con Testcontainers deben tolerar. Mitigación: en tests usar `encoder.encode` una vez por suite, no por test, o mock encoder solo para velocidad (pero hard rule exige DB real — se acepta costo).
- **BaseEntity herencia** → Puede acoplar todas las entidades a una superclase. Mitigación: `MappedSuperclass` sin tabla propia minimiza acoplamiento; alternativa `Embeddable` queda documentada para refact si escala.

## Migration Plan

1. **Pre-condición:** C-01 archivado, `mvn verify -DskipTests` pasa, `docker compose up -d mysql` healthy, `V1__init` aplicado (`flyway_schema_history` con `V1`).
2. **Crear rama** `c-02-core-models-usuario` desde `main`; `openspec new change c-02-core-models-usuario` ya ejecutado.
3. **Implementar en orden:** `Rol.java` → `BaseEntity.java` → `Usuario.java` → `BaseRepository.java`/`UsuarioRepository.java` → DTOs → `V2__usuario.sql` → seed dev (initializer o `VDev`) → `UsuarioRepositoryTest` Testcontainers.
4. **Verificación local:** `mvn flyway:migrate -Dflyway.locations=...` aplica `V2`; `mvn test -Dtest=UsuarioRepositoryTest` verde; `docker exec mysql mysql -e "SHOW CREATE TABLE usuario\G"` verifica charset, índices, ENUM, defaults; `SELECT * FROM usuario` tras seed dev muestra `admin` con hash `$2a$12$...`.
5. **CI:** `backend-test` job debe pasar con `mysql:8.0` service y Testcontainers; `lint` sin cambios.
6. **Rollback:** Nunca editar `V2` deployada. Si `V2` falla en prod, crear `V3__fix_usuario.sql` (ADD COLUMN nullable o MODIFY ENUM) y `flyway repair` si checksum. Down no se soporta (forward-only); documentar en `README §Migrations`.
7. **Deploy dev:** `docker compose up --build` + `SPRING_PROFILES_ACTIVE=dev` → admin creado; `curl` no aplica (no endpoint aún), pero `psql/mysql -e "SELECT username, rol, activo FROM usuario"` valida.
8. **Archivado:** `openspec archive c-02-core-models-usuario` mueve spec `user-accounts` a `openspec/specs/user-accounts/spec.md` y marca C-02 `[x]` en `CHANGES.md`.

## Open Questions

- ¿Confirmar `BIGINT UNSIGNED` vs `BIGINT` signed para PK? `mysql` skill recomienda UNSIGNED pero Java `Long` es signed — equipo decide y se congela en `V2` (cambiar después requiere `ALTER`).
- ¿Seed `admin` via Flyway `VDev` o `ApplicationRunner @Profile("dev")`? Ambos válidos; se elige runner si `spring.flyway.locations` por perfil complica CI. Decisión se toma en implementación y se documenta en `README`.
- ¿`email` debe ser `NOT NULL` + `UNIQUE` o permanecer nullable `UNIQUE`? `04` dice `VARCHAR(120) UNIQUE` sin `NOT NULL` explícito y SU-01 sugiere paciente sin cuenta puede no tener email; se mantiene nullable. Si prod exige email obligatorio, C-04 hará `ALTER` con default.
- ¿`BaseRepository` y `UnitOfWork` son necesarios en v1 o se introducen solo si C-05 los reutiliza? Se crean como placeholders `@NoRepositoryBean` sin lógica; si el equipo los ve overkill, se dejan vacíos y se documenta como No-Go si no aporta.
