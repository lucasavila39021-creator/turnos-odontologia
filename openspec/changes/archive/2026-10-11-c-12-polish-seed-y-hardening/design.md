# Design — c-12-polish-seed-y-hardening

## Context

Ver `proposal.md` (Why). Estado real verificado en código: `DevDataInitializer` @Profile(dev) solo crea admin (C-02); `TimeZoneConfig` fija TZ vía `app.timezone` (C-01); `GlobalExceptionHandler` RFC7807 cubre 400/403/409/404 + `MvcExceptionHandler` SSR (C-03..C-10); `V1..V6` aplicadas; `README.md` básico con health/migrations/backup-inline; `.env.example` con placeholders. Este change consolida sin duplicar ni romper.

Constraints: NUNCA password en migración ni seed prod; forward-only Flyway (database-migrations); secrets vía env (springboot-security); `mvn -o` con H2 para tests (Testcontainers skipea sin Docker); TZ `America/Argentina/Buenos_Aires`.

## Goals / Non-Goals

**Goals:**
- Seed dev completo y seguro (@Profile dev, bcrypt, idempotente).
- Validación global y envelope unificado verificados, no reescritos.
- TZ borde testeada, backup como script ejecutable, observabilidad mínima con logs.
- Test E2E Flujos 1-5 en H2 + README final de cierre.

**Non-Goals:**
- No hay V7 con datos en prod; no se edita V1..V6.
- No multi-sede, duración por especialidad, anticipación RN-TU-10 ni recordatorios (v2).
- No JWT ni cambio de auth; no rediseño UI.

## Decisions

### D-01 — Seed en `DevDataInitializer`, no en V7

Ampliar el `@Component @Profile("dev")` existente para crear en orden: 5 especialidades → 4 usuarios bcrypt → 1 profesional (link odontólogo + 2 especialidades) → 1 paciente (link paciente) → 3 turnos ejemplo. Checks `existsBy...` idempotentes + log `seed ... created/already exists`.
Alternativa descartada: `V7__seed.sql` con hashes fijos — viola "nunca password en migración", no es portable (bcrypt cost) y correría en prod. Si el equipo exige SQL versionado, V7 sería `dev-only` con `spring.flyway.locations` condicional, pero se prefiere Java @Profile por simplicidad y seguridad.

### D-02 — Validación global por auditoría, no rewrite

Auditar cada controller/DTO con `grep @Valid` y agregar `@Valid` faltantes + constraints (`@NotBlank/@Email/@Size`) donde falte. `GlobalExceptionHandler.handleValidation` ya retorna 400+fieldErrors; solo se agregan handlers para excepciones no mapeadas que hoy darían 500 (verificar `ConstraintViolationException`, `HttpMessageNotReadableException` → 400 fieldErrors).
Alternativa descartada: reescribir el handler — rompería C-03..C-10.

### D-03 — TZ: sin cambio de config, solo tests borde

`TimeZoneConfig` + `app.timezone` ya fuerzan TZ. Se agregan tests: `TimeZoneConfigTest` (default zone == America/Argentina/Buenos_Aires) y `TurnoHorarioBordeTest` (07:59→400, 08:00→201, hoy hora pasada→400) inyectando reloj fijo o fecha relativa. Sin tocar persistencia (`DATETIME` sin conversión).

### D-04 — Envelope: consolidar, no duplicar

Unificar `GlobalExceptionHandler` (API) y `MvcExceptionHandler` (SSR) al envelope `{error,message,fieldErrors,sugeridos}` para 400/401/403/409; 401 genérico sin enumeración; 409 turno con `sugeridos`. Verificar `DataIntegrityViolationException` mapea constraints conocidas a 409 fieldErrors. Sin cambiar status codes existentes (tests C-03..C-10 deben seguir verdes).

### D-05 — Backup como script + doc

`scripts/backup.sh`: `set -euo pipefail`, carga `.env`, `mysqldump -h $DB_HOST -P $DB_PORT -u $DB_USER -p$DB_PASSWORD $DB_NAME > backup-$(date +%F).sql`, `chmod +x`. README §Deploy: backup diario (cron ejemplo), restore, volumen `mysql_data` (persiste sin `-v`). `utf8mb4` intacto (sin flags de charset raro).

### D-06 — Observabilidad mínima con SLF4J

Sin Micrometer en v1: `log.info("op=crear-turno profesional={} fecha={} hora={}", ...)` en `TurnoService`, `log.info("seed ...")` existente, `log.info("timezone configured")` existente. README documenta `docker compose logs -f app`, `curl /api/health`, `mvn flyway:info`. Health ya público y testeado.

### D-07 — E2E Flujos 1-5 en H2, Testcontainers condicional

Un `@SpringBootTest` H2 (`@ActiveProfiles("test")`) encadena: alta paciente → reserva 201 → agenda contiene turno → cancelar → reprogramar (original CANCELADO + nuevo RESERVADO) → atendido. `@DisabledIfSystemProperty` o `assumeTrue(dockerAvailable)` para variante Testcontainers MySQL. Debe pasar con `mvn -o`.

## Risks / Trade-offs

- [Seed dev enmascara bug prod] → seed estrictamente @Profile(dev); test `ProdSinSeed` verifica perfil prod no crea admin.
- [Tests borde TZ frágiles por fecha del sistema] → usar fecha relativa (hoy/mañana) + hora calculada, no fechas fijas.
- [Envelope unificado rompe tests viejos que asertan `detail`] → mantener `detail` + agregar `error/message`; verificación: suite C-03..C-10 verde antes de cerrar.
- [mysqldump password en `ps`] → script usa `MYSQL_PWD` env o `--password="$DB_PASSWORD"` con nota; nunca commitear `.env`.
- [E2E H2 difiere de MySQL] → assertions sobre comportamiento (status/estado), no SQL nativo; Testcontainers como job CI con Docker.

## Migration Plan

Sin migración DB (forward-only: ninguna V nueva en prod). Deploy: `docker compose up --build`, `curl /api/health`, `mvn flyway:info` verifica V1..V6. Rollback: revertir commit + `docker compose restart app`; seed dev no afecta prod. Verificación: `mvn -o test` verde, `scripts/backup.sh --help`/dry-run, `grep -r admin123 src/main/resources` vacío.

## Open Questions

Ninguna bloqueante. Supuestos de las 3 Altas de `10_preguntas_abiertas.md` quedan documentados en README fuera-alcance (horario global, mono-sede, paciente con/sin cuenta).
