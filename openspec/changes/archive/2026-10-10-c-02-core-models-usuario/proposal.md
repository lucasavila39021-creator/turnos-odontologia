# Proposal — C-02 core-models-usuario

## Why

C-01 entregó scaffolding, `GET /api/health`, MySQL + Flyway `V1__init` y `SecurityConfig` base, pero el sistema aún no tiene identidad: no existe tabla ni entidad `Usuario`, sin la cual C-03 (`auth-login-rbac`) no puede validar credenciales, RBAC (RN-AU-01..06) no tiene sujeto, y el resto del roadmap (C-04..C-12) queda bloqueado. `CHANGES.md` marca C-02 como **GATE 1** único desbloqueado tras C-01 y camino crítico; governance **CRITICO** porque la tabla `usuario` concentra unicidad (`username`, `email`), `rol` ENUM con 4 valores, hash bcrypt y flag `activo` que condiciona todo login futuro. Sin este change cualquier intento de autenticar o auditar `created_by` es imposible.

KB `04 §Usuario` y `05 RN-AU-05/06` ya congelan el contrato: `id BIGINT PK AUTO_INCREMENT`, `username VARCHAR(50) UNIQUE`, `password_hash VARCHAR(255) bcrypt`, `email VARCHAR(120) UNIQUE`, `rol ENUM('PACIENTE','ODONTOLOGO','SECRETARIA','ADMIN')`, `activo BOOLEAN DEFAULT true`, `created_at/updated_at`. Postergarlo no ahorra riesgo: fallar unicidad o rol ahora propaga 409/400 mal tipados y brechas de seguridad a todos los changes siguientes. Se ejecuta ahora, aislado y testeado con Testcontainers, para dejar una base versionada (`V2__usuario.sql`) forward-only e inmutable.

## What Changes

- **Dominio `Usuario`**: entidad JPA `com.clinica.turnos.domain.model.Usuario` + `BaseEntity`/`AuditMixin` (`@MappedSuperclass` con `id BIGINT AUTO_INCREMENT`, `createdAt`, `updatedAt` via `@PrePersist/@PreUpdate` o `AuditingEntityListener`, y placeholder `createdBy` nullable para C-08). `Usuario` incluye `username`, `passwordHash`, `email`, `rol` (enum Java `Rol {PACIENTE,ODONTOLOGO,SECRETARIA,ADMIN}` mapeado a `ENUM`/`VARCHAR` con `CHECK`), `activo` default `true`.
- **Persistencia**: `UsuarioRepository extends JpaRepository<Usuario,Long>` + `BaseRepository<T,ID>` genérico (si aporta) y `existsByUsername`/`existsByEmail`/`findByUsername` para unicidad y login futuro. `UnitOfWork` si aplica como facade transaccional.
- **Migración `V2__usuario.sql`** (Flyway, forward-only, inmutable tras deploy): `CREATE TABLE usuario (id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, username VARCHAR(50) NOT NULL, password_hash VARCHAR(255) NOT NULL, email VARCHAR(120), rol ENUM('PACIENTE','ODONTOLOGO','SECRETARIA','ADMIN') NOT NULL, activo TINYINT(1) NOT NULL DEFAULT 1, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;` + `UNIQUE KEY uk_usuario_username (username)`, `UNIQUE KEY uk_usuario_email (email)`, `INDEX idx_usuario_rol (rol)`. Valida regla `database-migrations`: sin `NOT NULL` sin default en tabla con datos (tabla nueva, safe) y nunca mixta DDL+DML.
- **Seed dev-only**: `V2` no inserta admin en prod; `src/main/resources/db/migration/dev/VDev__seed_admin.sql` o `ApplicationRunner` con perfil `dev` inserta `admin / admin123` hasheado con `BCrypt(12)` (hash precomputado, no plaintext). Prod no recibe seed. Alternativa: `data.sql` con `spring.sql.init.mode=never` en prod.
- **Validación y DTOs** (preparación para C-04/C-03): `CreateUsuarioRequest`/`UsuarioResponse` con `@NotBlank @Size(max=50)`, `@Email`, `@NotNull Rol`, Bean Validation, `from(Usuario)` mapper; unicidad se traduce a 409 en capa service/controller (C-04) pero ya se prueba a nivel repository como `DataIntegrityViolationException`.
- **Tests**: integración con Testcontainers MySQL (no mocks, hard rule) — creación ok, duplicado `username` → violación unicidad, duplicado `email` → violación, `rol` inválido → `ConstraintViolation`/400, `activo=false` persistido y verificable que `findByUsername` lo retorna pero servicio futuro lo rechaza (semántica `activo`).
- **No cambia**: rutas HTTP nuevas (login/CRUD es C-03/C-04), lógica de Paciente/Profesional/Turno, frontend.

## Capabilities

### New Capabilities
- `user-accounts`: Modelo base de identidad del sistema — entidad Usuario, persistencia con unicidad y rol, timestamps auditables, hashing bcrypt y seed dev. Es el contrato observable de que el sistema distingue usuarios, hace cumplir unicidad y valida rol único (RN-AU-05/06) de forma reproducible.

### Modified Capabilities
- Ninguna — `platform-foundation` queda intacta (su `V1__init` no se edita; `SecurityConfig` y `application.properties` solo ganan datasource ya existente; no hay cambios de requisitos en specs publicadas).

## Impact

- **Código nuevo**: `domain/model/BaseEntity.java`, `domain/model/Usuario.java`, `domain/model/Rol.java`, `domain/repository/BaseRepository.java`, `domain/repository/UsuarioRepository.java`, `application/dto/CreateUsuarioRequest.java`, `application/dto/UsuarioResponse.java` (opcional), `db/migration/V2__usuario.sql`, seed dev (`VDev__` o `DevSeedConfig.java`).
- **Código modificado**: `SecurityConfig` ya tiene `BCryptPasswordEncoder(12)` — se reutiliza, no se cambia; `application.properties` sin cambios (datasource `validate` cubre nueva tabla).
- **APIs**: ninguna nueva en este change (endpoints vienen en C-03/C-04); a nivel DB expone tabla `usuario`.
- **Dependencias**: Flyway, Spring Data JPA, BCrypt, Testcontainers MySQL (ya en `pom.xml` 3.2.5 / 1.19.8) — sin nuevas deps.
- **Sistemas**: Flyway valida `V2` en arranque; CI `backend-test` debe correr `UsuarioRepositoryTest` con contenedor MySQL 8.0.
- **Riesgos si se omite**: C-03 no puede implementar `UserDetailsService`, RBAC sin sujeto, y cualquier tabla hija con `created_by FK → usuario.id` (Turno C-08) falla por FK inexistente.
- **Breaking**: ninguno para consumidores (no hay API); breaking interno si `V2` se edita tras deploy — mitigado con forward-only.
- **Governance CRITICO**: requiere revisión de unicidad, índices y hash antes de merge; GATE 1 bloquea todo el roadmap si falla.
