# Modelo de Datos — Turnos Odontología

## Dominios

| Dominio | Descripción |
|---------|-------------|
| **Usuarios y Acceso** | Usuarios, roles, autenticación |
| **Catálogo** | Especialidades y Profesionales |
| **Pacientes** | Ficha de paciente |
| **Agenda** | Turnos (entidad central) |

## ERD (textual)

```
Usuario 1──0..1 Profesional  (usuario con rol ODONTOLOGO puede tener ficha profesional)
Profesional *──* Especialidad (N:M vía profesional_especialidad)
Paciente 1──* Turno
Profesional 1──* Turno
Especialidad 1──* Turno (denormalizada para trazabilidad, o derivada de profesional)
Usuario 1──0..* Turno (como paciente, si paciente está vinculado a usuario)
```

```
[Usuario] ──< Turno >── [Paciente]
                │
                ├── [Profesional] ──< ProfesionalEspecialidad >── [Especialidad]
                │
                └── [Especialidad] (FK directa para auditoría)
```

## Entidades

### Usuario
- `id` BIGINT PK AUTO_INCREMENT
- `username` VARCHAR(50) UNIQUE NOT NULL
- `password_hash` VARCHAR(255) NOT NULL (bcrypt)
- `email` VARCHAR(120) UNIQUE
- `rol` ENUM('PACIENTE','ODONTOLOGO','SECRETARIA','ADMIN') NOT NULL
- `activo` BOOLEAN DEFAULT true
- `created_at` DATETIME, `updated_at` DATETIME
- Índices: `UNIQUE(username)`, `UNIQUE(email)`
- Relaciones: 1-0..1 con Profesional (via `profesional.usuario_id`), 1-0..1 con Paciente (opcional `paciente.usuario_id`)

### Paciente
- `id` BIGINT PK
- `nombre` VARCHAR(100) NOT NULL
- `apellido` VARCHAR(100) NOT NULL
- `dni` VARCHAR(20) UNIQUE NOT NULL
- `telefono` VARCHAR(30)
- `email` VARCHAR(120)
- `fecha_nacimiento` DATE
- `usuario_id` BIGINT FK → Usuario.id NULLABLE (si el paciente tiene cuenta)
- `created_at`, `updated_at`
- Índices: `UNIQUE(dni)`, `INDEX(apellido, nombre)`
- Constraints: DNI no vacío, email válido si presente

### Especialidad
- `id` BIGINT PK
- `nombre` VARCHAR(80) UNIQUE NOT NULL (ej. Ortodoncia, Endodoncia)
- `descripcion` VARCHAR(255)
- `activa` BOOLEAN DEFAULT true
- `created_at`
- Índices: `UNIQUE(nombre)`

### Profesional
- `id` BIGINT PK
- `nombre` VARCHAR(100) NOT NULL
- `apellido` VARCHAR(100) NOT NULL
- `matricula` VARCHAR(30) UNIQUE
- `telefono` VARCHAR(30)
- `email` VARCHAR(120)
- `usuario_id` BIGINT FK → Usuario.id NULLABLE UNIQUE (si tiene login)
- `activo` BOOLEAN DEFAULT true
- `created_at`, `updated_at`
- Índices: `UNIQUE(matricula)`, `INDEX(apellido)`
- Relaciones: N:M con Especialidad vía tabla intermedia

### ProfesionalEspecialidad (intermedia N:M)
- `profesional_id` BIGINT FK → Profesional.id
- `especialidad_id` BIGINT FK → Especialidad.id
- PK compuesta (`profesional_id`, `especialidad_id`)

### Turno (entidad central)
- `id` BIGINT PK
- `paciente_id` BIGINT FK → Paciente.id NOT NULL
- `profesional_id` BIGINT FK → Profesional.id NOT NULL
- `especialidad_id` BIGINT FK → Especialidad.id NOT NULL
- `fecha` DATE NOT NULL
- `hora_inicio` TIME NOT NULL
- `hora_fin` TIME NOT NULL (derivado de `hora_inicio + duración`, pero persistido para consultas)
- `estado` ENUM('RESERVADO','CANCELADO','ATENDIDO') DEFAULT 'RESERVADO' NOT NULL
- `motivo_cancelacion` VARCHAR(255) NULLABLE
- `created_by` BIGINT FK → Usuario.id (quién creó)
- `created_at`, `updated_at`
- Índices:
  - `INDEX(profesional_id, fecha, hora_inicio)` — búsqueda de agenda y validación de superposición
  - `INDEX(paciente_id, fecha)` — turnos del paciente
  - `INDEX(fecha, estado)` — agenda del día
- Constraints:
  - `hora_fin > hora_inicio`
  - `fecha >= CURRENT_DATE` al crear (RN-TU-04)
  - Validación de no-superposición a nivel servicio + índice parcial (ver RN-TU-01)

## Seed data inicial

- Roles: 4 usuarios de prueba (admin/admin, secretaria, odontologo, paciente) — password `admin123` hasheado, solo para dev
- Especialidades: Ortodoncia, Endodoncia, Cirugía, Periodoncia, Odontología General
- Profesional de ejemplo: vinculado a usuario odontólogo + 2 especialidades
- Paciente de ejemplo: vinculado a usuario paciente

## Notas de diseño

- **Soft delete:** no en v1; cancelación es cambio de estado, no borrado físico (trazabilidad)
- **Duración fija:** `hora_fin` se calcula en servicio según duración estándar (ej. 30 min) — no configurable por turno en v1 (RN-TU-02)
- **Auditoría mínima:** `created_by` + `created_at` en Turno; v2 puede agregar `updated_by` y tabla de auditoría
