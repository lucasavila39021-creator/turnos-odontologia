# Proposal — C-01 foundation-setup

## Why

El repo está vacío (sin código, sin BD, sin CI): ningún change posterior compila ni migra. Se necesita scaffolding del monolito + infra base para desbloquear C-02 y todo el roadmap; sin esta fundación no hay health check, env vars, ni migrations versionadas.

## What Changes

- Scaffolding monolito según `knowledge-base/08_arquitectura_propuesta.md` — Elegir **una** variante y congelar en `08` + `config.yaml`: Variante A Java Spring Boot `src/main/java/com/clinica/turnos/...` **o** Variante B Python Django `apps/` (no ambas en paralelo). Estructura incluye `config/`, `domain/`, `application/`, `web/`, `security` (Java) o `turnos/`+`apps/` (Django).
- Backend mínimo con `GET /api/health` → `{"status":"ok"}` (200) sin auth, para readiness/liveness y CI smoke test.
- MySQL 8.0 vía `docker-compose.yml` (servicios `app` + `mysql`/`db`), volumen persistente `mysql_data`, `wait-for-db`/healthcheck, `depends_on` y port mapping 3306.
- Migrations infra inicializada sin tablas de dominio: Flyway `V1__init.sql` (placeholder o `flyway_schema_history` bootstrap) **o** Django `migrations/` con `__init__.py` — `ddl-auto: validate` / `DEBUG=false` en prod.
- `.env.example` + `.env` (`.env` gitignored) con `DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD/APP_SECRET/APP_TIMEZONE=America/Argentina/Buenos_Aires/APP_TURNO_DURACION_MIN=30/APP_HORARIO_DESDE=08:00/APP_HORARIO_HASTA=18:00/PORT` (definición `08 §Variables de entorno`).
- `application.properties` / `settings.py` con datasource via `${VAR}`, zona horaria fija `APP_TIMEZONE`, perfiles `dev` vs `prod`, HikariCP/timeouts configurados.
- Seguridad base: `SecurityConfig` / `settings.AUTH` con CSRF según SSR vs JWT, `BCryptPasswordEncoder(12)` bean, CORS en `SecurityFilterChain` (no `*` en prod), paths públicos `GET /`, `GET /login`, `POST /login`, `GET /api/health`.
- GitHub Actions CI con jobs paralelos `backend-test` + `lint`, cache Maven/pip, run sobre `docker-compose` o Testcontainers MySQL.
- `.gitignore`, `README.md` con instrucciones `docker compose up`, `mysqldump` backup (P5:b), env vars y credenciales dev.

## Capabilities

### New Capabilities
- `platform-foundation`: Infraestructura base y scaffolding del monolito — health check, docker-compose MySQL, env vars, zona horaria fija, migrations bootstrap, seguridad base, CI, gitignore y README. Es el contrato observable de que el proyecto arranca, responde y migra.

### Modified Capabilities
- Ninguna (repo greenfield, no hay `openspec/specs` previos).

## Impact

- Nuevo código: `docker-compose.yml`, `Dockerfile` (multi-stage opcional), `.env.example`, `application.properties`/`settings.py`, `SecurityConfig`, `HealthController`, `TimeZoneConfig`, `db/migration/V1__init.sql` o `apps/*/migrations/`, `pom.xml`/`requirements.txt`, `.github/workflows/ci.yml`, `.gitignore`, `README.md`.
- APIs: nuevo `GET /api/health` público sin auth.
- Dependencias: MySQL 8.0, Flyway o Django migrations, Spring Boot 3.x / Django 5.x, Testcontainers MySQL para tests futuros.
- Sistemas: CI GitHub Actions, Docker local; sin impacto en specs existentes (no hay).
- Breaking: ninguno — primer change.
