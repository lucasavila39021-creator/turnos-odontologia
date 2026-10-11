# especialidades Specification

## Purpose
Catálogo de especialidades odontológicas (alta, lista, edición y desactivación controlada) con nombre único normalizado y acceso por rol, para que los profesionales puedan vincularse a especialidades y los turnos referenciarlas.

## Requirements

### Requirement: Alta de especialidad — campos y validaciones (US-020 CA-1)

El sistema SHALL exponer `POST /api/especialidades` que crea una especialidad con `nombre` (1..80, obligatorio, se guarda con `trim`) y `descripcion` (máx 255, opcional, `trim`; ausente o en blanco → `null`). Éxito → 201 con `EspecialidadResponse`.

#### Scenario: Alta ok como admin

- **WHEN** `ADMIN` envía `POST /api/especialidades` con `{"nombre":"Ortodoncia","descripcion":"Aparatos y alineación"}`
- **THEN** responde 201 con `EspecialidadResponse` con esos valores, `activa=true` y `created_at` presente.

#### Scenario: Nombre faltante devuelve 400

- **WHEN** se envía `POST /api/especialidades` sin `nombre` (ausente, null, vacío o solo espacios)
- **THEN** responde 400 con `fieldErrors.nombre` y mensaje claro; nunca 500.

#### Scenario: Descripcion demasiado larga devuelve 400

- **WHEN** se envía `POST /api/especialidades` con `descripcion` de más de 255 caracteres
- **THEN** responde 400 con `fieldErrors.descripcion` y no crea la fila.

### Requirement: Alta de especialidad — nombre duplicado normalizado 409 (RN-PR-03)

El sistema SHALL responder 409 problem-details con `fieldErrors.nombre` cuando el `nombre` normalizado (`trim` + comparación case-insensitive) ya exista, incluida carrera concurrente (red final: constraint `UNIQUE` en DB).

#### Scenario: Duplicado case-insensitive devuelve 409

- **WHEN** existe "Ortodoncia" y se envía `POST /api/especialidades` con `{"nombre":"  ortodoncia  "}`
- **THEN** responde 409 con `fieldErrors.nombre` y no crea fila nueva.

### Requirement: Lista paginada con filtro por activa

El sistema SHALL exponer `GET /api/especialidades?activa=&page=&size=&sort=` que retorna `Page<EspecialidadResponse>` (default `size=20`, `sort=nombre,asc`); `activa=true|false` filtra por estado; sin el parámetro retorna todas; sin resultados → página vacía 200 con `content:[]`.

#### Scenario: Lista con filtro activa=false

- **WHEN** un rol autenticado llama `GET /api/especialidades?activa=false` existiendo una activa y una inactiva
- **THEN** responde 200 con solo la inactiva en `content`.

#### Scenario: Lista ordenada por nombre

- **WHEN** se llama `GET /api/especialidades` existiendo "Endodoncia" y "Ortodoncia"
- **THEN** responde 200 con `content` ordenado `Endodoncia, Ortodoncia`.

### Requirement: Edición parcial de especialidad (US-020)

El sistema SHALL exponer `PATCH /api/especialidades/{id}` que edita `nombre` (validaciones idénticas al alta, incluido 409 normalizado contra otras filas), `descripcion` (máx 255, blanco → `null`) y `activa` (booleano); 404 si el id no existe; éxito → 200 con la entidad actualizada.

#### Scenario: Edición de descripcion ok

- **WHEN** `ADMIN` envía `PATCH /api/especialidades/{id}` con `{"descripcion":"Nueva descripción"}`
- **THEN** responde 200 con la descripción actualizada y `nombre` intacto.

#### Scenario: Renombrar a nombre existente devuelve 409

- **WHEN** `ADMIN` envía `PATCH /api/especialidades/{id-A}` con el `nombre` (normalizado) de la especialidad B
- **THEN** responde 409 con `fieldErrors.nombre` y la fila A queda intacta.

#### Scenario: Id inexistente devuelve 404

- **WHEN** `ADMIN` envía `PATCH /api/especialidades/999999` con un cuerpo válido
- **THEN** responde 404 y no crea ni modifica filas.

### Requirement: No desactivar con profesionales activos sin alternativa (US-020 CA-2)

El sistema SHALL rechazar con 400 problem-details el `PATCH /api/especialidades/{id}` con `{"activa":false}` cuando exista al menos un profesional `activo=true` vinculado a esta especialidad cuya única especialidad activa sea esta; reactivar (`activa=true`) siempre está permitido.

#### Scenario: Desactivar con profesional cautivo devuelve 400

- **WHEN** el profesional P (activo, solo vinculado a la especialidad E activa) existe y `ADMIN` envía `PATCH /api/especialidades/{id-E}` con `{"activa":false}`
- **THEN** responde 400 con mensaje claro, E sigue `activa=true` y P intacto.

#### Scenario: Desactivar sin profesionales vinculados ok

- **WHEN** `ADMIN` envía `PATCH /api/especialidades/{id}` con `{"activa":false}` y ningún profesional activo depende solo de ella
- **THEN** responde 200 con `activa=false`.

### Requirement: Profesionales por especialidad — contrato congelado hasta C-07

El sistema SHALL exponer `GET /api/especialidades/{id}/profesionales?page=&size=` que retorna página de profesionales vinculados ordenados por `apellido, nombre` (200; 404 si la especialidad no existe); desde C-07 lista los profesionales reales con `activo` cualquiera (filtro opcional `?activo=`); paginación, orden y RBAC iguales al contrato v1.

#### Scenario: Lista real de vinculados

- **WHEN** un rol autenticado llama `GET /api/especialidades/{id}/profesionales` existiendo 2 profesionales vinculados (uno activo, uno inactivo)
- **THEN** responde 200 con ambos en `content` ordenados por `apellido, nombre`.

#### Scenario: Lista vacía sin vinculados

- **WHEN** un rol autenticado llama `GET /api/especialidades/{id}/profesionales` con especialidad existente sin profesionales
- **THEN** responde 200 con `content:[]` y metadatos de paginación.

#### Scenario: Lista vacía pre-C-07

- **WHEN** un rol autenticado llama `GET /api/especialidades/{id}/profesionales` con especialidad existente
- **THEN** responde 200 con `content:[]` y metadatos de paginación.

#### Scenario: Id inexistente devuelve 404

- **WHEN** se llama `GET /api/especialidades/999999/profesionales`
- **THEN** responde 404.

### Requirement: RBAC — ADMIN CRUD, resto solo lectura (RN-AU-05, RN-AU-02)

El sistema SHALL hacer cumplir en backend: `ADMIN` → acceso completo a los 4 endpoints; `SECRETARIA`, `ODONTOLOGO` y `PACIENTE` → solo `GET` (lista y profesionales), 403 ante `POST/PATCH`; no autenticado → 401 JSON / 302 según `Accept`.

#### Scenario: Secretaria POST devuelve 403

- **WHEN** `SECRETARIA` envía `POST /api/especialidades` con un cuerpo válido
- **THEN** responde 403 y no crea la fila.

#### Scenario: Paciente puede listar

- **WHEN** `PACIENTE` autenticado llama `GET /api/especialidades`
- **THEN** responde 200 con la página de especialidades.
