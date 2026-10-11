# Spec Delta — ops-hardening

## Purpose

Cierra el MVP con seed dev reproducible y hardening operativo: validación global, errores unificados, zona horaria verificada, backup documentado y README final listos para demo y deploy.

## ADDED Requirements

### Requirement: Seed dev-only reproducible

El sistema SHALL proveer seed solo en perfil `dev` con 4 usuarios bcrypt, 5 especialidades, 1 profesional vinculado a odontólogo con 2 especialidades, 1 paciente vinculado a paciente y 3 turnos ejemplo (RESERVADO hoy, CANCELADO, ATENDIDO).

#### Scenario: Arranque dev con seed

- **WHEN** la app arranca con perfil `dev` y BD vacía
- **THEN** existen 4 usuarios logueables, 5 especialidades, 1 profesional con 2 especialidades, 1 paciente vinculado y 3 turnos visibles en agenda

#### Scenario: Prod sin seed

- **WHEN** la app arranca con perfil `prod`
- **THEN** no se crea ningún usuario ni turno de ejemplo y el login `admin/admin123` falla con 401

### Requirement: Validación global con fieldErrors

El sistema SHALL validar todos los DTOs de entrada con Bean Validation y retornar 400 con `fieldErrors` por campo ante input inválido, nunca 500.

#### Scenario: Input inválido retorna 400

- **WHEN** se envía un POST con campo obligatorio vacío o email malformado
- **THEN** responde 400 con `fieldErrors` que nombra el campo y el mensaje, sin stacktrace

### Requirement: Zona horaria verificada en borde

El sistema SHALL operar en `America/Argentina/Buenos_Aires` y rechazar turnos fuera de `APP_HORARIO_DESDE/HASTA` o con `fecha+hora` pasada según hora local de la clínica.

#### Scenario: Borde de horario

- **WHEN** se reserva a las 07:59 con apertura 08:00
- **THEN** responde 400; a las 08:00 el mismo día responde 201

#### Scenario: Turno hoy con hora pasada

- **WHEN** se reserva hoy con `hora_inicio` anterior a la hora actual de la clínica
- **THEN** responde 400 con `fieldErrors` en fecha/hora

### Requirement: Envelope de errores unificado

El sistema SHALL responder errores API con envelope `{error,message,fieldErrors,sugeridos}` según caso para 400/401/403/409 (404 con `{error,message}`), sin filtrar existencia de usuarios en 401.

#### Scenario: Credenciales inválidas genéricas

- **WHEN** login con password incorrecto o usuario inactivo
- **THEN** responde 401 con mensaje genérico "Credenciales inválidas" sin indicar qué falló

#### Scenario: Conflicto de turno con sugeridos

- **WHEN** se reserva un hueco ocupado
- **THEN** responde 409 con `sugeridos` no vacíos de slots libres alternativos

### Requirement: Backup documentado y ejecutable

El sistema SHALL proveer `scripts/backup.sh` basado en `mysqldump` con env vars y documentar en README §Deploy el backup diario y el volumen persistente.

#### Scenario: Backup manual

- **WHEN** un operador ejecuta `scripts/backup.sh` con `.env` válido
- **THEN** se genera un dump `.sql` restaurable con `mysql < backup.sql`

### Requirement: Observabilidad mínima

El sistema SHALL exponer `GET /api/health` público con `{"status":"ok"}` y emitir logs SLF4J estructurados en operaciones de turno y arranque (incluida TZ configurada).

#### Scenario: Health público

- **WHEN** se llama `GET /api/health` sin autenticación
- **THEN** responde 200 con `{"status":"ok"}`

### Requirement: Test E2E Flujos 1-5

El sistema SHALL incluir un test de integración que encadene alta paciente → reserva → consulta agenda → cancelar → reprogramar → atendido sobre H2 (Testcontainers solo si Docker disponible).

#### Scenario: Flujo completo verde

- **WHEN** se ejecuta el test E2E en `mvn -o`
- **THEN** todos los pasos 1-5 pasan sin Testcontainers y sin alterar datos de prod

### Requirement: Limpieza prod y README final

El sistema SHALL usar `ddl-auto: validate` en prod, mantener secrets solo como placeholders en `.env.example` con `.env` gitignored, y publicar README final con stack, run, credenciales dev, env vars y fuera-alcance v1.

#### Scenario: Verificación pre-cierre

- **WHEN** se revisa `application-prod.properties`, `.gitignore`, `.env.example` y `README.md`
- **THEN** prod valida schema sin migar, no hay secrets commiteados y el README documenta run, credenciales dev y fuera-alcance
