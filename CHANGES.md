# CHANGES — Secuencia de Implementación

> Índice canónico de todos los changes del proyecto **turnos-odontologia**.
> Cada change es atómico: un agente puede implementarlo en una sesión (~4-6 horas).
> **Leer este archivo antes de ejecutar cualquier `/opsx:propose`.**

---

## Cómo usar este documento

1. Identificar el change a implementar (verificar que sus dependencias están en `openspec/changes/archive/`).
2. Leer los docs de la knowledge-base indicados en "Leer antes".
3. Ejecutar `/opsx:propose <nombre-del-change>` (ej. `/opsx:propose C-01-foundation-setup`).
4. Al terminar el change, archivarlo con `/opsx:archive <nombre-del-change>`.
5. Marcar el checkbox `[x]` en este archivo.

---

## Árbol de dependencias

```
C-01 foundation-setup
  └── C-02 core-models-usuario
        └── C-03 auth-login-rbac                         ← desbloquea TODO lo demás
              │
              ├── C-04 gestion-usuarios-admin            ← rama admin (independiente)
              │
              ├── C-05 pacientes-abm
              │     │
              │     └──┐
              │        ├── C-08 turnos-reserva           ← barrera: necesita paciente + profesional
              │        │     ├── C-09 turnos-consulta-agenda
              │        │     └── C-10 turnos-cancelar-atendido-reprogramar
              │        │           └── C-11 frontend-shell-y-agenda
              │        │                 └── C-12 polish-seed-y-hardening
              │
              └── C-06 especialidades-abm
                    └── C-07 profesionales-abm ──────────┘
```

> Nota: C-04 corre en paralelo y converge en C-11; C-08 es la barrera central del dominio.

### Paralelismo por fase

> Cada "gate" es un punto de sincronización. Los changes dentro de un grupo pueden ejecutarse en paralelo.

```
GATE 0: ninguna
  → C-01 foundation-setup                      (solo)

GATE 1: C-01 ✓
  → C-02 core-models-usuario                   (solo)

GATE 2: C-02 ✓
  → C-03 auth-login-rbac                       (solo)

GATE 3: C-03 ✓                                 ← PRIMER FORK (3 paralelos)
  → C-04 gestion-usuarios-admin                [Agente A]
  → C-05 pacientes-abm                         [Agente B]
  → C-06 especialidades-abm                    [Agente C]

GATE 4: C-06 ✓
  → C-07 profesionales-abm                     [Agente C — si C-06 ✓]
  → C-04 y C-05 continúan                      [Agente A/B]

GATE 5: C-05 + C-07 ✓                          ← barrera agenda
  → C-08 turnos-reserva                        (solo — Agente A)
  → C-04 puede cerrar en paralelo              [Agente B — si aún pendiente]

GATE 6: C-08 ✓                                 ← SEGUNDO FORK (2 paralelos)
  → C-09 turnos-consulta-agenda                [Agente A]
  → C-10 turnos-cancelar-atendido-reprogramar  [Agente B]

GATE 7: C-09 + C-10 + C-04 ✓
  → C-11 frontend-shell-y-agenda               [Agente C]

GATE 8: C-11 ✓
  → C-12 polish-seed-y-hardening               (solo — Agente A)
```

### Camino crítico (9 changes — mínimo irreducible)

```
C-01 → C-02 → C-03 → C-06 → C-07 → C-08 → C-09 → C-11 → C-12
```

> Variante con C-10 en lugar de C-09 es equivalente (C-08 → C-10 → C-11 → C-12, 8 changes). El camino con C-06→C-07 es el más largo y define el mínimo irreducible. C-04 y C-05 corren en paralelo sin alargar el crítico si se asignan a Agente A/B.

### Plan óptimo con 3 agentes

```
Paso │ Agente A (Backend Core)                │ Agente B (Backend Dominio)               │ Agente C (Catálogo + Frontend)
─────┼────────────────────────────────────────┼──────────────────────────────────────────┼─────────────────────────────────────────
  1  │ C-01 foundation-setup                  │ —                                        │ —
  2  │ C-02 core-models-usuario               │ —                                        │ —
  3  │ C-03 auth-login-rbac                   │ —                                        │ —
  4  │ C-04 gestion-usuarios-admin            │ C-05 pacientes-abm                       │ C-06 especialidades-abm
  5  │ — (espera barrera)                     │ — (espera barrera)                       │ C-07 profesionales-abm
  6  │ C-08 turnos-reserva                    │ — (si C-04 pendiente, lo cierra)         │ —
  7  │ C-09 turnos-consulta-agenda            │ C-10 turnos-cancelar-atendido-reprogramar│ —
  8  │ —                                      │ —                                        │ C-11 frontend-shell-y-agenda
  9  │ C-12 polish-seed-y-hardening           │ —                                        │ —
```

