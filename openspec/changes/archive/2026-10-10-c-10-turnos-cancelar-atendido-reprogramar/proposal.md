# Proposal: C-10 Turnos Cancelar / Atendido / Reprogramar

## Why

Con reserva (C-08) y consulta (C-09) archivados, la agenda no puede operar: no hay forma de liberar un hueco (Flujo 2), cerrar la atención del día (US-034) ni mover un turno (Flujo 5). Este change completa el ciclo de vida del turno (RN-TU-06/07/08).

## What Changes

- `PATCH /api/turnos/{id}/cancelar` body `{motivo?}` → `estado=CANCELADO` (Flujo 2); solo desde `RESERVADO`, persiste `motivo_cancelacion` y `updated_at`.
- `PATCH /api/turnos/{id}/atendido` (sin body) → `estado=ATENDIDO` (US-034); solo desde `RESERVADO`.
- `POST /api/turnos/{id}/reprogramar` body `{nueva_fecha, nueva_hora_inicio}` → `cancelar(original, motivo:"reprogramado") + crear(nuevo RESERVADO)` en una TX (RN-TU-08, Flujo 5); si el nuevo hueco choca → 409 y el original queda `RESERVADO` intacto.
- Validaciones: RN-TU-06 (no-RESERVADO → 400), RN-TU-07 (quién cancela por rol+ownership), RN-TU-08 (reprogramar revalida RN-TU-01/05 + RN-TU-03/04 en destino); reutiliza envelope 400/403/409 existente.
- DTOs `TurnoCancelarRequest` / `TurnoReprogramarRequest` (`@Valid`) + `TurnoResponse` extendido con `motivoCancelacion`; sin migración nueva (columnas `estado`/`motivo_cancelacion` de V6).

## Capabilities

### New Capabilities

- (ninguna — todo es transición sobre la capacidad existente)

### Modified Capabilities

- `turnos`: transiciones de estado `RESERVADO→CANCELADO/ATENDIDO` con RBAC por acción, reprogramar como cancelar+crear transaccional (RN-TU-06/07/08, US-032/033/034, Flujos 2/5).

## Impact

- Nuevo: `TurnoService.cancelar/atendido/reprogramar` (TX serializable en reprogramar), 3 endpoints en `TurnoController`, DTOs `TurnoCancelarRequest`/`TurnoReprogramarRequest`, `TurnoResponse.from()` con `motivo`.
- Reutilizado sin cambio: `TurnoRepository.existsSolape*` (queries C-08), `GlobalExceptionHandler` (400/403/409 ya mapeados), índices V6 `(profesional_id,fecha)`, `(paciente_id,fecha)`, `(fecha,estado)`.
- Sin breaking: ningún endpoint C-08/C-09 cambia de firma; `GET /api/turnos` y agenda pasan a reflejar los nuevos estados (CANCELADO libera hueco, ya cubierto en C-09).
