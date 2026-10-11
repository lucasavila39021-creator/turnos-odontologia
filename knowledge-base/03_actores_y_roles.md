# Actores y Roles — Turnos Odontología

## Actores del sistema

| Actor | Descripción | Cómo interactúa |
|-------|-------------|-----------------|
| **Paciente** | Persona que reserva atención odontológica. Puede ser usuario registrado o carga por secretaria. | Web: login → ve/crea/cancela sus turnos |
| **Secretaria** | Personal administrativo que centraliza la agenda. Rol operativo clave. | Web: gestiona todos los turnos, pacientes y profesionales |
| **Odontólogo** | Profesional que atiende. Ve su agenda, no gestiona la de otros. | Web: consulta su agenda del día/semana, ve detalle de paciente |
| **Admin** | Responsable de configuración (puede ser secretaria senior o coordinador). | Web: ABM usuarios, roles, especialidades, profesionales |

> **Suposición SU-01:** Un odontólogo es también un Usuario con rol `ODONTOLOGO`; un paciente puede ser Usuario con rol `PACIENTE` o solo un registro `Paciente` creado por secretaria sin cuenta.

## RBAC — Matriz de permisos

| Recurso | Paciente | Odontólogo | Secretaria | Admin |
|---------|----------|------------|------------|-------|
| **Turnos — crear** | Sí (solo propios) | No | Sí (para cualquier paciente) | Sí |
| **Turnos — cancelar** | Sí (solo propios, si RN-TU-07 lo permite) | No | Sí (cualquiera) | Sí |
| **Turnos — consultar** | Solo propios | Solo su agenda | Todos | Todos |
| **Turnos — reprogramar** | No | No | Sí | Sí |
| **Pacientes — ABM** | Ve/edita solo su ficha | Solo lectura de sus pacientes del día | CRUD completo | CRUD completo |
| **Profesionales — ABM** | No | No | Lectura | CRUD |
| **Especialidades — ABM** | No | No | Lectura | CRUD |
| **Usuarios/Roles — ABM** | No | No | No | CRUD |
| **Agenda por profesional** | No | Solo la propia | Todas | Todas |

**Reglas de RBAC**
- RN-AU-01: toda ruta salvo login requiere autenticación
- RN-AU-02: autorización por rol en backend (no solo ocultar botón en frontend)
- RN-AU-03: paciente no puede ver turnos de otros pacientes (aislamiento por `paciente_id = current_user`)

## Rutas públicas (sin autenticación)

- `GET /login` — formulario de login
- `POST /login` — autenticar
- `GET /` — landing / redirección a login si no autenticado
- `GET /api/health` (si API) — health check

Todo lo demás requiere sesión válida.

## Jerarquía y herencia

- RBAC simple (rol → permisos), sin ABAC en v1
- Sin herencia entre roles (un Admin no hereda automáticamente permisos de Odontólogo; se asignan explícitos si necesita)
- Un usuario puede tener un solo rol en v1 (simplifica; ver `10` para multi-rol v2)