> Duración estimada: 12 changes / 3 agentes ≈ 9 pasos secuenciales. Con 1 agente: 12 sesiones (~48-72h). Con 3 agentes y gates respetados: ~9 sesiones de elapsed time.

---

## FASE 0 — Cimientos

### [C-01] `foundation-setup`
- **Estado**: `[x]` archivado (2026-10-10)
- **Scope**: Scaffolding completo del monolito + infraestructura base (sin lógica de dominio)
  - Estructura de directorios según `08` (Variante A Java `src/main/java/com/clinica/turnos/...` o Variante B Django `apps/` — elegir una y congelar en `config.yaml`)
  - Backend mínimo con health check `GET /api/health` → `{status:"ok"}`
  - MySQL 8.0 via `docker-compose.yml` (servicios `app` + `mysql`), volumen persistente, `wait-for-db`
  - Migrations infra: Flyway `V1__init.sql` o Django `migrations/` inicializado (sin tablas de dominio aún)
  - `.env.example` y `.env` con `DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD/APP_SECRET/APP_TIMEZONE=America/Argentina/Buenos_Aires/APP_TURNO_DURACION_MIN=30/APP_HORARIO_DESDE=08:00/APP_HORARIO_HASTA=18:00/PORT`
  - `application.properties` / `settings.py` con zona horaria fija, datasource via `${VAR}`, perfil `dev` vs `prod` (`ddl-auto: validate` / `DEBUG=false`)
  - Seguridad base: `SecurityConfig` / `settings.AUTH` con CSRF según SSR vs JWT, bcrypt encoder, CORS si SPA
  - GitHub Actions CI: jobs paralelos `backend-test` + `lint`, cache Maven/pip
  - `.gitignore`, `README.md` con instrucciones `docker compose up` y `mysqldump` de backup (P5:b)
- **Dependencias**: ninguna
- **Governance**: BAJO
- **Leer antes**:
  - `knowledge-base/08_arquitectura_propuesta.md` §Estructura de directorios y §Variables de entorno
  - `knowledge-base/02_descripcion_general.md` §Stack tecnológico y §Arquitectura general
  - `knowledge-base/09_decisiones_y_supuestos.md` §DD-01 y §DD-02

---

## FASE 1 — Autenticación y Usuarios

### [C-02] `core-models-usuario`
- **Estado**: `[x]` archivado (2026-10-10)
- **Scope**: Modelos base Usuario + mixins + repositorios genéricos + migración inicial
  - Modelos: `Usuario` (`id BIGINT PK AUTO_INCREMENT, username VARCHAR(50) UNIQUE, password_hash VARCHAR(255) bcrypt, email VARCHAR(120) UNIQUE, rol ENUM('PACIENTE','ODONTOLOGO','SECRETARIA','ADMIN'), activo BOOLEAN DEFAULT true, created_at, updated_at`)
  - `AuditMixin` / `BaseEntity` con `created_at`, `updated_at` (y `created_by` nullable para Turno en C-08)
  - `BaseRepository[T]` / `JpaRepository` o `Model.objects` manager base, `UnitOfWork` si aplica
  - Migración `V2__usuario.sql` (o `0002_usuario`): tabla `usuario` con índices `UNIQUE(username)`, `UNIQUE(email)`, `INDEX(rol)`
  - Constraint: `rol` validado en entidad + bean validation `@NotNull`
  - Seed mínimo para dev: 1 usuario `admin/admin123` hasheado (solo dev, no prod)
  - Tests: creación usuario, unicidad username/email (409), rol inválido (400), `activo=false` no loguea
- **Dependencias**: C-01
- **Governance**: CRITICO
- **Leer antes**:
  - `knowledge-base/04_modelo_de_datos.md` §Usuario y §Notas de diseño
  - `knowledge-base/08_arquitectura_propuesta.md` §Patrones aplicados
  - `knowledge-base/05_reglas_de_negocio.md` §RN-AU-05 y §RN-AU-06

