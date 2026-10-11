# Spec Delta — platform-foundation

## Purpose

Provee la infraestructura base y el scaffolding del monolito para que el sistema arranque, responda y migre de forma reproducible. Incluye health check, MySQL dockerizado, env vars, zona horaria fija y CI; sin ella ningún change posterior es desplegable ni testeable.

## ADDED Requirements

### Requirement: Health check público
El sistema SHALL exponer `GET /api/health` sin autenticación que retorne 200 con cuerpo `{"status":"ok"}` (o equivalente `UP`) para liveness/readiness y smoke tests de CI.

#### Scenario: Health check responde OK
- **WHEN** un cliente no autenticado hace `GET /api/health`
- **THEN** el sistema retorna 200 con `status` igual a `ok` o `UP` y `Content-Type` JSON

#### Scenario: Health no requiere auth
- **WHEN** `GET /api/health` se consulta sin sesión ni token
- **THEN** no se retorna 401 ni 302 sino 200

### Requirement: MySQL via docker-compose con volumen persistente
El sistema SHALL proveer `docker-compose.yml` con servicios `app` y `mysql` (MySQL 8.0, InnoDB, `utf8mb4_0900_ai_ci`) que levanten con `docker compose up`, con volumen persistente, healthcheck `mysqladmin ping` y `depends_on` healthy + wait-for-db.

#### Scenario: Stack levanta y MySQL está healthy
- **WHEN** se ejecuta `docker compose up -d` con `.env` válido
- **THEN** `mysql` pasa a `healthy` y `app` puede conectar a `DB_HOST:DB_PORT/DB_NAME` sin intervención manual

#### Scenario: Datos persisten tras reinicio
- **WHEN** se hace `docker compose down` sin `-v` y luego `up`
- **THEN** los datos de MySQL en volumen `mysql_data` permanecen

### Requirement: Configuración por variables de entorno
El sistema SHALL leer toda configuración sensible y de entorno desde variables (`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `APP_SECRET`, `APP_TIMEZONE`, `APP_TURNO_DURACION_MIN`, `APP_HORARIO_DESDE`, `APP_HORARIO_HASTA`, `PORT`) con defaults documentados, sin hardcodear secrets en código o `application.properties`/`settings.py`.

#### Scenario: Env vars inyectadas vía .env
- **WHEN** `.env` define `DB_PASSWORD=s3cr3t` y la app arranca
- **THEN** el datasource usa ese valor y no un literal en el repo

#### Scenario: .env.example documenta placeholders
- **WHEN** un desarrollador clona el repo
- **THEN** encuentra `.env.example` con placeholders (`changeme`) y lista completa de vars de `08 §Variables de entorno`

### Requirement: Zona horaria fija America/Argentina/Buenos_Aires
El sistema SHALL fijar la zona horaria en `America/Argentina/Buenos_Aires` a nivel JVM (`TimeZone.setDefault`) / Django `TIME_ZONE` y en `application.properties`/`settings.py`, persistiendo `DATETIME` sin conversión implícita (RN-GL-02).

#### Scenario: Timezone forzada al arranque
- **WHEN** la app arranca con `APP_TIMEZONE=America/Argentina/Buenos_Aires`
- **THEN** `TimeZone.getDefault()` (o `settings.TIME_ZONE`) es esa zona y un turno creado a las `10:00` se persiste como `10:00` local sin shift UTC

#### Scenario: JDBC URL incluye serverTimezone
- **WHEN** se inspecciona `spring.datasource.url`
- **THEN** contiene `serverTimezone=America/Argentina/Buenos_Aires` o equivalente parametrizado

### Requirement: Migrations infra bootstrap
El sistema SHALL tener infraestructura de migrations versionada forward-only inicializada sin tablas de dominio: Flyway `V1__init.sql` (bootstrap) con `validate` en prod **o** Django `migrations/` con `__init__.py`, y política de no editar migración deployada.

#### Scenario: Migrations validan en arranque
- **WHEN** la app arranca con perfil `prod`/`validate`
- **THEN** Flyway/Django valida que el historial coincide y falla si hay drift en lugar de auto-`update`

#### Scenario: Nueva columna requiere nullable o default
- **WHEN** un change futuro agrega columna a tabla con datos
- **THEN** la migración la declara `NULLABLE` o con `DEFAULT` y nunca `NOT NULL` sin default

### Requirement: Seguridad base mínima
El sistema SHALL proveer `SecurityConfig` / `settings.AUTH` con `BCryptPasswordEncoder(12)`, CSRF según SSR vs JWT (documentado), CORS configurado en `SecurityFilterChain` (nunca `*` en prod), y paths públicos `GET /`, `GET /login`, `POST /login`, `GET /api/health` con resto `authenticated` por defecto.

#### Scenario: Rutas públicas no requieren auth
- **WHEN** un cliente sin sesión accede a `GET /api/health` o `GET /login`
- **THEN** no se exige autenticación (200/302 según ruta) mientras otras rutas retornan 401/302

#### Scenario: Passwords hasheados con bcrypt 12
- **WHEN** se registra un password vía `PasswordEncoder`
- **THEN** el hash usa BCrypt con strength 12 y nunca plaintext

### Requirement: CI pipeline con jobs paralelos
El sistema SHALL incluir GitHub Actions CI en `.github/workflows/ci.yml` con jobs paralelos `backend-test` y `lint`, cache de Maven/pip, y ejecución sobre MySQL 8.0 (servicio o Testcontainers) que falle si tests o health no pasan.

#### Scenario: CI corre en push/PR
- **WHEN** se hace `git push` a `master`/`main` o PR
- **THEN** CI ejecuta `backend-test` + `lint` en paralelo y reporta verde/rojo en <10 min

### Requirement: Gitignore, README y estructura congelada
El sistema SHALL tener `.gitignore` que ignore `.env`, `target/`, `__pycache__/`, `venv/`, `.idea/` y `README.md` con instrucciones `docker compose up --build`, `curl /api/health`, `mysqldump` backup, tabla de env vars y variante de stack congelada (`08`/`config.yaml`) según DD-01/DD-02.

#### Scenario: Secrets no se commitean
- **WHEN** se ejecuta `git status` con `.env` presente
- **THEN** `.env` aparece ignorado y no staged

#### Scenario: README permite quickstart
- **WHEN** un desarrollador sigue `README.md` pasos `cp .env.example .env && docker compose up`
- **THEN** `curl localhost:${PORT}/api/health` retorna 200 sin pasos adicionales no documentados
