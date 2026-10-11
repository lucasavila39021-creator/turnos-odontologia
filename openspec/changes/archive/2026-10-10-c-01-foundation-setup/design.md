# Design — C-01 foundation-setup

## Context

Repo greenfield: `openspec list` vacío, `openspec/specs` sin capacidades, git sin commits. `CHANGES.md` define C-01 como primer change sin dependencias, governance BAJO, scope scaffolding + infra base. Debe desbloquear C-02 (`core-models-usuario`). Estado influye: P0-scale team (<50 usuarios, 40 turnos/día) y P5:b mantenibilidad exigen monolito modular simple, sin caché/cola/balanceador en v1. KB relevante: `08 §Estructura de directorios/§Variables de entorno`, `02 §Stack`, `09 DD-01/DD-02`. Ver `proposal.md` Why para motivación.

## Goals / Non-Goals

**Goals:**
- Proveer scaffolding compilable/ejecutable con `docker compose up` y `GET /api/health` 200 en <2 min.
- Fijar variante de stack (A Java o B Python) y estructura de directorios antes de C-02.
- Dejar migrations, env vars y zona horaria `America/Argentina/Buenos_Aires` inamovibles para reglas duras RN-GL-02.
- CI mínimo que falle si health o migrations rompen.

**Non-Goals:**
- Lógica de dominio (Usuario, Paciente, Turno) — eso es C-02..C-08.
- Auth real/login/RBAC — C-03.
- Seed de dominio o backups automatizados — C-12.
- Optimización de queries/índices de Turno — C-08.
- Elección SSR vs SPA definitiva más allá de preparar CSRF/CORS base.

## Decisions

### D-01 — Variante de stack: congelar una, documentar la otra como alternativa
- **Decisión:** Implementar Variante A (Java Spring Boot 17/3.x + Maven) como default si el equipo no declara Python; documentar en `08` y `README.md` la variante elegida y dejar `decision` en `config.yaml`/`application.properties`. Si el equipo es Python, scaffoldear Variante B (Django 5.x + `requirements.txt`) con árbol equivalente.
- **Rationale:** DD-01/DD-02 exige elegir una y no mantener dos en paralelo; Spring Boot alinea con skills instaladas (`springboot-patterns`, `springboot-security`, `database-migrations`, `mysql`) y testing Testcontainers.
- **Alternativas:** Scaffoldear ambas y abstraer — descartado: duplica mantenimiento y confunde gates.

### D-02 — MySQL 8.0 en docker-compose con volumen y healthcheck
- **Decisión:** `docker-compose.yml` servicios `app` + `mysql` (imagen `mysql:8.0`, `utf8mb4_0900_ai_ci`, InnoDB), volumen `mysql_data`, env `MYSQL_DATABASE/USER/PASSWORD` desde `.env`, `healthcheck: mysqladmin ping`, `depends_on: mysql: condition: service_healthy`, `wait-for-db` script/entrypoint si app arranca antes.
- **Rationale:** Cumple P4 relacional + ACID para RN-TU-01 sin infra externa; reproduce prod local.
- **Alternativas:** H2/SQLite dev — descartado: diverge de MySQL 8.0 y rompe Flyway/Testcontainers parity.

### D-03 — Migrations infra: Flyway V1 bootstrap (Java) o Django migrations init
- **Decisión:** Java: Flyway `V1__init.sql` vacío o con `CREATE TABLE flyway_schema_history` bootstrap + `spring.flyway.enabled=true`, `spring.jpa.hibernate.ddl-auto=validate` (dev y prod). Python: `apps/*/migrations/__init__.py` + `manage.py migrate` inicial sin tablas. Regla forward-only: nunca editar migración deployada; nueva columna → `V{n}__` nullable o con default (compact rule `database-migrations`).
- **Rationale:** Evita drift y `update` en prod; deja rastro auditable para C-02..C-12.
- **Alternativas:** `ddl-auto: update` — descartado: no versionado, riesgo en prod.

### D-04 — Health endpoint sin auth
- **Decisión:** `HealthController` → `GET /api/health` `200 {"status":"ok"}` (o `{"status":"UP"}` con Spring Actuator si se añade), `permitAll()` en `SecurityFilterChain`, sin DTO complejo. Respuesta `ResponseEntity<Map<String,String>>`.
- **Rationale:** Liveness para CI y `docker-compose` healthcheck; debe ser público por `RN-AU-01` (rutas públicas listadas).
- **Alternativas:** Actuator `/actuator/health` solo — descartado: prefix `/api/health` es contrato CHANGES.md.

