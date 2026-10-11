# Turnos Odontología

Sistema de gestión de turnos odontológicos — monolito Java Spring Boot + MySQL.

> **Stack congelado C-01:** Variante A — Java 17 + Spring Boot 3.x + Maven + Spring Data JPA + Flyway + MySQL 8.0. Variante B (Python Django) documentada como alternativa en `knowledge-base/08_arquitectura_propuesta.md` pero no activa (DD-01/DD-02).

## Requisitos

- Java 17+ (recomendado 17, probado con 21)
- Maven 3.9+
- Docker + Docker Compose (para MySQL y app)
- MySQL 8.0 (via Docker)

## Quickstart

```bash
cp .env.example .env
docker compose up --build -d
# Esperar ~30s a que mysql esté healthy y app arranque
curl http://localhost:8080/api/health
# → {"status":"ok"}

# Logs
docker compose logs -f app
```

Sin Docker (requiere MySQL local):

```bash
cp .env.example .env
# Editar DB_HOST=localhost si MySQL corre local
mvn spring-boot:run
curl http://localhost:8080/api/health
```

## Variables de entorno

Ver `knowledge-base/08_arquitectura_propuesta.md` §Variables de entorno.

| Variable | Descripción | Default | Sensible |
|----------|-------------|---------|----------|
| `DB_HOST` | Host MySQL | `localhost` / `mysql` en compose | No |
| `DB_PORT` | Puerto MySQL | `3306` | No |
| `DB_NAME` | Nombre BD | `turnos_db` | No |
| `DB_USER` | Usuario BD | `turnos_user` | No |
| `DB_PASSWORD` | Password BD | `changeme` | Sí |
| `APP_SECRET` | Clave sesión/JWT | `changeme-dev-secret-32-chars-min` | Sí |
| `APP_TIMEZONE` | Zona horaria | `America/Argentina/Buenos_Aires` | No |
| `APP_TURNO_DURACION_MIN` | Duración turno (min) | `30` | No |
| `APP_HORARIO_DESDE` | Apertura clínica | `08:00` | No |
| `APP_HORARIO_HASTA` | Cierre clínica | `18:00` | No |
| `PORT` | Puerto app | `8080` | No |

`.env` está gitignored; commitear solo `.env.example` con placeholders.

## Endpoints

- `GET /api/health` → `{"status":"ok"}` (público, sin auth) — liveness/readiness y CI smoke test.
- `GET /` , `GET /login`, `POST /login` — públicos (preparados para C-03 auth).
- Resto `authenticated` (deny by default).

Verificar health:

```bash
curl -s http://localhost:8080/api/health | jq .
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/health
# 200 sin cookie

curl -s http://localhost:8080/api/pacientes -w "%{http_code}\n"
# 401/302 sin auth (protegido)
```

## Migrations

- Flyway `V1__init.sql` bootstrap en `src/main/resources/db/migration/` (C-01).
- `spring.jpa.hibernate.ddl-auto=validate` en todos los perfiles (nunca `update`/`create`).
- `spring.flyway.enabled=true`, `validate` en prod.

### Política forward-only

- Nunca editar una migración ya deployada. Para corregir, crear nueva `V{n}__fix.sql`.
- Nueva columna en tabla con datos: `NULLABLE` o con `DEFAULT`, nunca `NOT NULL` sin default.
- Documentar rollback en PR si la migración es destructiva.

### Comandos Flyway

```bash
mvn flyway:info -Dflyway.url=jdbc:mysql://localhost:3306/turnos_db -Dflyway.user=turnos_user -Dflyway.password=changeme
mvn flyway:migrate
```

## Deploy y backups

```bash
scripts/backup.sh --dry-run   # muestra el comando sin ejecutar
scripts/backup.sh             # genera backup-YYYY-MM-DD.sql (lee .env)
# Restaurar
mysql -h $DB_HOST -P $DB_PORT -u $DB_USER -p$DB_PASSWORD $DB_NAME < backup-2026-01-01.sql
```

Backup diario sugerido (cron en el host):

```cron
0 3 * * * /opt/turnos/scripts/backup.sh >> /var/log/turnos-backup.log 2>&1
```

Volumen Docker `mysql_data` persiste datos tras `docker compose down` (sin `-v`). Con `-v` se borra.

## Estructura

Ver `knowledge-base/08_arquitectura_propuesta.md` §Estructura de directorios.

```
src/main/java/com/clinica/turnos/
  TurnosApplication.java
  config/ (SecurityConfig, WebConfig, TimeZoneConfig)
  domain/model, domain/repository
  application/service, application/dto
  web/controller, web/validator
  security/
src/main/resources/
  application.properties
  application-prod.properties
  db/migration/V1__init.sql
```

## CI

GitHub Actions `.github/workflows/ci.yml` — jobs paralelos `backend-test` (`mvn -B verify` con MySQL 8.0 service) + `lint`.

## Testing

- Unit sin IO: JUnit 5 puro.
- Web slice: `@WebMvcTest` para controllers.
- Integración DB: Testcontainers MySQL (C-02 en adelante).

```bash
mvn -B verify
```

## Docs

- `knowledge-base/` — fuente de verdad del dominio
- `CHANGES.md` — roadmap 12 changes (C-01 es GATE0, desbloquea C-02)
- `openspec/` — specs OpenSpec

## Fuera de alcance v1

Recordatorios, historia clínica (HC), pagos, multi-sede, duración por
especialidad, anticipación mínima RN-TU-10 — ver
`knowledge-base/01_vision_y_objetivos.md`.

Supuestos congelados (3 Altas de `10_preguntas_abiertas.md`):
horario global 08:00–18:00 (no por profesional/día), mono-sede (sin
`sede_id`), paciente con o sin cuenta linkeado por DNI (IN-01).

## Credenciales dev (seed @Profile dev)

El seed `DevDataInitializer` solo corre con perfil `dev` (nunca en prod):

| Usuario | Password | Rol |
|---------|----------|-----|
| `admin` | `admin123` | ADMIN |
| `secretaria` | `secre1234` | SECRETARIA |
| `odontologo` | `odonto1234` | ODONTOLOGO |
| `paciente` | `paciente123` | PACIENTE |

```bash
# Perfil dev con seed (5 especialidades + profesional + paciente + 3 turnos ejemplo)
APP_ENV=dev mvn spring-boot:run
# o con compose (APP_ENV=dev en .env)
docker compose up --build -d
curl -s http://localhost:8080/api/health
# → {"status":"ok"}

# Login de prueba
curl -s -c jar.txt -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' \
  http://localhost:8080/api/auth/login
curl -s -b jar.txt http://localhost:8080/api/turnos | jq .totalElements
# → 3 (turnos ejemplo del seed)
```