### [C-03] `auth-login-rbac`
- **Estado**: `[x]` archivado (2026-10-10)
- **Scope**: Autenticación completa + RBAC por rol (US-001, RN-AU-01 a 04)
  - `POST /api/auth/login` (o `POST /login` form) — valida `username`+`password` con bcrypt, rate limiting 5/60s por IP+username, error genérico 401 "Credenciales inválidas" (no revela si usuario existe)
  - Sesión server-side (SSR) o JWT access+refresh en cookie `HttpOnly, Secure, SameSite=Lax` (SPA) — elegir una, documentar en `08`; `GET /api/auth/me` → `{id, username, rol, email}`
  - `POST /api/auth/logout` — invalida sesión / blacklist token
  - `POST /api/auth/refresh` si JWT — rotación refresh token
  - Middleware/Filtro `AuthRequired` en todas las rutas salvo `GET /login`, `POST /login`, `GET /`, `GET /api/health` (RN-AU-01)
  - `PermissionContext` / `@PreAuthorize` / decorador `require_role(...)` con checks `RN-AU-02/03/04` en backend (no solo UI)
  - Redirección por rol tras login: `PACIENTE→/mis-turnos`, `ODONTOLOGO→/agenda`, `SECRETARIA/ADMIN→/agenda` (US-001 CA-2)
  - DTOs: `LoginRequest`, `LoginResponse`, `MeResponse`
  - Tests: login ok, password incorrecto 401, usuario inactivo 401, ruta protegida sin sesión 302/401, RBAC bypass por URL directa 403
- **Dependencias**: C-02
- **Governance**: CRITICO
- **Leer antes**:
  - `knowledge-base/03_actores_y_roles.md` §RBAC — Matriz de permisos y §Rutas públicas
  - `knowledge-base/05_reglas_de_negocio.md` §RN-AU-01 a RN-AU-06
  - `knowledge-base/07_flujos_principales.md` §Flujo 6: Login y RBAC
  - `knowledge-base/06_funcionalidades.md` §US-001 — Login

### [C-04] `gestion-usuarios-admin`
- **Estado**: `[x]` archivado (2026-10-10)
- **Scope**: ABM de usuarios y roles — solo ADMIN (US-002, RN-AU-05/06)
  - Modelos: sin tabla nueva (usa `Usuario` de C-02)
  - Endpoints admin (o vistas SSR): `GET /api/usuarios` (paginado), `POST /api/usuarios` (alta con `username` único, `password` hasheado, `rol` único), `PATCH /api/usuarios/{id}` (edición `rol`, `activo`, `email`), `PATCH /api/usuarios/{id}/desactivar`
  - Validaciones: `username` único (409), `email` único si presente, `rol` en enum, `password` min 8 chars, bcrypt
  - Regla "no auto-desactivar último Admin": `COUNT WHERE rol=ADMIN AND activo=true > 1` antes de desactivar/cambiar rol del último
  - RBAC: `require_role(ADMIN)` en todos los endpoints; 403 para SECRETARIA/ODONTOLOGO/PACIENTE; tests de 403 por rol
  - Auditoría: `created_at/updated_at` + log de cambios de rol
  - Tests: alta ok, username duplicado 409, cambio rol ok, desactivar último admin 400, paciente intenta `GET /api/usuarios` 403
- **Dependencias**: C-03
- **Governance**: ALTO
- **Leer antes**:
  - `knowledge-base/06_funcionalidades.md` §US-002 — ABM Usuarios (Admin)
  - `knowledge-base/03_actores_y_roles.md` §RBAC — Matriz de permisos
  - `knowledge-base/05_reglas_de_negocio.md` §RN-AU-05, RN-AU-06 y §RN-GL-01

---

## FASE 2 — Catálogo y Pacientes

> Los changes C-05 y C-06 pueden ejecutarse en paralelo tras GATE 3. C-07 requiere C-06.

