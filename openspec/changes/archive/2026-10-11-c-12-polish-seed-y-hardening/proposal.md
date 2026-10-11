# Proposal — c-12-polish-seed-y-hardening

## Why

Último change del roadmap (GATE 8, tras C-11). El MVP funciona pero le falta cierre operativo: sin seed dev completo no hay demo reproducible, y sin consolidar validación global, envelope de errores, TZ borde, backup script y README final el deploy a producción es frágil. Este change cierra el MVP sin romper nada existente.

## What Changes

- Seed dev-only ampliado: `DevDataInitializer` @Profile(dev) crea 4 usuarios bcrypt (admin/secretaria/odontologo/paciente), 5 especialidades, 1 profesional + usuario odontólogo + 2 especialidades, 1 paciente + usuario paciente, 3 turnos ejemplo (RESERVADO hoy / CANCELADO / ATENDIDO). NUNCA en prod (sin V7 prod, sin password en migración).
- Validación global RN-GL-01: auditoría `@Valid` en todos los DTOs/controllers faltantes; input inválido → 400 con `fieldErrors`, nunca 500.
- TZ RN-GL-02: `TimeZoneConfig` ya existe; se agregan tests borde horario (slot 07:59 rechaza / 08:00 acepta, turno hoy hora pasada rechaza) con TZ forzada.
- Errores unificados: consolidar `GlobalExceptionHandler` + `MvcExceptionHandler` al envelope `{error,message,fieldErrors,sugeridos}` para 400/401/403/409 (+404 existente); sin duplicar handlers.
- Backup: `scripts/backup.sh` (`mysqldump` con env vars, `chmod +x`, modo diario) + README §Deploy + volumen `mysql_data` documentado.
- Observabilidad: `/api/health` ya existe; agregar logs SLF4J estructurados en servicios turno/paciente + README docker compose + migrations.
- Tests integración: un test E2E H2 cubriendo Flujos 1-5 (alta paciente → reserva → agenda → cancelar → reprogramar → atendido); Testcontainers solo si Docker disponible (skipea en `mvn -o`).
- Limpieza: verificar `ddl-auto: validate` en prod, secrets solo `.env.example` placeholders, `.env` no commiteado, `APP_SECRET` random en `.env` real.
- README final: stack congelado, cómo correr, credenciales dev, env vars, fuera-alcance v1.

## Capabilities

### New Capabilities

- `ops-hardening`: cierre operativo del MVP — seed dev reproducible, validación global 400 fieldErrors, TZ borde testeada, envelope errores unificado, backup script, observabilidad mínima, test E2E Flujos 1-5, limpieza prod y README final.

### Modified Capabilities

(none — no cambian REQUIREMENTS de specs existentes; se consolida sin romper C-01..C-11.)

## Impact

- Código: `config/DevDataInitializer` (ampliar), `web/GlobalExceptionHandler` + `MvcExceptionHandler` (consolidar envelope), DTOs/controllers (agregar `@Valid` faltantes), `scripts/backup.sh` (nuevo), `README.md` (sección final), tests E2E nuevos.
- Sin breaking changes: endpoints, DTOs y RBAC existentes intactos; seed solo perfil `dev`.
- Supuestos (10_preguntas_abiertas 3 Altas no cerradas): horario global 08:00-18:00 (no por profesional/día), mono-sede (sin `sede_id`), paciente con o sin cuenta linkeado por DNI (IN-01).
