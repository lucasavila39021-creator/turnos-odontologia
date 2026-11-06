# Spec Delta — turnos

## ADDED Requirements

### Requirement: Alta nueva exige sillón

El sistema SHALL exigir `sillon_id` en toda alta nueva de turno, tanto por `POST /api/turnos` como por el alta SSR; ausente → 400 sin crear la fila. Un `sillon_id` inexistente → 404; uno inactivo → 400. Los turnos legacy pueden conservar `sillon_id NULL` y siguen siendo legibles. El alta SHALL interpretar y persistir `fecha` y `hora_inicio` en `America/Argentina/Buenos_Aires` (RN-GL-02).

#### Scenario: Alta sin sillón devuelve 400

- **GIVEN** que `SECRETARIA` está autenticada y existen un paciente, un profesional y una especialidad válidos,
- **WHEN** envía `POST /api/turnos` con paciente, profesional, especialidad, fecha y hora válidos pero sin `sillon_id`,
- **THEN** responde 400 con `fieldErrors.sillon_id` y no crea fila.

#### Scenario: Alta SSR sin sillón devuelve 400

- **GIVEN** que `SECRETARIA` está autenticada y el formulario tiene paciente, profesional, especialidad, fecha y hora válidos,
- **WHEN** envía el formulario SSR de nuevo turno sin elegir sillón,
- **THEN** re-renderiza el formulario con estado 400, error en `sillonId` y no crea fila.

#### Scenario: Sillón inexistente devuelve 404

- **GIVEN** que no existe un sillón con `id = 999999`,
- **WHEN** se envía `POST /api/turnos` con `sillon_id = 999999`,
- **THEN** responde 404 y no crea fila.

#### Scenario: Sillón inactivo devuelve 400

- **GIVEN** que existe un sillón `S` con `activo = false`,
- **WHEN** se envía `POST /api/turnos` con `sillon_id = S`,
- **THEN** responde 400 con `fieldErrors.sillon_id` y no crea fila.

#### Scenario: Turno legacy sin sillón sigue siendo legible

- **GIVEN** un turno `RESERVADO` legacy persistido con `sillon_id NULL`,
- **WHEN** `SECRETARIA` pide `GET /api/turnos` o `GET /api/turnos/{id}`,
- **THEN** responde 200 y el turno aparece con `sillon: null`, sin excepción.

#### Scenario: Alta cerca de medianoche no corre la fecha

- **GIVEN** que el servidor corre en UTC y son las `2026-11-06 22:00` hora de `America/Argentina/Buenos_Aires` (`2026-11-07 01:00` UTC),
- **WHEN** se pide un turno para `2026-11-07` a las `08:00` hora de la clínica en un sillón válido,
- **THEN** responde 201 y la `fecha` persistida es `2026-11-07` según `America/Argentina/Buenos_Aires` (RN-GL-02), no `2026-11-08`.

### Requirement: No superposición por sillón devuelve 409 (RN-TU-11)

El sistema SHALL responder 409 cuando exista otro turno `RESERVADO` para el mismo sillón ese día con `hora_inicio < nuevo_fin AND hora_fin > nuevo_inicio`, aunque sea de otro profesional; nunca crea la fila. Turnos `CANCELADO`/`ATENDIDO` no bloquean y los turnos con `sillon_id NULL` no participan de RN-TU-11. El 409 SHALL conservar el envelope `{"error":"Horario ocupado","sugeridos":[...]}` y los `sugeridos` SHALL incluir solo horarios libres tanto para el profesional como para el sillón pedido.

#### Scenario: Mismo sillón y horario con profesionales distintos devuelve 409

- **GIVEN** que existe un turno `RESERVADO` `2026-11-10 10:00–10:30` del profesional `P1` en el sillón `S`,
- **WHEN** se pide `2026-11-10 10:00` para el profesional `P2` en el mismo sillón `S`,
- **THEN** responde 409 con `{"error":"Horario ocupado","sugeridos":[...]}` y no crea fila.

#### Scenario: Mismo horario en sillones distintos se crea

- **GIVEN** que existe un turno `RESERVADO` `2026-11-10 10:00–10:30` del profesional `P1` en el sillón `S1`,
- **WHEN** se pide `2026-11-10 10:00` para el profesional `P2` en el sillón `S2`,
- **THEN** responde 201 y crea la fila con `sillon.id = S2`.

#### Scenario: Turno contiguo en el mismo sillón se crea

- **GIVEN** que existe un turno `RESERVADO` `2026-11-10 10:00–10:30` del profesional `P1` en el sillón `S`,
- **WHEN** se pide `2026-11-10 10:30–11:00` para el profesional `P2` en el mismo sillón `S`,
- **THEN** responde 201 porque `hora_fin == hora_inicio` no es solape.

#### Scenario: Turno CANCELADO libera el sillón

- **GIVEN** que el único turno del sillón `S` en `2026-11-10 10:00–10:30` está `CANCELADO` y pertenece al profesional `P1`,
- **WHEN** se pide `2026-11-10 10:00` para el profesional `P2` en el mismo sillón `S`,
- **THEN** responde 201 y crea la nueva fila `RESERVADO`.

#### Scenario: Turno ATENDIDO no bloquea el sillón

- **GIVEN** que el único turno del sillón `S` en `2026-11-10 10:00–10:30` está `ATENDIDO` y pertenece al profesional `P1`,
- **WHEN** se pide `2026-11-10 10:00` para el profesional `P2` en el mismo sillón `S`,
- **THEN** responde 201 y crea la nueva fila `RESERVADO`.