### [C-05] `pacientes-abm`
- **Estado**: `[x]` archivado (2026-10-10)
- **Scope**: ABM de pacientes con validaciones RN-PA y RBAC (US-010, US-011, Flujo 4)
  - Modelos: `Paciente` (`id BIGINT PK, nombre VARCHAR(100) NOT NULL, apellido VARCHAR(100) NOT NULL, dni VARCHAR(20) UNIQUE NOT NULL, telefono VARCHAR(30), email VARCHAR(120), fecha_nacimiento DATE, usuario_id BIGINT FK → Usuario.id NULLABLE UNIQUE, created_at, updated_at`) con índices `UNIQUE(dni)`, `INDEX(apellido, nombre)`
  - `Profesional` no se toca acá — solo Paciente
  - Endpoints (o vistas): `POST /api/pacientes` (alta, RN-PA-02), `GET /api/pacientes?dni=&apellido=&page=` (buscador por DNI/apellido con índice), `GET /api/pacientes/{id}`, `PATCH /api/pacientes/{id}` (edición), `GET /api/pacientes/{id}/turnos` (historial, ver RN-PA-04)
  - Validaciones: DNI único 409 + link a ficha existente en error (US-010 CA-2), `nombre/apellido/dni` obligatorios (400 por campo), `email` formato si presente, `telefono` formato libre, RN-PA-04: no eliminar paciente con turnos futuros RESERVADO (DELETE no existe en v1 — solo `activo` lógico si se agrega, o 400)
  - RBAC: SECRETARIA/ADMIN → CRUD completo; ODONTOLOGO → `GET` solo lectura de sus pacientes del día (filtrado por turnos); PACIENTE → `GET/PATCH` solo su propia ficha (`paciente.usuario_id = current_user.id`, RN-AU-03) — 403 si intenta ver otro
  - Vinculación opcional `paciente.usuario_id`: al crear Usuario PACIENTE validar DNI único contra Paciente existente y linkear si existe (IN-01)
  - Migración `V3__paciente.sql`: tabla `paciente` + FK `usuario_id`
  - Tests: alta ok, DNI duplicado 409, búsqueda por apellido, paciente ve solo su ficha 403, secretaria ve todos, validación email inválido 400
- **Dependencias**: C-03
- **Governance**: MEDIO
- **Leer antes**:
  - `knowledge-base/04_modelo_de_datos.md` §Paciente y §Seed data inicial
  - `knowledge-base/05_reglas_de_negocio.md` §RN-PA-01 a RN-PA-04 y §RN-AU-03
  - `knowledge-base/06_funcionalidades.md` §US-010 — Alta de paciente y §US-011 — Consulta y edición
  - `knowledge-base/07_flujos_principales.md` §Flujo 4: Alta de paciente + primer turno

### [C-06] `especialidades-abm`
- **Estado**: `[x]` archivado (2026-10-10)
- **Scope**: ABM de especialidades — solo ADMIN (US-020, RN-PR-03)
  - Modelos: `Especialidad` (`id BIGINT PK, nombre VARCHAR(80) UNIQUE NOT NULL, descripcion VARCHAR(255), activa BOOLEAN DEFAULT true, created_at`)
  - Endpoints: `GET /api/especialidades` (lista, incluye `activa` filter), `POST /api/especialidades` (nombre único normalizado — trim + case-insensitive check), `PATCH /api/especialidades/{id}` (edición `nombre/descripcion/activa`), `GET /api/especialidades/{id}/profesionales`
  - Validaciones: `nombre` único 409 (normalizar "Ortodoncia" vs "ortodoncia"), `descripcion` opcional, RN-PR-03
  - Regla: no desactivar (`activa=false`) si hay profesionales activos con esa especialidad sin alternativa (verificar `profesional_especialidad` + `profesional.activo=true` y `COUNT especialidades_activas ==1` → 400)
  - RBAC: ADMIN CRUD, SECRETARIA/ODONTOLOGO/PACIENTE solo lectura `GET`
  - Migración `V4__especialidad.sql`: tabla `especialidad` + `UNIQUE(nombre)`
  - Tests: alta ok, nombre duplicado case-insensitive 409, desactivar con profesionales activos 400, secretaria `POST` 403
- **Dependencias**: C-03
- **Governance**: BAJO
- **Leer antes**:
  - `knowledge-base/04_modelo_de_datos.md` §Especialidad
  - `knowledge-base/05_reglas_de_negocio.md` §RN-PR-03 y §RN-PR-04
  - `knowledge-base/06_funcionalidades.md` §US-020 — ABM Especialidades

