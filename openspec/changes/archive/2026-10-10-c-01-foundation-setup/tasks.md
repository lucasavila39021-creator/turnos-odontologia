# Tasks — C-01 foundation-setup

## 1. Decisión de variante y scaffolding base

- [x] 1.1 Congelar variante de stack (A Java Spring Boot 17/3.x + Maven o B Django 5.x) en `knowledge-base/08_arquitectura_propuesta.md` + `config.yaml`/`README.md` y verificar que el doc menciona una sola variante activa
- [x] 1.2 Scaffoldear árbol de directorios según variante elegida (`src/main/java/com/clinica/turnos/{config,domain,application,web,security}` + `src/main/resources` **o** `turnos/`+`apps/{usuarios,pacientes,catalogo,agenda}`) y verificar con `ls -R` que no quedan directorios de la variante descartada
- [x] 1.3 Crear `pom.xml` (Spring Boot 3.x, `spring-boot-starter-web`, `data-jpa`, `validation`, `security`, `flyway`, `mysql-connector-j`, `testcontainers`) **o** `requirements.txt`/`pyproject.toml` (Django, `mysqlclient`, `djangorestframework` si SPA) y verificar `mvn -v`/`pip install --dry-run` resuelve deps

## 2. Variables de entorno y zona horaria

- [x] 2.1 Crear `.env.example` con placeholders para `DB_HOST/PORT/NAME/USER/PASSWORD/APP_SECRET/APP_TIMEZONE=America/Argentina/Buenos_Aires/APP_TURNO_DURACION_MIN=30/APP_HORARIO_DESDE=08:00/APP_HORARIO_HASTA=18:00/PORT` y verificar que `grep -c APP_ .env.example` >=7 y no contiene `s3cr3t` real
- [x] 2.2 Crear `.env` local (copia de example) y `.gitignore` que ignore `.env` y verificar `git check-ignore .env` retorna `.env` y `git status` no lo lista
- [x] 2.3 Configurar `src/main/resources/application.properties` (o `turnos/settings.py`) con datasource `jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:turnos_db}?serverTimezone=${APP_TIMEZONE}` + `APP_*` vía `${VAR:default}`, perfiles `dev` (`ddl-auto: validate`, `DEBUG=false` no, log level) vs `prod`, y verificar arranque lee `DB_HOST` de env (`env DB_HOST=test mvn spring-boot:run --dry-run` o `python manage.py diffsettings | grep DB_HOST`)
- [x] 2.4 Fijar zona horaria `America/Argentina/Buenos_Aires` (`TimeZoneConfig` con `TimeZone.setDefault` + `@PostConstruct` **o** `TIME_ZONE`+`USE_TZ=True`) y verificar con test unit que `TimeZone.getDefault().getID()` == `America/Argentina/Buenos_Aires` tras arranque

## 3. Docker y MySQL 8.0

- [x] 3.1 Crear `docker-compose.yml` (`app` build `.` + `mysql:8.0` `utf8mb4_0900_ai_ci`, volumen `mysql_data`, `healthcheck: mysqladmin ping -h localhost`, `depends_on: condition: service_healthy`, env desde `.env`) y verificar `docker compose config` valida y expone `3306`
- [x] 3.2 Crear `Dockerfile` multi-stage (eclipse-temurin:17 + `mvn package -DskipTests` **o** `python:3.11-slim` + `pip install`) con `wait-for-db`/`entrypoint` que espera `mysql:3306` y verificar `docker compose up -d mysql && docker inspect --format='{{.State.Health.Status}}' turnos-mysql` → `healthy` en <60s
- [x] 3.3 Validar persistencia de volumen y charset y verificar `docker exec mysql mysql -e "SELECT @@character_set_database"` → `utf8mb4` y `docker compose down && docker compose up -d && mysql -e "SHOW TABLES"` conserva datos

## 4. Migrations infra bootstrap

- [x] 4.1 Inicializar Flyway `src/main/resources/db/migration/V1__init.sql` (bootstrap con comentario "C-02 creará V2__usuario") + `spring.flyway.enabled=true` **o** Django `apps/*/migrations/__init__.py` + `manage.py makemigrations` vacío y verificar `mvn flyway:info`/`python manage.py migrate --plan` no reporta `ddl-auto: update`
- [x] 4.2 Configurar `spring.jpa.hibernate.ddl-auto=validate` (Java) o `DEBUG=false` prod check (Django) y verificar que `application-prod.properties` contiene `validate` y no `update`/`create`
- [x] 4.3 Documentar política forward-only (nunca editar migración deployada, nueva columna nullable/con default) en `README.md` §Migrations y verificar que `V1__init.sql` no contiene `NOT NULL` sin `DEFAULT` en tabla con datos (review)

## 5. Seguridad base y health check

- [x] 5.1 Implementar `SecurityConfig` (`@EnableWebSecurity`, `SecurityFilterChain`: `permitAll` para `GET /`, `GET /login`, `POST /login`, `GET /api/health`; resto `authenticated`; `BCryptPasswordEncoder(12)` bean; CSRF/CORS según SSR vs JWT documentado) y verificar que `curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/health` → 200 sin cookie y `curl http://localhost:8080/api/pacientes` → 401/302
- [x] 5.2 Implementar `HealthController` `GET /api/health` → `ResponseEntity.ok(Map.of("status","ok"))` (constructor injection, sin exponer entidad) y verificar `curl localhost:8080/api/health | jq .status` → `ok` y `Content-Type: application/json`
- [x] 5.3 Añadir `WebConfig`/`ForwardedHeaderFilter` + `server.forward-headers-strategy=native` si aplica y verificar headers de seguridad (`X-Frame-Options: SAMEORIGIN`, `Referrer-Policy` equivalente) en respuesta health

## 6. CI, gitignore y README

- [x] 6.1 Crear `.gitignore` completo (`target/`, `*.class`, `.idea/`, `__pycache__/`, `venv/`, `node_modules/`, `.env`, `.DS_Store`) y verificar `git status --ignored` lista `.env` como ignored
- [x] 6.2 Crear `.github/workflows/ci.yml` con jobs paralelos `backend-test` (`mvn -B verify` o `pytest`, cache `actions/setup-java@v4`+`cache: maven` o `setup-python`, servicio `mysql:8.0` o Testcontainers) y `lint` (checkstyle/spotless o `flake8/black --check`) y verificar `act --dry-run` o `yamllint .github/workflows/ci.yml` pasa y ambos jobs tienen `runs-on: ubuntu-latest`
- [x] 6.3 Escribir `README.md` con stack congelado, `cp .env.example .env && docker compose up --build`, `curl localhost:${PORT}/api/health`, backup `mysqldump -h $DB_HOST -u $DB_USER -p$DB_NAME > backup.sql`, tabla de env vars (de `08`), link a `knowledge-base/` y `CHANGES.md`, y verificar `markdownlint README.md` sin errores y quickstart ejecutable en fresh clone

## 7. Verificación integrada

- [x] 7.1 Smoke test E2E local: `docker compose down -v && docker compose up --build -d && sleep 30 && curl -f http://localhost:${PORT}/api/health && docker compose logs app | grep -i "Started.*Application"` y verificar 200 + log de arranque sin stacktrace
- [x] 7.2 Validar `openspec validate --strict` para `c-01-foundation-setup` sin errores de spec (cada Requirement tiene Scenario `####`) y verificar `openspec status --change c-01-foundation-setup --json` reporta todos los artifacts `done`
