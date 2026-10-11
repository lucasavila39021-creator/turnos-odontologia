# Tasks — C-02 core-models-usuario

## 1. Modelo de dominio `BaseEntity` y `Rol`

- [x] 1.1 Crear `src/main/java/com/clinica/turnos/domain/model/BaseEntity.java` como `@MappedSuperclass` con `id @Id @GeneratedValue(strategy=IDENTITY) Long`, `createdAt @Column(name="created_at", nullable=false, updatable=false) LocalDateTime`, `updatedAt @Column(name="updated_at", nullable=false) LocalDateTime`, `@PrePersist` set `now` y `@PreUpdate` update `updatedAt`, y verificar `javap` muestra callbacks
- [x] 1.2 Crear `src/main/java/com/clinica/turnos/domain/model/Rol.java` enum `PACIENTE, ODONTOLOGO, SECRETARIA, ADMIN` y verificar `Rol.valueOf("ADMIN")` no falla y `valueOf("INVALID")` lanza `IllegalArgumentException` (para 400 futuro)
- [x] 1.3 Revisar que `BaseEntity` usa `LocalDateTime` (no `Instant`) coherente con `DATETIME` MySQL y `TimeZoneConfig America/Argentina/Buenos_Aires` (RN-GL-02), y que `application.properties` ya fija `serverTimezone` — verificar con `grep serverTimezone application.properties`

## 2. Entidad `Usuario`

- [x] 2.1 Crear `src/main/java/com/clinica/turnos/domain/model/Usuario.java` `@Entity @Table(name="usuario", uniqueConstraints=@UniqueConstraint(columnNames="username"), @UniqueConstraint(columnNames="email"))` extend `BaseEntity`, columnas: `username VARCHAR(50) NOT NULL`, `passwordHash VARCHAR(255) NOT NULL` (`@Column(name="password_hash")`), `email VARCHAR(120)` nullable, `rol @Enumerated(EnumType.STRING) @Column(nullable=false) Rol`, `activo @Column(nullable=false) Boolean` default `true` (constructor o `@Builder.Default`), y verificar que no expone plaintext ni `@Data` con `password` en `toString` (usar `@ToString.Exclude` o manual)
- [x] 2.2 Añadir validaciones JPA/Bean Validation en entidad: `@NotBlank @Size(max=50)` username, `@NotBlank` passwordHash (hash, no raw), `@Email` email si no null, `@NotNull` rol, y verificar con `ValidatorFactory` que entidad con `rol=null` falla validación
- [x] 2.3 Asegurar constructor injection ready: entidad con `@NoArgsConstructor(access=PROTECTED)` para JPA + `@AllArgsConstructor` o builder, sin field injection, y verificar `Usuario` no usa `@Autowired`

## 3. Repositorios

- [x] 3.1 Crear `src/main/java/com/clinica/turnos/domain/repository/BaseRepository.java` `@NoRepositoryBean interface BaseRepository<T,ID> extends JpaRepository<T,ID>` (placeholder para C-05..C-08) y verificar que compila y no genera bean concreto
- [x] 3.2 Crear `src/main/java/com/clinica/turnos/domain/repository/UsuarioRepository.java` `interface UsuarioRepository extends BaseRepository<Usuario,Long>` con `Optional<Usuario> findByUsername(String username)`, `boolean existsByUsername(String)`, `boolean existsByEmail(String)`, `Optional<Usuario> findByEmail(String)` y verificar que Spring Data deriva queries (sin `@Query` custom aún)
- [x] 3.3 (Opcional) Crear `src/main/java/com/clinica/turnos/domain/repository/UnitOfWork.java` facade si el equipo lo exige; sino documentar en `design.md` que se difiere a C-08 — verificar decisión con tech lead y dejar `// TODO C-08` si se omite

## 4. DTOs y validación (preparación C-04)

