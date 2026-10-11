# Spec Delta — pacientes

## Purpose

Gestión de la ficha de paciente (alta, búsqueda, consulta, edición e historial de turnos) con DNI único, validaciones RN-PA-01..04 y acceso por rol con aislamiento de ficha propia, para que la agenda pueda asignar turnos a pacientes identificados y vinculados opcionalmente a su cuenta de usuario.

## ADDED Requirements

### Requirement: Alta de paciente — campos y validaciones (US-010, RN-PA-01/02)

El sistema SHALL exponer `POST /api/pacientes` que crea una ficha con `nombre` (1..100, obligatorio), `apellido` (1..100, obligatorio), `dni` (1..20, obligatorio, único), `telefono` (máx 30, opcional), `email` (máx 120, opcional, formato email) y `fecha_nacimiento` (DATE `YYYY-MM-DD`, opcional, no futura). Éxito → 201 con `PacienteResponse`.

#### Scenario: Alta ok como secretaria

- **WHEN** `SECRETARIA` envía `POST /api/pacientes` con `{"nombre":"Ana","apellido":"Gómez","dni":"30123456","telefono":"+54 11 1234-5678","email":"ana@mail.com","fecha_nacimiento":"1990-05-01"}`
- **THEN** responde 201 con `PacienteResponse` con esos valores, `id` generado y `created_at/updated_at` presentes.

#### Scenario: Obligatorios faltantes devuelven 400 por campo

- **WHEN** se envía `POST /api/pacientes` sin `nombre` o sin `apellido` o sin `dni` (ausente, null, vacío o solo espacios)
- **THEN** responde 400 con `fieldErrors` por cada campo faltante y mensaje claro; nunca 500.

#### Scenario: Email inválido devuelve 400

- **WHEN** se envía `POST /api/pacientes` con `email` sin formato válido (ej. `"no-es-mail"`)
- **THEN** responde 400 con `fieldErrors.email` y no crea la ficha.

### Requirement: Alta de paciente — DNI duplicado con link (US-010 CA-2, RN-PA-03)

El sistema SHALL responder 409 problem-details con `fieldErrors.dni` y link a la ficha existente (`pacienteId` + URI `/api/pacientes/{id}`) cuando el `dni` ya esté registrado, incluida carrera concurrente.

#### Scenario: DNI duplicado devuelve 409 con link a ficha

- **WHEN** se envía `POST /api/pacientes` con un `dni` ya registrado (incluida mayúsculas/espacios normalizados por trim)
- **THEN** responde 409 con envelope `{"error","message":"DNI ya registrado","fieldErrors":{"dni":"..."},"pacienteId":<id>,"pacienteUri":"/api/pacientes/<id>"}` y no crea fila nueva.

### Requirement: Búsqueda paginada por DNI y apellido con índice (US-011 CA-1)

El sistema SHALL exponer `GET /api/pacientes?dni=&apellido=&page=&size=&sort=` que retorna `Page<PacienteResponse>` (default `size=20`, `sort=apellido,asc` + `nombre,asc`); `dni` filtra por coincidencia exacta (trim, case preservado pero comparación exacta sobre columna `UNIQUE`); `apellido` filtra por prefijo case-insensitive (`LIKE 'valor%'` sobre `INDEX(apellido,nombre)`); ambos combinables (AND); sin resultados → página vacía 200 con `content:[]`; `page/size` inválidos → 400.

#### Scenario: Búsqueda por apellido prefijo

- **WHEN** `SECRETARIA` llama `GET /api/pacientes?apellido=góm` existiendo "Gómez" y "González"
- **THEN** responde 200 con ambos en `content` ordenados por `apellido, nombre` y `totalElements >= 2` (colación `utf8mb4_0900_ai_ci`).

#### Scenario: Búsqueda por DNI exacto

- **WHEN** se llama `GET /api/pacientes?dni=30123456`
- **THEN** responde 200 con la ficha de ese DNI o página vacía si no existe.

### Requirement: Consulta y edición de ficha (US-011, RN-PA-02/03)

El sistema SHALL exponer `GET /api/pacientes/{id}` (200 con `PacienteResponse` o 404 si no existe) y `PATCH /api/pacientes/{id}` (edición parcial de `nombre/apellido/telefono/email/fecha_nacimiento/usuario_id`; `dni` inmutable en v1 — intento de cambiarlo → 400; validaciones idénticas al alta para los campos enviados; éxito → 200 con `updated_at` posterior a `created_at`; `email` en blanco se normaliza a `null`; `usuario_id` sigue reglas IN-01).

#### Scenario: Edición parcial ok

