# user-accounts Specification

## Purpose
Provee la identidad base del sistema — entidad Usuario persistida con unicidad, rol único y timestamps — para que autenticación, RBAC y auditoría tengan sujeto válido y C-03 pueda implementar login sin refactors.

## Requirements

### Requirement: Persistencia de Usuario con BaseEntity auditable
El sistema SHALL persistir `Usuario` con `id BIGINT PK AUTO_INCREMENT`, `username VARCHAR(50) NOT NULL`, `password_hash VARCHAR(255) NOT NULL`, `email VARCHAR(120)` nullable, `rol ENUM(PACIENTE,ODONTOLOGO,SECRETARIA,ADMIN) NOT NULL`, `activo BOOLEAN DEFAULT true` y `created_at/updated_at` via `BaseEntity @MappedSuperclass` con tabla InnoDB utf8mb4 y migración `V2__usuario.sql` forward-only.

#### Scenario: Crear usuario persiste con timestamps
- **WHEN** se guarda `Usuario(username="ana", passwordHash="$2a$12$...", email="ana@clinica.local", rol=PACIENTE, activo=true)` vía `UsuarioRepository.save()`
- **THEN** el sistema asigna `id` no nulo, `created_at` y `updated_at` no nulos y `SELECT * FROM usuario WHERE username='ana'` retorna la fila

#### Scenario: Update actualiza updated_at sin tocar created_at
- **WHEN** se modifica `usuario.email` y se hace `save()`/`flush()`
- **THEN** `updated_at` es posterior a `created_at` y `created_at` permanece igual al valor de creación

### Requirement: Unicidad de username y email con índices UNIQUE
El sistema SHALL garantizar `UNIQUE(username)` y `UNIQUE(email)` donde `email` nullable permite múltiples `NULL` pero colisiona si valor no nulo duplicado (case-insensitive por `utf8mb4_0900_ai_ci`), con índices `uk_usuario_username`, `uk_usuario_email` y `INDEX idx_usuario_rol(rol)`, y traducir violación a semántica 409 (DataIntegrityViolation) a nivel repository (mapeo a 409 HTTP en C-04).

#### Scenario: Username duplicado viola unicidad
- **WHEN** existe `usuario(username="dup")` y se intenta `INSERT` con `username="dup"` y distinto `email`
- **THEN** la BD rechaza con `Duplicate entry 'dup' for key 'uk_usuario_username'` y el repository lanza `DataIntegrityViolationException`

#### Scenario: Email duplicado case-insensitive viola unicidad
- **WHEN** existe `usuario(email="Test@Example.com")` y se intenta guardar `email="test@example.com"`
- **THEN** la BD rechaza por `uk_usuario_email` (collation `0900_ai_ci` case-insensitive) con `DataIntegrityViolationException`

#### Scenario: Dos usuarios con email NULL no colisionan
- **WHEN** se guardan dos usuarios con `email=NULL` y `username` distintos
- **THEN** ambos persisten sin violación (MySQL UNIQUE permite múltiples NULL)

### Requirement: Validación de rol único ENUM (RN-AU-06)
El sistema SHALL validar que `rol` es uno de `PACIENTE, ODONTOLOGO, SECRETARIA, ADMIN`, no nulo, mapeado como `ENUM` en MySQL y `enum Rol` Java con `@Enumerated(STRING)` + Bean Validation `@NotNull`, rechazando valor inválido o nulo con 400 (ConstraintViolation) y garantizando un único rol por usuario en v1 (RN-AU-06).

#### Scenario: Rol nulo falla validación
- **WHEN** se intenta persistir `Usuario` con `rol=null` o DTO con `rol=null`
- **THEN** Bean Validation falla con `rol must not be null` o la BD rechaza `NOT NULL` con `DataIntegrityViolationException` y el sistema responde semántica 400

#### Scenario: Rol inválido fuera de ENUM falla
- **WHEN** se envía `rol="INVALID"` o `Rol.valueOf("INVALID")`
- **THEN** deserialización/validación falla con 400 `rol inválido` y no se persiste fila (no se acepta string arbitrario)

#### Scenario: Cada usuario tiene exactamente un rol
- **WHEN** se consulta `usuario.rol` de cualquier fila
- **THEN** el valor es uno de los 4 enum y nunca nulo ni multi-valor (un rol por fila, sin tabla intermedia)

### Requirement: Almacenamiento de password con BCrypt(12) y nunca plaintext
El sistema SHALL almacenar `password_hash` con `BCryptPasswordEncoder(12)` (hash ~60 chars, columna `VARCHAR(255)`), nunca plaintext, con `BCrypt.matches(raw, hash)` verificable y sin exponer `passwordHash` en DTOs `UsuarioResponse`/`from()` ni logs.

#### Scenario: Password se hashea con strength 12
- **WHEN** se crea usuario con `password="admin123"` via `encoder.encode("admin123")` y se persiste `password_hash`
- **THEN** `password_hash` inicia con `$2a$12$`, longitud >=59, y `encoder.matches("admin123", hash)` es `true` mientras `matches("wrong", hash)` es `false`

#### Scenario: UsuarioResponse no expone hash
- **WHEN** se serializa `UsuarioResponse.from(usuario)` a JSON
- **THEN** el JSON contiene `id, username, email, rol, activo, createdAt, updatedAt` y no contiene `password_hash` ni `password`

### Requirement: Flag activo y semántica de deshabilitación (RN-GL-03)
El sistema SHALL persistir `activo TINYINT(1) NOT NULL DEFAULT 1` y garantizar que `activo=false` se persiste y distingue via `findByUsername`, quedando listo para que C-03 (`UserDetailsService`) rechace login con 401 `cuenta deshabilitada` (no se autentica usuario inactivo).

#### Scenario: Usuario inactivo persiste y es consultable
- **WHEN** se guarda `Usuario(username="inact", activo=false)` y se hace `findByUsername("inact")`
- **THEN** el sistema retorna la entidad con `activo=false` y `isActivo()==false`

#### Scenario: Usuario inactivo no debe autenticar (preparación C-03)
- **WHEN** `UserDetailsService` futuro carga `username="inact"` con `activo=false`
- **THEN** debe lanzar `DisabledException` y el flujo de login responderá 401 (este change solo garantiza que `activo` es persistido y consultable para esa decisión)

### Requirement: Seed dev-only admin/admin123 hasheado
El sistema SHALL proveer seed de desarrollo con usuario `admin` / `admin123` hasheado con `BCrypt(12)`, ejecutado solo con perfil `dev` (Flyway `dev/V2_1__seed_admin.sql` o `ApplicationRunner @Profile("dev")`), sin insertar en `V2__usuario.sql` ni en prod, y con hash no reversible.

#### Scenario: Seed admin existe en dev y valida password
- **WHEN** la app arranca con `SPRING_PROFILES_ACTIVE=dev` y se consulta `usuario WHERE username='admin'`
- **THEN** existe una fila `username='admin', rol='ADMIN', activo=1` y `encoder.matches("admin123", password_hash)` es `true`

#### Scenario: Seed no corre en prod
- **WHEN** la app arranca con `SPRING_PROFILES_ACTIVE=prod` sobre BD vacía migrada solo con `V1`+`V2`
- **THEN** `SELECT COUNT(*) FROM usuario WHERE username='admin'` es 0 (no se inserta admin en prod)