### [C-07] `profesionales-abm`
- **Estado**: `[x]` archivado (2026-10-10)
- **Scope**: ABM de profesionales con N:M especialidades y vinculación a Usuario (US-021, RN-PR-01/02/04/05)
  - Modelos: `Profesional` (`id BIGINT PK, nombre VARCHAR(100) NOT NULL, apellido VARCHAR(100) NOT NULL, matricula VARCHAR(30) UNIQUE, telefono VARCHAR(30), email VARCHAR(120), usuario_id BIGINT FK → Usuario.id NULLABLE UNIQUE, activo BOOLEAN DEFAULT true, created_at, updated_at`) + `ProfesionalEspecialidad` (`profesional_id, especialidad_id` PK compuesta, FKs)
  - Endpoints: `GET /api/profesionales?especialidad=&activo=`, `POST /api/profesionales` (requiere ≥1 especialidad RN-PR-01, matrícula única), `GET /api/profesionales/{id}`, `PATCH /api/profesionales/{id}` (edición + re-asignar especialidades), `PATCH /api/profesionales/{id}/desactivar`
  - Validaciones: RN-PR-01 (≥1 especialidad, 400 si vacío), RN-PR-02 (matrícula única 409 si se carga), especialidades deben existir y estar `activa=true`, `usuario_id` si se vincula debe ser `rol=ODONTOLOGO` y no estar ya vinculado a otro profesional (409)
  - RN-PR-04: no desactivar si tiene turnos futuros `RESERVADO` (`SELECT COUNT FROM turno WHERE profesional_id=? AND fecha >= hoy AND estado='RESERVADO' >0 → 400`)
  - RBAC: ADMIN CRUD, SECRETARIA/ODONTOLOGO solo `GET`, PACIENTE 403 a POST/PATCH
  - Migración `V5__profesional.sql`: tablas `profesional` + `profesional_especialidad` con FKs y `UNIQUE(matricula)`, `INDEX(apellido)`
  - Tests: alta con 2 especialidades ok, sin especialidad 400, matrícula duplicada 409, vincular usuario no-odontólogo 400, desactivar con turnos futuros 400
- **Dependencias**: C-06
- **Governance**: MEDIO
- **Leer antes**:
  - `knowledge-base/04_modelo_de_datos.md` §Profesional y §ProfesionalEspecialidad (intermedia N:M) y §ERD
  - `knowledge-base/05_reglas_de_negocio.md` §RN-PR-01 a RN-PR-05
  - `knowledge-base/06_funcionalidades.md` §US-021 — ABM Profesionales
  - `knowledge-base/03_actores_y_roles.md` §RBAC — Matriz de permisos

---

## FASE 3 — Agenda Núcleo (MVP P2:a)

> C-08 es la barrera central — concentra RN-TU-01/05. C-09 y C-10 son paralelizables tras C-08.

### [C-08] `turnos-reserva`
- **Estado**: `[x]` archivado (2026-10-10)
- **Scope**: Alta de turno con validación de no-superposición — corazón del MVP (US-031, Flujo 1, RN-TU-01 a 05, RN-PR-05)
  - Modelos: `Turno` (`id BIGINT PK, paciente_id BIGINT FK → Paciente NOT NULL, profesional_id BIGINT FK → Profesional NOT NULL, especialidad_id BIGINT FK → Especialidad NOT NULL, fecha DATE NOT NULL, hora_inicio TIME NOT NULL, hora_fin TIME NOT NULL, estado ENUM('RESERVADO','CANCELADO','ATENDIDO') DEFAULT 'RESERVADO', motivo_cancelacion VARCHAR(255), created_by BIGINT FK → Usuario.id, created_at, updated_at`) + índices `INDEX(profesional_id, fecha, hora_inicio)`, `INDEX(paciente_id, fecha)`, `INDEX(fecha, estado)` y constraints `hora_fin > hora_inicio`, `fecha >= hoy`
  - Servicio `TurnoService.crear(dto)` en transacción ACID serializable: calcula `hora_fin = hora_inicio + APP_TURNO_DURACION_MIN` (RN-TU-02, configurable `APP_TURNO_DURACION_MIN`), valida RN-TU-03 (dentro de `APP_HORARIO_DESDE/HASTA`), RN-TU-04 (`fecha+hora >= ahora`), RN-PR-05 (`profesional_especialidad` existe), RN-TU-01 (no solape profesional: `WHERE profesional_id=? AND fecha=? AND estado='RESERVADO' AND hora_inicio < nuevo_hora_fin AND hora_fin > nuevo_hora_inicio` → 409), RN-TU-05 (no solape paciente idem → 409)
  - Endpoint: `POST /api/turnos` body `{paciente_id, profesional_id, especialidad_id, fecha:YYYY-MM-DD, hora_inicio:HH:mm}` → 201 con `TurnoResponse` o 409 `{"error":"Horario ocupado","sugeridos":["10:30","11:00"]}` + 400 para cada RN violada con mensaje por campo
  - DTOs: `TurnoCreateRequest`, `TurnoResponse` (con `paciente{nombre,dni}`, `profesional{nombre,matricula}`, `especialidad{nombre}`, `estado`)
  - RBAC: PACIENTE solo puede crear para sí mismo (`paciente.usuario_id == current_user.id`, 403 si otro), SECRETARIA/ADMIN para cualquier paciente, ODONTOLOGO 403 a crear
  - Migración `V6__turno.sql`: tabla `turno` + FKs + índices + check constraints
  - Tests: reserva ok 201, solape profesional 409, solape paciente 409, fuera de horario 400, fecha pasada 400, especialidad no pertenece a profesional 400, paciente intenta reservar para otro 403, concurrencia (2 requests simultáneos al mismo hueco — solo 1 gana)
