# Descripción General — Turnos Odontología

## Stack tecnológico

Orientado a entorno universitario, prioridad **mantenibilidad** (P5:b). Se documentan dos variantes equivalentes; elegir una al iniciar implementación.

### Opción A — Java (recomendada si el equipo es Java)

| Capa | Tecnología | Versión mínima | Notas |
|------|------------|----------------|-------|
| Backend | Java + Spring Boot | 17 / 3.x | MVC, Spring Data JPA, Bean Validation |
| Frontend | Thymeleaf + Bootstrap o React (si SPA) | 3.x / 18 | SSR simple para v1 es suficiente |
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

> **Decisión trazada en DD-01 / DD-02** — ambas variantes comparten modelo de datos, reglas y flujos. No documentar dos implementaciones en paralelo; elegir una y seguir `08`.

## Arquitectura general

**Monolito modular** — 1 deploy, 1 base de datos, capas separadas. Suficiente para escala `small` (<50 usuarios) y fácil de mantener/enseñar en contexto universitario.

```
[ Navegador ] → [ Web App (Controllers/Templates o SPA) ] → [ Service Layer ] → [ Repository/JPA ] → [ MySQL ]
                     ↕
                [ Spring Security / Django Auth — RBAC ]
```

**Justificación**
- Sin necesidad de microservicios, colas o caché distribuido en v1 (P0-scale:a, P5:b)
- Transacciones ACID de MySQL garantizan no-superposición sin lógica distribuida
- Mantenibilidad: capas claras (controller → service → repository) facilitan agregar recordatorios/HC en v2 sin reescribir

## Integraciones externas

| Servicio | Propósito | Tipo | Estado v1 |
|----------|-----------|------|-----------|
| — | — | — | Ninguna integración externa en v1 |

> Integraciones previstas v2: email/WhatsApp (recordatorios), SSO universitario. Ver `10_preguntas_abiertas.md`.

## API REST (si aplica — referencia para SPA)

Si el frontend es SPA, exponer REST bajo `/api/v1`:

| Recurso | Endpoints principales |
|---------|------------------------|
| Auth | `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/me` |
| Pacientes | `GET /api/pacientes`, `POST /api/pacientes`, `GET /api/pacientes/{id}` |
| Profesionales | `GET /api/profesionales`, `POST /api/profesionales`, `GET /api/profesionales/{id}/agenda?fecha=YYYY-MM-DD` |
| Especialidades | `GET /api/especialidades`, `POST /api/especialidades` |
| Turnos | `GET /api/turnos?profesional=&desde=&hasta=`, `POST /api/turnos`, `DELETE /api/turnos/{id}`, `PATCH /api/turnos/{id}/cancelar` |
| Usuarios | `GET /api/usuarios`, `POST /api/usuarios`, `PATCH /api/usuarios/{id}/rol` |

Si es SSR (Thymeleaf/Django Templates), las mismas operaciones vía formularios y vistas, sin API pública separada.
