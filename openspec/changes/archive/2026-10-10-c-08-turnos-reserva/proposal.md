# Proposal: C-08 Turnos Reserva

## Why

La superposición de turnos es el problema raíz del sistema (RN-TU-01). Sin reserva con validación transaccional no hay MVP: C-05 (pacientes) y C-07 (profesionales) ya están archivados y la barrera GATE 5 está superada. Este change crea el corazón de la agenda.

## What Changes

- Entidad `Turno` + `TurnoRepository` con queries de solape profesional/paciente + `V6__turno.sql` forward-only (FKs NOT NULL, índices, CHECK `hora_fin > hora_inicio`).
- `TurnoService.crear` en TX serializable: calcula `hora_fin = hora_inicio + APP_TURNO_DURACION_MIN` (RN-TU-02), valida RN-TU-03 (horario `APP_HORARIO_DESDE/HASTA`), RN-TU-04 (no pasada), RN-PR-05 (pertenece), RN-TU-01/RN-TU-05 (solape → 409 con `sugeridos`).
- Endpoint `POST /api/turnos` → 201 `TurnoResponse` (anidados paciente/profesional/especialidad) o 409 `{"error","sugeridos"}` / 400 por RN / 403 ownership / 404 FK inexistente.
- DTOs `TurnoCreateRequest` (`@Valid`) / `TurnoResponse.from()`; RBAC: PACIENTE solo propio, SECRETARIA/ADMIN todos, ODONTOLOGO 403 crear.
- Handoff C-07: `tieneTurnosFuturosReservados` pasa de defensivo (`false` + TODO) a query real contra `turno`; se cierra el TODO C-08.
- Tests: 201 ok, 2×409 solape, 400 horario/pasada/especialidad, 403 paciente-ajeno y odontólogo-crea, concurrencia 1-gana (hilos H2; nota MySQL gap-locks).

## Capabilities

### New Capabilities

- `turnos`: reserva de turno con no-superposición transaccional, cálculo de duración fija, validaciones RN-TU/RN-PR-05 y RBAC ownership (núcleo agenda; C-09/C-10 extienden consulta/transiciones sobre esta base).

### Modified Capabilities

- `profesionales`: requirement "Desactivar con guard de turnos futuros (RN-PR-04)" pasa de check defensivo + TODO(C-08) a query real contra tabla `turno` (sin él, desactivar rompería agenda con turnos futuros).

## Impact

- Nuevo: `domain/model/Turno`, `TurnoRepository` (solape + `existsFuturoReservado`), `TurnoService`, `TurnoController POST /api/turnos`, DTOs, excepciones (`TurnoOcupadoException`→409 con sugeridos, `TurnoInvalidoException`→400, `TurnoForbiddenException`→403), `V6__turno.sql`, `AuthzService.isPropioPaciente`.
- Modificado: `ProfesionalService.tieneTurnosFuturosReservados` (real), `GlobalExceptionHandler` (mapeos 409/400 nuevos), `application.properties` (`APP_TURNO_DURACION_MIN/HORARIO_*` ya existen desde C-01 — solo se consumen).
- Sin breaking: ningún endpoint existente cambia de firma; `GET /api/turnos` llega en C-09.