- [x] 4.1 Crear `src/main/java/com/clinica/turnos/application/dto/CreateUsuarioRequest.java` record/class con `@NotBlank @Size(max=50) username`, `@NotBlank @Size(min=8, max=100) password`, `@Email @Size(max=120) email` (nullable), `@NotNull Rol rol`, `@NotNull Boolean activo` o default, y `from()` no aplica (es request) — verificar con `Validator` que `password="short"` falla
- [x] 4.2 Crear `src/main/java/com/clinica/turnos/application/dto/UsuarioResponse.java` record con `Long id, String username, String email, Rol rol, Boolean activo, LocalDateTime createdAt, updatedAt` + `static UsuarioResponse from(Usuario u)` mapper que nunca expone `passwordHash`, y verificar `from()` no incluye hash en JSON serializado (`@JsonIgnore` en entidad)
- [x] 4.3 Verificar que `application.properties` tiene `spring.mvc.problemdetails.enabled=true` (ya en C-01) para futuro mapeo 400/409 via `@ControllerAdvice` — `grep problemdetails application.properties`

## 5. Migración `V2__usuario.sql`

- [x] 5.1 Crear `src/main/resources/db/migration/V2__usuario.sql` con `CREATE TABLE usuario (id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, username VARCHAR(50) NOT NULL, password_hash VARCHAR(255) NOT NULL, email VARCHAR(120) NULL, rol ENUM('PACIENTE','ODONTOLOGO','SECRETARIA','ADMIN') NOT NULL, activo TINYINT(1) NOT NULL DEFAULT 1, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, UNIQUE KEY uk_usuario_username (username), UNIQUE KEY uk_usuario_email (email), INDEX idx_usuario_rol (rol)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;` y verificar `cat V2__usuario.sql` contiene `utf8mb4`, `ENUM`, `UNIQUE`, `INDEX(rol)` y no tiene `NOT NULL` sin `DEFAULT` en columnas nuevas (tabla nueva, safe; regra `database-migrations`)
- [x] 5.2 Ejecutar `mvn flyway:info` y `mvn flyway:migrate` local con `docker compose up -d mysql` y verificar `V2` aparece como `Success` y `SHOW CREATE TABLE usuario\G` muestra charset, `AUTO_INCREMENT`, y `ON UPDATE CURRENT_TIMESTAMP`
- [x] 5.3 Verificar forward-only: `git log --follow -- src/main/resources/db/migration/V1__init.sql` no muestra edición de `V1` en este change y `V2` es archivo nuevo — `git status` debe listar `V2__usuario.sql` como untracked/new

## 6. Seed dev-only `admin/admin123`

- [x] 6.1 Elegir e implementar una de: (a) `src/main/resources/db/migration/dev/V2_1__seed_admin.sql` con `INSERT INTO usuario (username,password_hash,email,rol) VALUES ('admin','$2a$12$<hash>', 'admin@clinica.local','ADMIN') ON DUPLICATE KEY UPDATE ...` + `spring.flyway.locations` condicional, o (b) `src/main/java/com/clinica/turnos/config/DevDataInitializer.java` `@Component @Profile("dev") implements ApplicationRunner` que inyecta `UsuarioRepository` y `PasswordEncoder` (constructor injection) y hace `if (!repo.existsByUsername("admin")) repo.save(new Usuario("admin", encoder.encode("admin123"), "admin@clinica.local", Rol.ADMIN, true))` con log `log.info("seed admin created")`
- [x] 6.2 Verificar que seed no corre en prod: `grep -r "admin123" src/main/resources/db/migration/V2__usuario.sql` debe dar 0; seed solo en `dev/` o clase `@Profile("dev")`; `grep -r "Profile.*prod" DevDataInitializer` no debe existir; `SPRING_PROFILES_ACTIVE=prod mvn spring-boot:run --dry-run` no loggea seed
- [x] 6.3 Generar hash bcrypt(12) para `admin123` con `PasswordEncoder` bean o `htpasswd -B` y verificar que `BCryptPasswordEncoder(12).matches("admin123", hash)` es `true` y que hash inicia con `$2a$12$` y longitud ~60 (no 32 md5)

## 7. Tests de integración (Testcontainers MySQL, DB real)