- **Dependencias**: C-05, C-07
- **Governance**: CRITICO
- **Leer antes**:
  - `knowledge-base/04_modelo_de_datos.md` §Turno (entidad central) y §ERD
  - `knowledge-base/05_reglas_de_negocio.md` §RN-TU-01 a RN-TU-05, §RN-TU-09 y §RN-PR-05
  - `knowledge-base/06_funcionalidades.md` §US-031 — Reservar turno
  - `knowledge-base/07_flujos_principales.md` §Flujo 1: Reserva de turno
  - `knowledge-base/08_arquitectura_propuesta.md` §Patrones aplicados (Transacción ACID)

### [C-09] `turnos-consulta-agenda`
- **Estado**: `[x]` archivado (2026-10-10)
- **Scope**: Consulta de agenda por día/semana con scoping por rol (US-030, Flujo 3)
  - Endpoints: `GET /api/turnos?profesional=&fecha=YYYY-MM-DD&desde=&hasta=&estado=RESERVADO,ATENDIDO&page=&size=` (filtros combinables), `GET /api/turnos/{id}` (detalle con paciente/profesional/especialidad), `GET /api/profesionales/{id}/agenda?fecha=YYYY-MM-DD` (huecos libres: genera slots `APP_HORARIO_DESDE→HASTA` step `APP_TURNO_DURACION_MIN` y resta ocupados)
  - Lógica: orden por `hora_inicio ASC`, paginación, `fecha` default `hoy`, `hasta` para vista semana (rango 7 días), cálculo de huecos libres en servicio (no en DB) — retorna `{"ocupados":[...],"libres":["09:00","09:30"]}`
  - RBAC scoping: PACIENTE → `WHERE paciente_id = su_paciente_id` (aislado, RN-AU-03, ignora `profesional` param), ODONTOLOGO → `WHERE profesional_id = su_profesional_id` (forzado, RN-AU-04, 403 si pide otro), SECRETARIA/ADMIN → sin filtro (todos)
  - DTOs: `TurnoListResponse`, `AgendaResponse`, `HuecosResponse`
  - Tests: paciente ve solo suyos, odontólogo ve solo su agenda (intento de ver otra 403/ filtrado), secretaria ve todos, vista semana 7 días, huecos libres excluye RESERVADO pero incluye CANCELADO como libre, fecha inválida 400, sin turnos → lista vacía con mensaje
- **Dependencias**: C-08
- **Governance**: MEDIO
- **Leer antes**:
  - `knowledge-base/06_funcionalidades.md` §US-030 — Ver agenda
  - `knowledge-base/07_flujos_principales.md` §Flujo 3: Consulta de agenda del día
  - `knowledge-base/03_actores_y_roles.md` §RBAC — Matriz de permisos (Turnos — consultar)
  - `knowledge-base/05_reglas_de_negocio.md` §RN-AU-03, RN-AU-04 y §RN-GL-02 (zona horaria)

### [C-10] `turnos-cancelar-atendido-reprogramar`
- **Estado**: `[x]` archivado (2026-10-10)
- **Scope**: Transiciones de estado del turno — cancelar, atendido, reprogramar (US-032, US-033, US-034, Flujos 2 y 5, RN-TU-06/07/08)
  - Endpoints: `PATCH /api/turnos/{id}/cancelar` body `{motivo?: string}` → `estado=CANCELADO` (Flujo 2), `PATCH /api/turnos/{id}/atendido` → `estado=ATENDIDO` (US-034), `POST /api/turnos/{id}/reprogramar` body `{nueva_fecha, nueva_hora_inicio}` → `cancelar(original, motivo:"reprogramado") + crear(nuevo)` en transacción (RN-TU-08, Flujo 5)
  - Validaciones: RN-TU-06 (solo `RESERVADO` puede transicionar; `CANCELADO/ATENDIDO →` 400 "No se puede cancelar"), RN-TU-07 (quién cancela: PACIENTE solo sus turnos `paciente.usuario_id==current_user`, SECRETARIA/ADMIN cualquiera, ODONTOLOGO 403 a cancelar), RN-TU-08 (reprogramar valida RN-TU-01/05 en nuevo horario; si nuevo choca → 409 y original permanece RESERVADO — rollback parcial)
  - RBAC: `cancelar` — PACIENTE (propios) + SECRETARIA/ADMIN; `atendido` — SECRETARIA/ODONTOLOGO (solo su agenda) + ADMIN; `reprogramar` — solo SECRETARIA/ADMIN (PACIENTE 403)
  - Auditoría: `motivo_cancelacion` persistido, `updated_at` actualizado, `created_by` inmutable; reprogramación deja dos registros auditables (original CANCELADO + nuevo RESERVADO)
  - DTOs: `TurnoCancelarRequest`, `TurnoReprogramarRequest`, `TurnoResponse` con `estado` y `motivo`
  - Tests: cancelar RESERVADO ok, cancelar ya CANCELADO 400, paciente cancela ajeno 403, odontólogo cancela 403, marcar ATENDIDO desde RESERVADO ok y desde CANCELADO 400, reprogramar ok (2 turnos), reprogramar a hueco ocupado 409 (original intacto)
