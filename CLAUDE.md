# Turnos Odontología — Instrucciones para Agentes

> Este archivo (y su copia `CLAUDE.md`) es lo PRIMERO que todo agente lee al entrar al repo.
> Generado a partir de `knowledge-base/` y `CHANGES.md`. No editar a mano sin re-sincronizar ambos archivos.

---

## Stack Tecnológico

Detalle completo: [knowledge-base/02_descripcion_general.md](knowledge-base/02_descripcion_general.md)

### Opción A — Java (recomendada si el equipo es Java)

| Capa | Tecnología | Versión mínima | Notas |
|------|------------|----------------|-------|
| Backend | Java + Spring Boot | 17 / 3.x | MVC, Spring Data JPA, Bean Validation |
| Frontend | Thymeleaf + Bootstrap o React | 3.x / 18 | SSR simple para v1 |
| BD | MySQL | 8.0 | InnoDB, FKs, índices por profesional+fecha |
| Autenticación | Spring Security (sesión o JWT) | 6.x | RBAC por rol |
| Build | Maven/Gradle | — | — |
| Deploy | JAR ejecutable + MySQL (Docker opcional) | — | `java -jar` + `application.properties` |

### Opción B — Python (equivalente si el equipo es Python)

| Capa | Tecnología | Versión mínima | Notas |
|------|------------|----------------|-------|
| Backend | Python + Django (o FastAPI) | 3.11 / 5.x | ORM, Admin, Validación |
| Frontend | Django Templates + Bootstrap o React | — | SSR simple para v1 |
| BD | MySQL | 8.0 | mysqlclient / SQLAlchemy |
| Autenticación | Django Auth o JWT (FastAPI) | — | RBAC por grupo/rol |
| Deploy | `manage.py` / Uvicorn + MySQL | — | `requirements.txt`, `.env` |

> Decisión DD-01/DD-02: elegir **una** variante y congelar en `knowledge-base/08_arquitectura_propuesta.md`. No documentar dos en paralelo.

---

## Base de Conocimiento

La fuente de verdad del dominio vive en `knowledge-base/`. **Leé el archivo relevante ANTES de implementar.**

| Archivo | Cuándo leerlo |
|---------|---------------|
| [01_vision_y_objetivos.md](knowledge-base/01_vision_y_objetivos.md) | Entender propósito y alcance v1 |
| [03_actores_y_roles.md](knowledge-base/03_actores_y_roles.md) | Auth, RBAC (4 roles), permisos |
| [04_modelo_de_datos.md](knowledge-base/04_modelo_de_datos.md) | Entidades, ERD, migraciones, seed |
| [05_reglas_de_negocio.md](knowledge-base/05_reglas_de_negocio.md) | Reglas codificadas RN-AU/PR/PA/TU/GL |
| [06_funcionalidades.md](knowledge-base/06_funcionalidades.md) | Historias de usuario por épica (US-001..034) |
| [07_flujos_principales.md](knowledge-base/07_flujos_principales.md) | Flujos E2E (reserva, cancelar, agenda, reprogramar, login) |
| [08_arquitectura_propuesta.md](knowledge-base/08_arquitectura_propuesta.md) | Patrones, estructura, seguridad, env vars |
| [10_preguntas_abiertas.md](knowledge-base/10_preguntas_abiertas.md) | ⚠️ Inconsistencias + 11 preguntas (3 Alta) — resolver ANTES de codear |

> ⚠️ Resolver las preguntas de prioridad **Alta** de `10_preguntas_abiertas.md` (horario por profesional/día, sedes, paciente con/sin cuenta) antes de arrancar el primer change.

---

## Skills Disponibles

Fuente de verdad: `.atl/skill-registry.md` (generado por `skill-registry` — no versionado).

| Agente | Rol | Skills que carga |
|--------|-----|------------------|
| **Backend Core** | Spring Boot / capas / DTOs / TX | `springboot-patterns`, `mysql`, `database-migrations` |
| **Backend Sec** | Auth/RBAC/validación/hardening | `springboot-security` |
| **Frontend** | UI agenda/turnos | `frontend-design` |
| **Testing** | Integración/E2E | `webapp-testing` |
| **Orquestación** | SDD / OPSX / docs | `kb-creator`, `roadmap-generator`, `agents-md-generator`, `skill-registry`, `find-skills` (foundation) |

