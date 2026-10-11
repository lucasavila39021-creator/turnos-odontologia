# Tasks — c-12-polish-seed-y-hardening

## 1. Seed dev-only completo

- [x] 1.1 Ampliar `DevDataInitializer` (@Profile dev): crear 5 especialidades + 4 usuarios bcrypt + 1 profesional linkeado a odontólogo con 2 especialidades + 1 paciente linkeado a paciente, idempotente con `existsBy`, y verificar arranque dev loguea `seed ... created` y login de los 4 usuarios retorna 200
- [x] 1.2 Agregar 3 turnos ejemplo en seed (RESERVADO hoy, CANCELADO, ATENDIDO) con profesional/especialidad/paciente del seed, y verificar `GET /api/turnos` dev lista ≥3 y `grep -r admin123 src/main/resources` vacío (sin password en migraciones)
- [x] 1.3 Test `ProdSinSeedTest`: perfil prod no crea usuarios seed y `admin/admin123` retorna 401, y verificar `mvn -o -Dtest=ProdSinSeedTest test` verde

## 2. Validación global y envelope

- [x] 2.1 Auditar `@Valid` en controllers/DTOs (`grep -rn @Valid src/main/java`), agregar faltantes + constraints, y verificar input inválido en cada POST/PATCH retorna 400 con `fieldErrors` (nunca 500) vía tests MockMvc
- [x] 2.2 Consolidar `GlobalExceptionHandler` + `MvcExceptionHandler` al envelope `{error,message,fieldErrors,sugeridos}` para 400/401/403/409 (mantener `detail` compat), 401 genérico, y verificar suite C-03..C-10 (`mvn -o test`) sigue verde + nuevo `ErrorEnvelopeTest` asserts envelope
- [x] 2.3 Mapear excepciones no cubiertas (`ConstraintViolationException`, `HttpMessageNotReadableException`) a 400 fieldErrors, y verificar request con JSON malformado retorna 400 no 500

## 3. Zona horaria borde

- [x] 3.1 Test `TimeZoneConfigTest`: default zone es `America/Argentina/Buenos_Aires` tras `TimeZoneConfig.init`, y verificar `mvn -o -Dtest=TimeZoneConfigTest test` verde
- [x] 3.2 Test `TurnoHorarioBordeTest`: reserva 07:59→400, 08:00→201, hoy hora pasada→400 con TZ clínica, y verificar `mvn -o -Dtest=TurnoHorarioBordeTest test` verde

## 4. Backup, observabilidad y limpieza

- [x] 4.1 Crear `scripts/backup.sh` (`set -euo pipefail`, carga `.env`, `mysqldump`, `chmod +x`) y verificar `bash -n scripts/backup.sh` + dry-run genera dump restaurable documentado
- [x] 4.2 Agregar logs SLF4J estructurados en `TurnoService` (crear/cancelar/reprogramar/atendido) sin secrets/PII en claro, y verificar `docker compose logs app` muestra `op=crear-turno ...` y `timezone configured`
- [x] 4.3 Verificar limpieza prod: `ddl-auto: validate` en prod, `.env` gitignored, `.env.example` solo placeholders (`changeme`), `APP_SECRET` real random 32+ chars fuera de git, y verificar `git status --porcelain` no lista `.env` y `grep -ri password src/main/resources/application*.properties` vacío de valores reales

## 5. E2E Flujos 1-5 y README final

- [x] 5.1 Test E2E H2 `@SpringBootTest` encadenando alta paciente → reserva 201 → agenda contiene turno → cancelar → reprogramar (original CANCELADO + nuevo RESERVADO) → atendido, y verificar `mvn -o -Dtest=*E2E* test` verde (Testcontainers con `assumeTrue` docker, skipea sin Docker)
- [x] 5.2 README final: stack congelado, `docker compose up` + `curl /api/health`, credenciales dev (admin/admin123 + 3), tabla env vars, §Deploy (backup.sh + restore + volumen), fuera-alcance v1 (recordatorios, HC, pagos, multi-sede, RN-TU-10), y verificar `README.md` contiene las 5 secciones y comandos copiables
- [x] 5.3 Verificación cierre: `mvn -o verify` verde completo, `openspec validate --strict` sin errores en el change, y `curl /api/health` 200 sin auth tras `docker compose up`