- **Dependencias**: C-08
- **Governance**: MEDIO
- **Leer antes**:
  - `knowledge-base/06_funcionalidades.md` §US-032 — Cancelar turno, §US-033 — Reprogramar y §US-034 — Marcar como atendido
  - `knowledge-base/07_flujos_principales.md` §Flujo 2: Cancelación y §Flujo 5: Reprogramación
  - `knowledge-base/05_reglas_de_negocio.md` §RN-TU-06 a RN-TU-08 y §RN-GL-03 (soft-state)
  - `knowledge-base/04_modelo_de_datos.md` §Turno §estado y §motivo_cancelacion

---

## FASE 4 — Presentación y Cierre

### [C-11] `frontend-shell-y-agenda`
- **Estado**: `[x]` archivado (2026-10-11)
- **Scope**: UI web responsive — shell + login + agenda + formularios de turnos/pacientes (cubre US-001/010/011/030/031/032 visibles)
  - Shell: layout con sidebar/header, menú por rol (PACIENTE: Mis turnos / Nuevo turno; ODONTOLOGO: Mi agenda; SECRETARIA: Agenda / Pacientes / Turnos; ADMIN: + Usuarios / Especialidades / Profesionales), `GET /` redirige a `/login` si no autenticado y a dashboard por rol si autenticado
  - Páginas: `/login` (form username+password, error genérico, RN-AU-01), `/agenda` (vista día/semana, filtro profesional+fecha, lista cronológica por `hora_inicio` con paciente/especialidad/estado, huecos libres destacados), `/turnos/nuevo` (selector paciente autocomplete por DNI/apellido, profesional, especialidad filtrada por profesional, fecha, hora_inicio con slots libres de `GET /api/turnos/huecos` — validación inline RN-PR-05/RN-TU-03), `/turnos/{id}` (detalle + botones Cancelar/Reprogramar/Atendido según rol y estado), `/pacientes` (buscador + alta inline sin salir del flujo — Flujo 4), `/pacientes/{id}` (ficha + historial turnos)
  - Estado: Zustand/Redux o Thymeleaf model según variante SSR/SPA elegida en C-01; manejo 401→redirect `/login`, 403→"No autorizado", 409→"Horario ocupado, probá 10:30" con sugeridos, 400→mensajes por campo
  - Componentes: `AgendaDia`, `AgendaSemana`, `TurnoCard` (estado color: RESERVADO azul, CANCELADO tachado gris, ATENDIDO verde), `HuecosLibres`, `PacienteAutocomplete`, `ProfesionalSelect` (filtra especialidades), `EstadoBadge`
  - RBAC en frontend (ocultar botones) + backend ya valida (RN-AU-02) — no confiar solo en UI
  - Responsive: Bootstrap/Tailwind, mobile-first (secretaria en mostrador con tablet)
  - Tests: e2e Playwright/Cypress — login por rol, crear paciente+turno (Flujo 4), ver agenda día, cancelar turno (Flujo 2), reprogramar (Flujo 5), paciente no ve agenda ajena
- **Dependencias**: C-04, C-09, C-10
- **Governance**: MEDIO
- **Leer antes**:
  - `knowledge-base/07_flujos_principales.md` §Flujo 1 a §Flujo 6 (todos — UI debe reflejarlos)
  - `knowledge-base/03_actores_y_roles.md` §RBAC — Matriz de permisos y §Jerarquía
  - `knowledge-base/08_arquitectura_propuesta.md` §Estructura de directorios (templates/static) y §Seguridad (CSRF)
  - `knowledge-base/06_funcionalidades.md` §US-030 a §US-034 (criterios de aceptación visibles)