- [x] 7.1 Crear `src/test/java/com/clinica/turnos/domain/repository/UsuarioRepositoryTest.java` con `@DataJpaTest` o `@SpringBootTest` + `@Testcontainers @Container MySQLContainer("mysql:8.0")` + `@DynamicPropertySource` para `spring.datasource.url`, y verificar que clase usa `MySQLContainer` no `H2` para tests de unicidad (hard rule: DB real, no mocks)
- [x] 7.2 Test creación ok: `Usuario u = new Usuario("juan","$2a$12$hash","juan@x.com",Rol.PACIENTE,true); repo.save(u); assertThat(repo.findByUsername("juan")).isPresent()` y verificar `createdAt/updatedAt` no nulos tras `save`
- [x] 7.3 Test unicidad username 409 semántica: `repo.save(new Usuario("dup","hash1","a@x.com",Rol.ADMIN,true)); assertThatThrownBy(() -> repo.saveAndFlush(new Usuario("dup","hash2","b@x.com",Rol.PACIENTE,true))).isInstanceOf(DataIntegrityViolationException.class)` con mensaje contiene `uk_usuario_username` o `Duplicate entry`
- [x] 7.4 Test unicidad email 409: `repo.save(new Usuario("u1","h","same@x.com",Rol.SECRETARIA,true)); assertThatThrownBy(() -> repo.saveAndFlush(new Usuario("u2","h","same@x.com",Rol.ODONTOLOGO,true))).isInstanceOf(DataIntegrityViolationException.class)` — y test que dos `email=null` no colisionan (MySQL permite múltiple NULL en UNIQUE)
- [x] 7.5 Test rol inválido 400: intentar persistir `rol=null` → `ConstraintViolationException` o `DataIntegrityViolationException` por `NOT NULL`; y test a nivel DTO `validator.validate(new CreateUsuarioRequest("u","short","bad-email",null))` falla con `rol must not be null` y `password size`
- [x] 7.6 Test `activo=false` no loguea (semántica): `Usuario inact = repo.save(new Usuario("inact","hash","i@x.com",Rol.PACIENTE,false)); assertThat(repo.findByUsername("inact").get().getActivo()).isFalse()` y documentar que `UserDetailsService` (C-03) debe rechazarlo con `DisabledException` → 401; test no debe crear endpoint aún
- [x] 7.7 Test colación `utf8mb4_0900_ai_ci`: `repo.save(new Usuario("user1","h","Test@Example.com",Rol.ADMIN,true)); assertThatThrownBy(() -> repo.saveAndFlush(new Usuario("user2","h","test@example.com",Rol.ADMIN,true))).isInstanceOf(DataIntegrityViolationException.class)` si collation es case-insensitive (documentar si MySQL la respeta)

## 8. Verificación integrada y governance CRITICO

- [x] 8.1 Ejecutar `mvn -B verify` (o `mvn test`) con Testcontainers y verificar todo verde: `Tests run: X, Failures: 0, Errors: 0`; `docker exec turnos-mysql mysql -u turnos_user -pchangeme -e "SELECT table_name, engine, table_collation FROM information_schema.tables WHERE table_name='usuario'"` → `InnoDB` + `utf8mb4_0900_ai_ci`
- [x] 8.2 Verificar índices: `SHOW CREATE TABLE usuario\G` contiene `UNIQUE KEY uk_usuario_username`, `UNIQUE KEY uk_usuario_email`, `KEY idx_usuario_rol (rol)` y `SELECT COUNT(*) FROM information_schema.statistics WHERE table_name='usuario'` ≥3
- [x] 8.3 Validar spec: `openspec validate c-02-core-models-usuario --strict` sin errores (cada Requirement con ≥1 Scenario `####`), y `openspec status --change c-02-core-models-usuario --json` reporta `proposal/design/tasks/specs` presentes
- [x] 8.4 Revisión CRITICO: asignar reviewer que verifique unicidad, `V2` forward-only, `BCrypt(12)` no plaintext, `activo` default, `email` nullable UNIQUE, y que `.env` no contiene `admin123` real — checklist en PR description con links a `knowledge-base/04` y `05 RN-AU-05/06`
- [x] 8.5 Preparar `git status/diff` (sin commit — hard rule NUNCA commitear sin pedido) y documentar en PR que C-02 desbloquea C-03 (`auth-login-rbac`) y es camino crítico — actualizar `CHANGES.md` C-02 a `[x]` solo tras `openspec archive`
