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

## Integración MCP: MySQL en solo lectura

El agente de código (OpenCode) puede consultar el **esquema real** de la base de desarrollo a través de un servidor MCP. Se usa para explorar antes de proponer un change (por ejemplo, el C-13), no para modificar datos.

| Dato | Valor |
|---|---|
| Servidor | [`@benborla29/mcp-server-mysql`](https://github.com/benborla/mcp-server-mysql) (MIT) |
| Herramienta usada | `mysql_query` (y el recurso `mysql://tables`) |
| Cliente | OpenCode **1.19.5** (V1). La configuración es de V1: no actualizar a 2.0 sin migrarla |
| Configuración | `opencode.json` en la raíz del repo |

### Requisitos

- Node.js 20 o superior.
- OpenCode 1.19.5: `npm install -g opencode-ai@1.19.5`.
- MySQL con la base `turnos_db` cargada: con Docker (Quickstart) o, sin Docker, con `scripts/dev-db-nativo.sql`, que aplica las migraciones V2–V6 y carga datos ficticios.

### Configuración

1. Instalar el servidor de forma fija (con `npx` falló en Windows, ver más abajo):
   ```bash
   npm install -g @benborla29/mcp-server-mysql
   ```
2. Crear un usuario **exclusivo para el agente, solo lectura**, como root:
   ```sql
   CREATE USER 'agente_mcp'@'localhost' IDENTIFIED BY '<clave>';
   GRANT SELECT, SHOW VIEW ON turnos_db.* TO 'agente_mcp'@'localhost';
   ```
3. Guardar esa clave **fuera del repo**, en una variable de entorno del sistema:
   ```
   setx MYSQL_PASS_AGENTE "<clave>"        (Windows, CMD)
   export MYSQL_PASS_AGENTE='<clave>'      (Linux, en ~/.bashrc)
   ```
   Cerrar y volver a abrir las terminales (y VS Code) para que la tomen.
4. Ajustar en `opencode.json` la ruta de `command` a la de la máquina. En Windows es `node` + la ruta de `dist\index.js` dentro de `%APPDATA%\npm\node_modules\@benborla29\mcp-server-mysql\`; en Linux alcanza con `["mcp-server-mysql"]`.

### Variables

| Variable | Valor en este proyecto | Dónde vive |
|---|---|---|
| `MYSQL_HOST` / `MYSQL_PORT` | `127.0.0.1` / `3306` | `opencode.json` |
| `MYSQL_USER` | `agente_mcp` | `opencode.json` |
| `MYSQL_DB` | `turnos_db` | `opencode.json` |
| `MYSQL_PASS` | `{env:MYSQL_PASS_AGENTE}` | solo la variable del sistema; **nunca** en el repo |
| `ALLOW_INSERT/UPDATE/DELETE_OPERATION` | `false` | `opencode.json` |

### Límites de acceso (tres candados)

1. **El servidor MCP** corre en solo lectura: los tres `ALLOW_*_OPERATION` están en `false`.
2. **El usuario de MySQL** solo tiene `SELECT` y `SHOW VIEW`. Comprobado: un `DELETE` con `agente_mcp` devuelve *Error 1142, DELETE command denied*.
3. **OpenCode pide permiso** antes de ejecutar comandos de consola o editar archivos (`"permission": {"bash": "ask", "edit": "ask"}`). Se agregó porque, sin MCP, el agente intentó resolver el pedido por su cuenta (ver `docs/skills-mcp/bitacora.md`, H14).

Solo se usa contra la base de desarrollo, con datos ficticios.

### Cómo probar

1. Conexión del servidor:
   ```bash
   opencode mcp list
   # → ✓ mysql connected
   ```
2. Uso real: abrir `opencode` en la raíz del repo, esperar a que `mysql` figure conectado en el panel lateral y pedir:
   > Usá exclusivamente la herramienta del servidor MCP mysql para ejecutar SHOW CREATE TABLE turno sobre la base turnos_db. Si la herramienta no está disponible, decímelo y no respondas leyendo archivos del repositorio.

   La respuesta debe incluir los índices que MySQL crea solo para las claves foráneas (`fk_turno_especialidad`, `fk_turno_created_by`), que **no están escritos** en `V6__turno.sql`. Es la señal de que leyó la base y no el archivo.
3. En el log de OpenCode (`~/.local/share/opencode/log/opencode.log`) queda la línea `permission=mysql_mysql_query`.

### Problemas encontrados y solución

| Síntoma | Causa | Solución |
|---|---|---|
| Docker Desktop no instala | El equipo usa Windows 10 Enterprise LTSC 1809, que Docker no soporta | MySQL nativo + `scripts/dev-db-nativo.sql` |
| `Operation timed out after 30000ms` | La primera descarga con `npx` superó el límite de OpenCode | `"timeout": 120000` e instalación fija |
| `Connection closed` y avisos `EPERM` | `npx` dejó su caché a medio descargar | `npm install -g` en lugar de `npx` |
| `opencode mcp list` se queda colgado | OpenCode no puede lanzar el atajo `.cmd` que npm crea en Windows | `"command": ["node", "<ruta>\\dist\\index.js"]` |
| `mcp list` dice *connected*, pero el agente no puede usar la base (`failed to get resources`) | La clave de `MYSQL_PASS_AGENTE` y la de MySQL no coincidían: listar herramientas no entra a la base, consultar sí | Misma clave en `ALTER USER` y en `setx`. Prueba directa: `ER_ACCESS_DENIED_ERROR` → `CONEXION OK` |

## Docs

- `knowledge-base/` — fuente de verdad del dominio
- `CHANGES.md` — roadmap 12 changes (C-01 es GATE0, desbloquea C-02)
- `openspec/` — specs OpenSpec
- `docs/skills-mcp/` — TP Skills + MCP: bitácora, ficha de investigación y evidencias

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