- **WHEN** `SECRETARIA` envía `PATCH /api/pacientes/{id}` con `{"telefono":"+54 11 9999-0000"}`
- **THEN** responde 200 con el teléfono actualizado y el resto de campos intactos.

#### Scenario: Intento de cambiar DNI es rechazado

- **WHEN** se envía `PATCH /api/pacientes/{id}` con un `dni` distinto al registrado
- **THEN** responde 400 con `fieldErrors.dni` (`dni inmutable en v1`) y la ficha queda intacta.

### Requirement: Historial de turnos del paciente (RN-PA-04)

El sistema SHALL exponer `GET /api/pacientes/{id}/turnos?page=&size=` que retorna página de turnos del paciente ordenados por `fecha DESC, hora_inicio DESC` (200; 404 si el paciente no existe); en este change el historial responde con página vacía (`content:[]`) hasta que C-08 cree la entidad `Turno` — el contrato (ruta, paginación, orden, RBAC) queda congelado desde v1; respeta el mismo RBAC que la ficha.

#### Scenario: Historial vacío pre-C-08

- **WHEN** `SECRETARIA` llama `GET /api/pacientes/{id}/turnos` con paciente existente y sin turnos
- **THEN** responde 200 con `content:[]` y metadatos de paginación.

### Requirement: RBAC staff y lectura (RN-AU-02)

El sistema SHALL hacer cumplir en backend: `SECRETARIA` y `ADMIN` → acceso completo a los 5 endpoints; `ODONTOLOGO` → solo `GET` (lista, ficha, historial), 403 ante `POST/PATCH`; no autenticado → 401 JSON / 302 según `Accept`; toda denegación → 403 problem-details con `@PreAuthorize`.

#### Scenario: Secretaria ve todas las fichas

- **WHEN** `SECRETARIA` llama `GET /api/pacientes` y `GET /api/pacientes/{id}` de cualquier paciente
- **THEN** ambas responden 200 sin restricción de ownership.

#### Scenario: Odontólogo solo lectura

- **WHEN** `ODONTOLOGO` llama `GET /api/pacientes/{id}` y luego `POST /api/pacientes`
- **THEN** el GET responde 200 y el POST 403.

### Requirement: RBAC ownership de ficha propia (RN-AU-03)

El sistema SHALL restringir al rol `PACIENTE` a `GET /api/pacientes/{id}`, `PATCH /api/pacientes/{id}` y `GET /{id}/turnos` solo cuando `paciente.usuario_id = current_user.id` (403 en caso contrario), y 403 ante `POST` y ante `GET /api/pacientes`.

#### Scenario: Paciente ve solo su ficha y es 403 en ajena

- **WHEN** `PACIENTE` con `usuario_id` vinculado a ficha A llama `GET /api/pacientes/{id-A}` y luego `GET /api/pacientes/{id-B}` (ficha de otro)
- **THEN** la primera responde 200 y la segunda 403.

### Requirement: Vinculación a Usuario — persistencia (IN-01)

El sistema SHALL persistir `paciente.usuario_id BIGINT NULLABLE UNIQUE` (FK → `usuario.id`, `ON DELETE SET NULL`, `ON UPDATE CASCADE`); `usuario_id` puede desvincularse enviando `null` explícito en `PATCH`.

#### Scenario: Link a usuario paciente existente

- **WHEN** se crea ficha con `usuario_id` de un `Usuario` rol `PACIENTE` sin ficha vinculada
- **THEN** responde 201 con `usuario_id` persistido y un segundo intento de vincular el mismo usuario a otra ficha responde 409.

### Requirement: Vinculación a Usuario — validaciones (IN-01)

El sistema SHALL validar al informar `usuario_id` en alta/edición que el usuario exista (404/400 si no) y que no esté ya vinculado a otra ficha (409); un `Usuario` con rol distinto de `PACIENTE` vinculado → 400.

#### Scenario: Link duplicado responde 409

- **WHEN** se informa un `usuario_id` ya vinculado a otra ficha
- **THEN** responde 409 y no modifica ninguna ficha.

### Requirement: Sin borrado físico — RN-PA-04 y RN-GL-03

El sistema SHALL NO exponer `DELETE /api/pacientes` en v1 (ruta inexistente → 404/405 por `SecurityConfig`); la protección RN-PA-04 (no eliminar paciente con turnos futuros RESERVADOS) queda garantizada por ausencia de borrado; `GET /api/pacientes/{id}/turnos` es el punto de consulta del historial para futura validación en C-08+.

#### Scenario: DELETE no existe

- **WHEN** cualquier rol autenticado llama `DELETE /api/pacientes/{id}`
- **THEN** responde 404 o 405 y la ficha permanece intacta.