#### Scenario: Turnos legacy sin sillón no participan

- **GIVEN** que existe un turno `RESERVADO` legacy `2026-11-10 10:00–10:30` del profesional `P1` con `sillon_id NULL`,
- **WHEN** se pide `2026-11-10 10:00` para el profesional `P2` en un sillón concreto `S`,
- **THEN** responde 201 y la fila nueva no es bloqueada por el turno legacy.

#### Scenario: Mismo horario en otro día no solapa

- **GIVEN** que existe un turno `RESERVADO` `2026-11-10 10:00–10:30` del profesional `P1` en el sillón `S`,
- **WHEN** se pide `2026-11-11 10:00–10:30` para el profesional `P2` en el mismo sillón `S`,
- **THEN** responde 201 y crea la fila.

#### Scenario: Sugeridos filtran por profesional y sillón

- **GIVEN** que el sillón `S` tiene un `RESERVADO` `2026-11-10 10:00–10:30` del profesional `P1` y un `RESERVADO` `2026-11-10 11:00–11:30` del profesional `P3`, y el profesional `P2` está libre ambos horarios,
- **WHEN** `P2` pide `2026-11-10 10:00` en el sillón `S`,
- **THEN** responde 409 y `sugeridos` incluye `10:30` y no incluye `11:00`.

#### Scenario: Dos altas simultáneas al mismo sillón: una se crea y la otra devuelve 409

- **GIVEN** que el sillón `S` está libre y existen dos profesionales distintos `P1` y `P2`,
- **WHEN** se envían simultáneamente dos `POST /api/turnos` para el sillón `S`, ambos `2026-11-10 10:00–10:30`,
- **THEN** exactamente una petición responde 201 y crea la fila `RESERVADO`; la otra responde 409 con `{"error":"Horario ocupado","sugeridos":[...]}` y no crea fila.

### Requirement: Reprogramar conserva el sillón del turno original

El sistema SHALL conservar el `sillon_id` del turno original al reprogramar. Si el original es legacy con `sillon_id NULL`, el request SHALL informar `sillon_id`; ausente → 400. Si el original tiene sillón y el request informa uno distinto → 400 con `fieldErrors.sillon_id`. En esos rechazos el original permanece `RESERVADO` sin crear fila. La reprogramación SHALL dejar dos filas auditables: el original `CANCELADO` con `motivo="reprogramado"` y el nuevo `RESERVADO` con el sillón.

#### Scenario: Reprogramar conserva el sillón

- **GIVEN** que existe un turno `RESERVADO` con `sillon_id = S`,
- **WHEN** `SECRETARIA` lo reprograma a una fecha y hora libres,
- **THEN** responde 201 con el turno nuevo `RESERVADO` y `sillon.id = S`, y quedan dos filas: el original `CANCELADO` con `motivo_cancelacion="reprogramado"` y el nuevo `RESERVADO`.

#### Scenario: Reprogramar legacy exige sillón

- **GIVEN** que existe un turno `RESERVADO` legacy con `sillon_id NULL`,
- **WHEN** `SECRETARIA` lo reprograma sin informar `sillon_id`,
- **THEN** responde 400 con `fieldErrors.sillon_id`, no crea fila y el original sigue `RESERVADO`.

#### Scenario: Reprogramar con sillón distinto devuelve 400

- **GIVEN** que existe un turno `RESERVADO` con `sillon_id = S1`,
- **WHEN** `SECRETARIA` lo reprograma informando `sillon_id = S2`,
- **THEN** responde 400 con `fieldErrors.sillon_id`, no crea fila y el original sigue `RESERVADO` con `sillon_id = S1`.

### Requirement: Reprogramar valida el sillón y el destino

El sillón efectivo SHALL validarse: inexistente → 404; inactivo → 400; en ambos casos no se crea fila y el original permanece `RESERVADO`. El destino SHALL validar RN-TU-11 además de RN-TU-01/05; ante conflicto el original permanece `RESERVADO` sin crear fila.

#### Scenario: Reprogramar con sillón inexistente devuelve 404

- **GIVEN** que existe un turno `RESERVADO` legacy con `sillon_id NULL` y no existe un sillón con `id = 999999`,
- **WHEN** `SECRETARIA` lo reprograma informando `sillon_id = 999999`,
- **THEN** responde 404, no crea fila y el original sigue `RESERVADO`.

#### Scenario: Reprogramar con sillón inactivo devuelve 400

- **GIVEN** que existe un turno `RESERVADO` legacy con `sillon_id NULL` y un sillón `S` con `activo = false`,
- **WHEN** `SECRETARIA` lo reprograma informando `sillon_id = S`,
- **THEN** responde 400 con `fieldErrors.sillon_id`, no crea fila y el original sigue `RESERVADO`.

#### Scenario: Reprogramar a sillón ocupado devuelve 409 con original intacto

- **GIVEN** que existe un turno `RESERVADO` del profesional `P1` con `sillon_id = S` y otro turno `RESERVADO` `2026-11-10 10:00–10:30` del profesional `P2` en el mismo sillón `S`,
- **WHEN** `SECRETARIA` reprograma el turno de `P1` a `2026-11-10 10:15` en el sillón `S`,
- **THEN** responde 409 con `sugeridos`, no crea fila y el original sigue `RESERVADO`.