### [C-12] `polish-seed-y-hardening`
- **Estado**: `[x]` archivado (2026-10-11)
- **Scope**: Seed, hardening, validación global y cierre de MVP (P5:b mantenibilidad, RN-GL)
  - Seed `V7__seed.sql` o `seed.py`: 5 especialidades (Ortodoncia, Endodoncia, Cirugía, Periodoncia, Odontología General), 1 profesional vinculado a usuario odontólogo + 2 especialidades, 1 paciente vinculado a usuario paciente, 4 usuarios (`admin/admin123`, `secretaria`, `odontologo`, `paciente` — bcrypt, solo dev), 3 turnos de ejemplo (1 RESERVADO hoy, 1 CANCELADO, 1 ATENDIDO)
  - Validación global RN-GL-01: Bean Validation / Django validators en todos los DTOs con mensajes por campo, nunca 500 por input inválido (400 con `fieldErrors`)
  - Zona horaria: `APP_TIMEZONE=America/Argentina/Buenos_Aires` forzada en JVM/Django (`TimeZone.setDefault`, `settings.TIME_ZONE`), tests que crean turno en borde de horario con TZ correcta (RN-GL-02)
  - Manejo de errores unificado: `@ControllerAdvice` / middleware con envelope `{"error","message","fieldErrors","sugeridos"}` para 400/401/403/409
  - Backups: script `scripts/backup.sh` (`mysqldump` diario) + doc en `README.md` §Deploy, volumen Docker con snapshot
  - Observabilidad mínima: `/api/health` + logs estructurados (SLF4J/Django logging) + `README` de cómo correr `docker compose up` y migrations
  - Tests de integración: Testcontainers MySQL (o `pytest-django` con `mysql` service) — flujo completo alta paciente → reserva → consulta agenda → cancelar → reprogramar → atendido (cubriendo Flujos 1-5 en un test)
  - Limpieza: `ddl-auto: validate` en prod, secrets fuera de git, `.env` no commiteado, `APP_SECRET` random 32 chars
  - Documentación: `README.md` final con stack elegido, cómo correr, credenciales dev, env vars, y sección "Fuera de alcance v1" (recordatorios, HC, pagos — v2)
- **Dependencias**: C-11
- **Governance**: BAJO
- **Leer antes**:
  - `knowledge-base/04_modelo_de_datos.md` §Seed data inicial y §Notas de diseño
  - `knowledge-base/05_reglas_de_negocio.md` §RN-GL-01 a RN-GL-03 y §RN-TU-10 (anticipación opcional)
  - `knowledge-base/08_arquitectura_propuesta.md` §Variables de entorno y §Decisiones de infra
  - `knowledge-base/09_decisiones_y_supuestos.md` §SU-01 a §SU-06 y §DD-03/DD-06
  - `knowledge-base/10_preguntas_abiertas.md` (validar que 3 Altas estén cerradas o documentadas como supuestos)

---

## Resumen

| Fase | Changes | IDs | Paralelizable |
|------|---------|-----|---------------|
| FASE 0 — Cimientos | 1 | C-01 | No |
| FASE 1 — Autenticación y Usuarios | 3 | C-02, C-03, C-04 | C-04 con FASE 2 |
| FASE 2 — Catálogo y Pacientes | 3 | C-05, C-06, C-07 | C-05 ∥ C-06, C-07 tras C-06 |
| FASE 3 — Agenda Núcleo | 3 | C-08, C-09, C-10 | C-09 ∥ C-10 tras C-08 |
| FASE 4 — Presentación y Cierre | 2 | C-11, C-12 | No |
| **Total** | **12** | **C-01 → C-12** | **3 gates con fork** |

**Gates de paralelismo**: 9 gates (GATE 0 a GATE 8), 2 forks reales (GATE 3 con 3 paralelos, GATE 6 con 2 paralelos)
**Camino crítico**: 9 changes (C-01→C-02→C-03→C-06→C-07→C-08→C-09→C-11→C-12) — mínimo irreducible para producción
**Governance**: CRITICO×3 (C-02, C-03, C-08), ALTO×1 (C-04), MEDIO×5 (C-05, C-07, C-09, C-10, C-11), BAJO×3 (C-01, C-06, C-12)

> **Primer change recomendado**: `C-01` (`foundation-setup`) — sin él nada compila ni migra. Para arrancar: `/opsx:propose C-01-foundation-setup`

> **Nota de priorización (P5:b + P2:a)**: Este roadmap prioriza mantenibilidad (capas controller→service→repository, DTOs, validación en servicio, migraciones versionadas) y MVP solo-agenda (sin recordatorios/HC/pagos/multi-sede — explícitamente fuera de alcance en `01`). Cada change es reversible y testeable aislado.
