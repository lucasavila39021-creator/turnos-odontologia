# Spec Delta — turnos

## Purpose

Reserva de turno con validación transaccional de no-superposición para profesional y paciente, base de la agenda del MVP.

## ADDED Requirements

### Requirement: Crear turno con duración fija calculada

El sistema SHALL exponer `POST /api/turnos` que crea `Turno` en estado `RESERVADO` con `hora_fin = hora_inicio + APP_TURNO_DURACION_MIN`; éxito → 201 con `TurnoResponse` anidado.

#### Scenario: Reserva ok devuelve 201

- **WHEN** `SECRETARIA` envía `POST /api/turnos` con paciente, profesional, especialidad válidos, fecha futura y `hora_inicio` dentro de horario
- **THEN** responde 201 con `estado=RESERVADO` y `hora_fin = hora_inicio + duración`.

#### Scenario: FK inexistente devuelve 404

- **WHEN** se envía `POST /api/turnos` con `paciente_id` inexistente
- **THEN** responde 404 y no crea fila.

### Requirement: Solape profesional devuelve 409 (RN-TU-01)

El sistema SHALL responder 409 cuando exista otro `RESERVADO` del mismo profesional ese día con `hora_inicio < nuevo_fin AND hora_fin > nuevo_inicio`; nunca crea la fila.

#### Scenario: Solape profesional devuelve 409

- **WHEN** existe turno RESERVADO 10:00–10:30 del profesional P y se pide 10:15 para P
- **THEN** responde 409 con `error` y no crea fila.

#### Scenario: Turno adyacente no solapa

- **WHEN** existe 10:00–10:30 y se pide 10:30–11:00 del mismo profesional
- **THEN** responde 201 (borde `hora_fin == hora_inicio` no es solape).

### Requirement: Solape paciente devuelve 409 (RN-TU-05)

El sistema SHALL responder 409 cuando el paciente tenga otro `RESERVADO` solapado ese día (aunque sea otro profesional); nunca crea la fila.

#### Scenario: Paciente con doble reserva devuelve 409

- **WHEN** el paciente tiene RESERVADO 10:00–10:30 y pide 10:15 con otro profesional
- **THEN** responde 409 y no crea fila.

### Requirement: Fuera de horario devuelve 400 (RN-TU-03)

El sistema SHALL responder 400 cuando `hora_inicio` o `hora_fin` caiga fuera de `APP_HORARIO_DESDE/HASTA`; nunca crea la fila.

#### Scenario: Hora fuera de rango devuelve 400

- **WHEN** se pide `hora_inicio=07:30` con apertura 08:00
- **THEN** responde 400 con `fieldErrors.hora_inicio` y no crea fila.

### Requirement: Fecha pasada devuelve 400 (RN-TU-04)

El sistema SHALL responder 400 cuando `fecha < hoy` o `fecha+hora_inicio < ahora` en TZ America/Argentina/Buenos_Aires; nunca crea la fila.

#### Scenario: Fecha pasada devuelve 400

- **WHEN** se pide turno con fecha de ayer
- **THEN** responde 400 con `fieldErrors.fecha` y no crea fila.

### Requirement: Especialidad ajena devuelve 400 (RN-PR-05)

El sistema SHALL responder 400 cuando la especialidad no esté vinculada al profesional en `profesional_especialidad`; nunca crea la fila.

#### Scenario: Especialidad no pertenece devuelve 400

- **WHEN** se pide turno con especialidad E no asignada al profesional P
- **THEN** responde 400 con `fieldErrors.especialidad_id` y no crea fila.

### Requirement: RBAC de creación con ownership de paciente

El sistema SHALL hacer cumplir: `PACIENTE` solo para su propia ficha (`paciente.usuario_id == current_user`, otro → 403); `SECRETARIA`/`ADMIN` para cualquier paciente; `ODONTOLOGO` → 403 ante crear; no autenticado → 401.

#### Scenario: Paciente reserva para otro devuelve 403

- **WHEN** `PACIENTE` envía `POST /api/turnos` con `paciente_id` de otro paciente
- **THEN** responde 403 y no crea fila.

#### Scenario: Odontólogo crear devuelve 403

- **WHEN** `ODONTOLOGO` envía `POST /api/turnos` válido
- **THEN** responde 403 y no crea fila.

### Requirement: Conflicto 409 incluye sugeridos y CANCELADO libera

El sistema SHALL incluir en el 409 `{"error":"Horario ocupado","sugeridos":[...]}` con hasta 3 huecos libres próximos; turnos `CANCELADO`/`ATENDIDO` nunca bloquean (solo `RESERVADO` solapa).

#### Scenario: 409 trae sugeridos

- **WHEN** se pide un hueco ocupado existiendo libres 10:30 y 11:00
- **THEN** responde 409 con `sugeridos` conteniendo esos horarios.

#### Scenario: Hueco cancelado queda libre

- **WHEN** solo existe un CANCELADO en el hueco pedido
- **THEN** responde 201.