Cargá la skill correspondiente al contexto ANTES de escribir código.

> Los compact rules de cada skill los resuelve el orquestador desde `.atl/skill-registry.md` (generado por `skill-registry`; no versionado — no está en el repo). Esta tabla solo mapea skill→rol.

Ver registry: [.atl/skill-registry.md](.atl/skill-registry.md) — 6 skills con triggers y compact rules pre-digeridos.

---

## Roadmap de Changes

El plan de implementación completo está en [CHANGES.md](CHANGES.md). Resumen:

- **Total**: 12 changes en 5 fases.
- **Camino crítico** (9): `C-01 → C-02 → C-03 → C-06 → C-07 → C-08 → C-09 → C-11 → C-12` (variante C-10 equivalente).
- **Primer change**: `C-01` (`foundation-setup`) — scaffolding monolito + MySQL + health check.
- **Gates**: 9 gates, 2 forks (GATE 3: C-04∥C-05∥C-06, GATE 6: C-09∥C-10).

**Antes de cualquier `/opsx:propose`**: leé [CHANGES.md](CHANGES.md), identificá las dependencias del change y los archivos de "Leer antes".

Dependencias clave: `C-01 → C-02 → C-03` desbloquea todo; `C-05 + C-07 → C-08` es la barrera de agenda (no-superposición).

---

## Reglas Duras (específicas del proyecto)

> `~/.claude/CLAUDE.md` global no encontrado en este entorno — ninguna regla universal heredada. Las reglas universales que normalmente vivirían en el global se incluyen acá como proyecto hasta que se agreguen al global. Si en el futuro el global aparece, referenciarlo y no duplicar.

Son contrato; romperlas es un defecto. Formato `NUNCA X → hacer Y`:

- **NUNCA** commitear o pushear sin pedido explícito del usuario → solo `git status/diff`, commits solo cuando el usuario diga.
- **NUNCA** buildear/instalar dependencias sin pedido → proponer y esperar OK (`npm install`, `pip install`, `mvn package` solo si el usuario autoriza).
- **NUNCA** romper conventional commits → mensajes en español, formato `feat:`, `fix:`, `docs:`, etc., sin co-autoría IA.
- **NUNCA** exponer entidad JPA/Django Model directo en API → usar DTOs `CreateXRequest`/`XResponse` con `@Valid` y mapper `from()`.
- **NUNCA** inyección por campo → usar constructor injection (Spring) / inyección explícita.
- **NUNCA** migración con `NOT NULL` sin default en tabla con datos → hacer nullable o con default, forward-only, nunca editar migración deployada (Flyway `V{n}__` / Django `migrations`).
- **NUNCA** hardcodear secrets/DB password → usar env vars (`DB_PASSWORD`, `APP_SECRET` en `.env`, `.env` nunca commiteado, solo `.env.example` versionado).
- **NUNCA** crear/validar turno fuera de transacción ACID → validar RN-TU-01/05 en `TurnoService` con TX serializable y query `hora_inicio < :fin AND hora_fin > :inicio`.
- **NUNCA** ignorar zona horaria → forzar `America/Argentina/Buenos_Aires` en JVM (`TimeZone.setDefault`) / Django (`TIME_ZONE`), persistir `DATETIME` sin conversión implícita (RN-GL-02).
- **NUNCA** testear DB con mocks → usar DB real/Testcontainers MySQL para integración; unit puro solo para lógica sin IO.

---

## Flujo de Trabajo

```
1. Leer la KB relevante (knowledge-base/)        → entender el dominio
2. Identificar el change en CHANGES.md           → respetar dependencias y gates
3. /opsx:propose C-NN-nombre                     → proposal + design + specs + tasks
4. Implementar las tasks (cargando skills)       → respetando las reglas duras
5. /opsx:archive C-NN-nombre + marcar [x]        → cerrar el change y actualizar CHANGES.md
```

Aplicar TODAS las reglas duras en cada paso. Ante conflicto entre la KB y este archivo, las reglas duras prevalecen.

> Para sub-agentes: el orquestador inyecta los compact rules de `.atl/skill-registry.md` en el prompt. No leer `SKILL.md` individuales.
