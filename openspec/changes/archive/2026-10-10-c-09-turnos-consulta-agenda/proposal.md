# Proposal

## Why

Con reserva (C-08) sin lectura, la agenda es ciega: odontólogo/secretaria no pueden ver el día ni los huecos libres, y el paciente no ve sus turnos. C-09 cierra el loop reserva → consulta (US-030, Flujo 3) antes de mutaciones de estado (C-10).

## What Changes

- `GET /api/turnos` con filtros combinables `profesional, fecha, desde, hasta, estado`, orden `hora_inicio ASC`, paginación `page/size`, `fecha` default hoy.
- `GET /api/turnos/{id}` detalle con paciente/profesional/especialidad anidados.
- `GET /api/profesionales/{id}/agenda?fecha=` con `ocupados` + `libres` (slots `HORARIO_DESDE→HASTA` step `DURACION_MIN` menos RESERVADO; CANCELADO/ATENDIDO libres).
- Scoping por rol en servicio: PACIENTE solo suyos (ignora `profesional`), ODONTOLOGO solo propia (403 otro), SECRETARIA/ADMIN todos.
- DTOs `TurnoListResponse` (Page), `AgendaResponse`/`HuecosResponse`; `fecha` inválida → 400; sin turnos → `content:[]` vacío.

## Capabilities

### New Capabilities

- (ninguna — se extiende la capacidad existente)

### Modified Capabilities

- `turnos`: requisitos de consulta de agenda por día/semana, detalle y huecos libres con scoping RBAC por rol (nuevo comportamiento de lectura; C-08 solo cubría creación).

## Impact

- Código: `TurnoController` (+GETs), `TurnoService` (+queries readOnly + cálculo huecos), `TurnoRepository` (+queries paginadas por rol), nuevos DTOs, `AuthzService` (+`isPropioProfesional` / lookup `findByUsuarioId`), `GlobalExceptionHandler` (400 fecha inválida).
- API: 3 endpoints GET nuevos, sin breaking (POST C-08 intacto).
- Sin migración nueva: reusa índices V6 (`profesional_id,fecha`, `paciente_id,fecha`, `fecha,estado`).
- Deps: C-08 archivado (Turno/TurnoRepository/TurnoService/TurnoResponse, ownership paciente).
