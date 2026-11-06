# Spec Delta — turnos

## ADDED Requirements

### Requirement: Alta nueva exige sillón

El sistema SHALL exigir `sillon_id` en toda alta nueva de turno, tanto por `POST /api/turnos` como por el alta SSR; ausente → 400 sin crear la fila. Un `sillon_id` inexistente → 404; uno inactivo → 400. Los turnos legacy pueden conservar `sillon_id NULL` y siguen siendo legibles.

#### Scenario: Alta sin sillón devuelve 400

- **WHEN** `SECRETARIA` envía `POST /api/turnos` con paciente, profesional, especialidad, fecha y hora válidos pero sin `sillon_id`
- **THEN** responde 400 con `fieldErrors.sillon_id` y no crea fila.

#### Scenario: Alta SSR sin sillón devuelve 400

- **WHEN** `SECRETARIA` envía el formulario SSR de nuevo turno sin elegir sillón
- **THEN** re-renderiza el formulario con estado 400, error en `sillonId` y no crea fila.

#### Scenario: Sillón inexistente devuelve 404

- **WHEN** se envía `POST /api/turnos` con `sillon_id` que no existe
- **THEN** responde 404 y no crea fila.

#### Scenario: Sillón inactivo devuelve 400

- **WHEN** se envía `POST /api/turnos` con un `sillon_id` existente pero `activo=false`
- **THEN** responde 400 con `fieldErrors.sillon_id` y no crea fila.

### Requirement: No superposición por sillón devuelve 409 (RN-TU-11)

El sistema SHALL responder 409 cuando exista otro turno `RESERVADO` para el mismo sillón ese día con `hora_inicio < nuevo_fin AND hora_fin > nuevo_inicio`, aunque sea de otro profesional; nunca crea la fila. Turnos `CANCELADO`/`ATENDIDO` no bloquean y los turnos con `sillon_id NULL` no participan de RN-TU-11.

#### Scenario: Mismo sillón y horario con profesionales distintos devuelve 409

- **WHEN** existe un turno RESERVADO 10:00–10:30 del profesional P1 en el sillón S y se pide 10:00 para el profesional P2 en el mismo sillón S
- **THEN** responde 409 con `{"error":"Horario ocupado","sugeridos":[...]}` y no crea fila.

#### Scenario: Mismo horario en sillones distintos se crea

- **WHEN** existe un turno RESERVADO 10:00–10:30 en el sillón S1 y se pide 10:00 para el sillón S2
- **THEN** responde 201 y crea la fila con `sillon.id = S2`.

#### Scenario: Turno contiguo en el mismo sillón se crea

- **WHEN** existe un turno RESERVADO 10:00–10:30 en el sillón S y se pide 10:30–11:00 en el mismo sillón S
- **THEN** responde 201 porque `hora_fin == hora_inicio` no es solape.

#### Scenario: Turno CANCELADO libera el sillón

- **WHEN** el único turno del sillón S en el hueco pedido está CANCELADO
- **THEN** responde 201 y crea la nueva fila RESERVADO.

#### Scenario: Turnos legacy sin sillón no participan

- **WHEN** existe un turno RESERVADO legacy con `sillon_id NULL` en 10:00–10:30 y se pide 10:00 en un sillón concreto
- **THEN** responde 201 y la fila nueva no es bloqueada por el turno legacy.

#### Scenario: Sugeridos filtran por profesional y sillón

- **WHEN** el 409 se produce por solape de sillón y hay huecos libres para ese profesional pero con el sillón ocupado
- **THEN** `sugeridos` solo incluye horarios libres tanto para el profesional como para el sillón pedido.

### Requirement: Reprogramar conserva el sillón del turno original

El sistema SHALL conservar el `sillon_id` del turno original al reprogramar. Si el original es legacy con `sillon_id NULL`, el request SHALL informar `sillon_id`; ausente → 400. El destino SHALL validar RN-TU-11 además de RN-TU-01/05, y ante conflicto el original permanece `RESERVADO` sin crear fila.

#### Scenario: Reprogramar conserva el sillón

- **WHEN** `SECRETARIA` reprograma un turno RESERVADO con `sillon_id = S` a una fecha y hora libres
- **THEN** responde 201 con el turno nuevo `RESERVADO` y `sillon.id = S`, y el original queda CANCELADO.

#### Scenario: Reprogramar legacy exige sillón

- **WHEN** `SECRETARIA` reprograma un turno RESERVADO legacy con `sillon_id NULL` sin informar `sillon_id`
- **THEN** responde 400 con `fieldErrors.sillon_id`, no crea fila y el original sigue RESERVADO.

#### Scenario: Reprogramar a sillón ocupado devuelve 409 con original intacto

- **WHEN** se reprograma al hueco 10:15 de un sillón que ya tiene un RESERVADO 10:00–10:30
- **THEN** responde 409 con `sugeridos`, no crea fila y el original sigue RESERVADO.