### D-05 — Env vars y zona horaria fija
- **Decisión:** `.env.example` versionado + `.env` gitignored. `application.properties` usa `${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:turnos_db}` etc. `TimeZoneConfig` hace `TimeZone.setDefault(TimeZone.getTimeZone("America/Argentina/Buenos_Aires"))` en `@PostConstruct` (Java) o `TIME_ZONE="America/Argentina/Buenos_Aires"` + `USE_TZ=True` (Django). `APP_TURNO_DURACION_MIN`, `APP_HORARIO_DESDE/HASTA`, `PORT` inyectados con defaults.
- **Rationale:** Regla dura "NUNCA hardcodear secrets" + RN-GL-02 (DATETIME sin conversión implícita).
- **Alternativas:** `application.yml` con perfiles — válido pero `properties` es mínimo para C-01.

### D-06 — Seguridad base mínima
- **Decisión:** `SecurityConfig` con `@EnableWebSecurity`, `SecurityFilterChain` que `authorizeHttpRequests: permitAll` para `GET /api/health`, `GET /`, `GET /login`, `POST /login`; resto `authenticated` (preparado para C-03). `BCryptPasswordEncoder(12)` bean, CSRF habilitado si SSR (Thymeleaf) y deshabilitado + `STATELESS` si API Bearer (documentar elección), CORS configurado en chain (no `*`).
- **Rationale:** Compact rule `springboot-security`: deny by default, least privilege; deja C-03 sin reescribir chain.
- **Alternativas:** Seguridad completa RBAC ya en C-01 — descartado: governance BAJO, RBAC es C-03.

### D-07 — CI GitHub Actions
- **Decisión:** `.github/workflows/ci.yml` jobs paralelos `backend-test` (`mvn -B verify` o `pytest`) + `lint` (checkstyle/spotless o flake8/black), cache `actions/setup-java@v4` + `cache: maven` / `setup-python`, servicio `mysql:8.0` o Testcontainers si repo es Java. Fail fast si health o migrations fallan.
- **Rationale:** Feedback <5 min, paralelizado.
- **Alternativas:** Jenkins/self-hosted — descartado: universitario, GitHub nativo.

### D-08 — .gitignore y README
- **Decisión:** `.gitignore` ignora `.env`, `target/`, `*.class`, `.idea/`, `__pycache__/`, `venv/`, `node_modules/` si aplica. `README.md` con stack elegido, `docker compose up --build`, `curl localhost:8080/api/health`, `mysqldump` backup snippet, env vars table, link a `knowledge-base/` y `CHANGES.md`.

## Risks / Trade-offs

- **Variante elegida prematura** → Si equipo cambia de Java↔Python tras C-01, hay rework. Mitigación: decidir y congelar en `08` + `config.yaml` en C-01 review; documentar trade-offs DD-01.
- **MySQL volumen local vs CI efímero** → CI no persiste volumen; tests deben usar Testcontainers o servicio efímero. Mitigación: configurar `spring.datasource.url` con `${DB_HOST}` y fallback a Testcontainers en tests.
- **Flyway V1 vacío confunde** → Algunos equipos esperan `V1__usuario`. Mitigación: comentar en `V1__init.sql` que es bootstrap y que `V2` será Usuario en C-02.
- **Zona horaria JVM vs MySQL** → Desfase si `serverTimezone` mal. Mitigación: fijar `serverTimezone=America/Argentina/Buenos_Aires` en JDBC URL y validar con test que `SELECT NOW()` respeta TZ.
- **Secrets en .env.example** → Fuga si se comitean valores reales. Mitigación: `.env.example` con placeholders `changeme`, `.env` ignorado, CI inyecta `DB_PASSWORD` desde secrets.

## Migration Plan

1. `openspec new change c-01-foundation-setup` (hecho) → crear artifacts → review → `openspec archive` no aplica aún (solo planning).
2. Implementación C-01: scaffoldear árbol, `docker compose up -d mysql && wait-for-db`, `mvn verify`/`pip install`, `GET /api/health` 200, `git status` limpio (sin `.env` trackeado).
3. Rollback: `docker compose down -v` + `git checkout -- .` (no hay migrations de dominio que revertir). Forward-only: si V1 tiene error, crear `V1_1__fix` no editar V1 deployada.
4. Deploy dev: `java -jar target/*.jar --spring.profiles.active=dev` o `python manage.py runserver` con `.env` cargado.

## Open Questions

- ¿Equipo confirma Variante A Java Spring Boot vs B Django para C-01? (Bloquea scaffolding; default A si no responden en 48h).
- ¿SSR Thymeleaf o SPA React para decidir CSRF vs JWT en `SecurityConfig`? Puede dejarse CSRF enabled + CORS permisivo dev y ajustarse en C-03 sin cambiar specs.
- ¿Puerto `PORT` final 8080 (Java) vs 8000 (Django)? Parametrizado, no bloquea.
