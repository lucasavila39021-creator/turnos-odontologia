# turnos Specification

## Purpose
Reserva de turno con validación transaccional de no-superposición para profesional y paciente, base de la agenda del MVP.

## Requirements

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

### Requirement: Listar turnos con filtros combinables y paginación

El sistema SHALL exponer `GET /api/turnos?profesional=&fecha=&desde=&hasta=&estado=&page=&size=` con filtros combinables, orden `hora_inicio ASC`, `fecha` default hoy en TZ America/Argentina/Buenos_Aires y paginación `page/size` con envelope `content/totalElements`.

#### Scenario: Lista día ordenada por hora

- **WHEN** `SECRETARIA` pide `GET /api/turnos?profesional=1&fecha=2026-10-12`
- **THEN** responde 200 con turnos de ese profesional ese día ordenados por `hora_inicio ASC`.

#### Scenario: Fecha default es hoy

- **WHEN** se pide `GET /api/turnos` sin `fecha/desde/hasta`
- **THEN** filtra por hoy en TZ America/Argentina/Buenos_Aires.

#### Scenario: Vista semana con rango 7 días

- **WHEN** se pide `GET /api/turnos?desde=2026-10-12&hasta=2026-10-18`
- **THEN** responde 200 con turnos en ese rango ambos inclusivos ordenados por fecha y hora.

#### Scenario: Filtro por estado combinable

- **WHEN** se pide `GET /api/turnos?fecha=2026-10-12&estado=RESERVADO`
- **THEN** solo devuelve turnos en ese estado; sin `estado` devuelve todos.

#### Scenario: Lista vacía con envelope

- **WHEN** no hay turnos para los filtros pedidos
- **THEN** responde 200 con `content:[]` y `totalElements:0`.

#### Scenario: Fecha inválida devuelve 400

- **WHEN** se pide `GET /api/turnos?fecha=no-fecha`
- **THEN** responde 400 con `fieldErrors.fecha` y no consulta la DB.

### Requirement: Detalle de turno por id

El sistema SHALL exponer `GET /api/turnos/{id}` que devuelve el turno con paciente/profesional/especialidad anidados; inexistente → 404; respeta el mismo scoping por rol que la lista.

#### Scenario: Detalle ok con anidados

- **WHEN** `SECRETARIA` pide `GET /api/turnos/5` existente
- **THEN** responde 200 con `paciente{nombre,apellido}`, `profesional{nombre,apellido}`, `especialidad{nombre}`, `fecha`, `horaInicio/Fin`, `estado`.

#### Scenario: Detalle inexistente devuelve 404

- **WHEN** se pide `GET /api/turnos/999999`
- **THEN** responde 404 y no filtra nada.

#### Scenario: Detalle ajeno respeta scoping

- **WHEN** `PACIENTE` pide detalle de un turno de otro paciente
- **THEN** responde 404 (no revela existencia) y no devuelve datos.

### Requirement: Agenda diaria con huecos libres calculados

El sistema SHALL exponer `GET /api/profesionales/{id}/agenda?fecha=` que genera slots `APP_HORARIO_DESDE→HASTA` con step `APP_TURNO_DURACION_MIN`, resta los ocupados `RESERVADO` y retorna `{"fecha","ocupados":[...],"libres":[...]}`; `CANCELADO`/`ATENDIDO` cuentan como libres.

#### Scenario: Huecos excluyen solo RESERVADO

- **WHEN** hay RESERVADO 10:00–10:30 y CANCELADO 11:00–11:30 con apertura 08:00–18:00 step 30
- **THEN** `libres` no contiene `10:00` pero sí `11:00`, y `ocupados` solo lista el RESERVADO.

#### Scenario: Profesional inexistente devuelve 404

- **WHEN** se pide `GET /api/profesionales/999999/agenda?fecha=2026-10-12`
- **THEN** responde 404.

#### Scenario: Fecha inválida en agenda devuelve 400

- **WHEN** se pide agenda con `fecha=no-fecha`
- **THEN** responde 400 con `fieldErrors.fecha`.

### Requirement: PACIENTE solo ve sus turnos e ignora profesional

El sistema SHALL forzar `WHERE paciente_id = ficha del usuario` en lista, detalle y agenda para rol `PACIENTE` (RN-AU-03); el query param `profesional` se ignora; sin ficha vinculada → lista vacía 200.

#### Scenario: Paciente ve solo suyos

- **WHEN** `PACIENTE` con ficha P pide `GET /api/turnos?profesional=<otro>`
- **THEN** responde 200 solo con turnos de P aunque existan de otros pacientes.

#### Scenario: Paciente sin ficha ve lista vacía

- **WHEN** `PACIENTE` sin `paciente.usuario_id` vinculado pide `GET /api/turnos`
- **THEN** responde 200 con `content:[]`.

### Requirement: ODONTOLOGO solo ve su agenda y 403 ante otra

El sistema SHALL forzar `WHERE profesional_id = ficha del usuario` para rol `ODONTOLOGO` (RN-AU-04); si pide `profesional` distinto a su ficha o agenda de otro → 403; agenda sin `profesional` usa su ficha.

#### Scenario: Odontólogo ve solo su agenda

- **WHEN** `ODONTOLOGO` con ficha F pide `GET /api/turnos` sin filtros
- **THEN** responde 200 solo con turnos de F.

#### Scenario: Odontólogo pide agenda ajena devuelve 403

- **WHEN** `ODONTOLOGO` con ficha F pide `GET /api/turnos?profesional=<otro>` o agenda de otro
- **THEN** responde 403 y no devuelve filas.

### Requirement: SECRETARIA y ADMIN ven todo y requieren auth

El sistema SHALL devolver turnos sin scoping para `SECRETARIA`/`ADMIN` (respetando filtros pedidos) y exigir autenticación en los tres endpoints (RN-AU-01/02); sin sesión → 401.

#### Scenario: Secretaria ve todos

- **WHEN** `SECRETARIA` pide `GET /api/turnos?fecha=2026-10-12` con turnos de varios profesionales
- **THEN** responde 200 con todos los turnos de ese día.

#### Scenario: Sin sesión devuelve 401

- **WHEN** se pide cualquier endpoint de consulta sin autenticación
- **THEN** responde 401.

### Requirement: Cancelar turno RESERVADO con motivo opcional

El sistema SHALL exponer `PATCH /api/turnos/{id}/cancelar` con body `{motivo? ≤255}` que pasa el turno de `RESERVADO` a `CANCELADO`, persiste `motivo_cancelacion` y actualiza `updated_at`; inexistente → 404.

#### Scenario: Cancelar RESERVADO ok

- **WHEN** `SECRETARIA` envía `PATCH /api/turnos/5/cancelar` con `{motivo:"paciente avisa"}` sobre turno RESERVADO
- **THEN** responde 200 con `estado=CANCELADO`, `motivoCancelacion` persistido y el hueco queda libre para re-reserva.

#### Scenario: Cancelar ya CANCELADO devuelve 400

- **WHEN** se envía `PATCH /api/turnos/5/cancelar` sobre turno `CANCELADO` o `ATENDIDO`
- **THEN** responde 400 con `detail` "No se puede cancelar" y el estado no cambia.

### Requirement: RBAC de cancelar por rol y ownership

El sistema SHALL hacer cumplir en cancelar: `PACIENTE` solo sus turnos (`paciente.usuario_id == current_user`, ajeno → 403); `SECRETARIA`/`ADMIN` cualquier turno; `ODONTOLOGO` → 403 siempre; sin sesión → 401.

#### Scenario: Paciente cancela ajeno devuelve 403

- **WHEN** `PACIENTE` envía `PATCH /api/turnos/9/cancelar` sobre turno de otro paciente
- **THEN** responde 403 y el turno sigue RESERVADO.

#### Scenario: Odontólogo cancela devuelve 403

- **WHEN** `ODONTOLOGO` envía `PATCH /api/turnos/5/cancelar` sobre turno RESERVADO
- **THEN** responde 403 y el turno sigue RESERVADO.

### Requirement: Marcar turno como ATENDIDO solo desde RESERVADO

El sistema SHALL exponer `PATCH /api/turnos/{id}/atendido` (sin body) que pasa el turno de `RESERVADO` a `ATENDIDO`; desde `CANCELADO`/`ATENDIDO` → 400; inexistente → 404.

#### Scenario: Marcar ATENDIDO ok

- **WHEN** `SECRETARIA` envía `PATCH /api/turnos/5/atendido` sobre turno RESERVADO
- **THEN** responde 200 con `estado=ATENDIDO` visible en historial y detalle.

#### Scenario: Marcar ATENDIDO desde CANCELADO devuelve 400

- **WHEN** se envía `PATCH /api/turnos/5/atendido` sobre turno CANCELADO
- **THEN** responde 400 y el estado no cambia.

### Requirement: RBAC de atendido por rol y agenda propia

El sistema SHALL hacer cumplir en atendido: `SECRETARIA`/`ADMIN` cualquier turno; `ODONTOLOGO` solo turnos de su propia agenda (`profesional.usuario_id == current_user`, otra → 403); `PACIENTE` → 403; sin sesión → 401.

#### Scenario: Odontólogo marca su agenda ok

- **WHEN** `ODONTOLOGO` con ficha F envía `PATCH /api/turnos/5/atendido` de su profesional F
- **THEN** responde 200 con `estado=ATENDIDO`.

#### Scenario: Odontólogo marca agenda ajena devuelve 403

- **WHEN** `ODONTOLOGO` con ficha F envía `PATCH /api/turnos/9/atendido` de otro profesional
- **THEN** responde 403 y el turno sigue RESERVADO.

### Requirement: Reprogramar como cancelar más crear en una transacción

El sistema SHALL exponer `POST /api/turnos/{id}/reprogramar` con `{nueva_fecha, nueva_hora_inicio}` que en una sola TX marca el original `CANCELADO` (`motivo="reprogramado"`) y crea un turno nuevo `RESERVADO` con mismo paciente/profesional/especialidad; original no-RESERVADO → 400; destino inválido → 400.

#### Scenario: Reprogramar ok deja dos registros

- **WHEN** `SECRETARIA` envía `POST /api/turnos/5/reprogramar` con fecha futura libre dentro de horario
- **THEN** responde 201 con el turno nuevo RESERVADO y el original queda CANCELADO con motivo reprogramado (dos filas auditables).

#### Scenario: Reprogramar turno no RESERVADO devuelve 400

- **WHEN** se envía `POST /api/turnos/5/reprogramar` sobre turno CANCELADO
- **THEN** responde 400, no crea fila y el original no cambia.

### Requirement: Reprogramar valida solapes y conserva original ante 409

El sistema SHALL revalidar en destino RN-TU-01/05 con la misma query de solape de reserva; si el nuevo hueco choca → 409 con `sugeridos` y rollback total (original permanece `RESERVADO`); `CANCELADO`/`ATENDIDO` nunca bloquean.

#### Scenario: Reprogramar a hueco ocupado devuelve 409 con original intacto

- **WHEN** se pide reprogramar al hueco 10:15 existiendo RESERVADO 10:00–10:30 del mismo profesional
- **THEN** responde 409 con `sugeridos`, no crea fila y el original sigue RESERVADO.

### Requirement: RBAC de reprogramar solo SECRETARIA y ADMIN

El sistema SHALL hacer cumplir en reprogramar: solo `SECRETARIA`/`ADMIN`; `PACIENTE` y `ODONTOLOGO` → 403; sin sesión → 401.

#### Scenario: Paciente reprograma devuelve 403

- **WHEN** `PACIENTE` envía `POST /api/turnos/5/reprogramar` válido
- **THEN** responde 403, no crea fila y el original sigue RESERVADO.

### Requirement: Respuesta con estado y motivo más auditoría inmutable

El sistema SHALL devolver en cancelar/atendido/reprogramar `TurnoResponse` con `estado` y `motivoCancelacion`; `created_by` del original y del nuevo nunca muta; `updated_at` del original se actualiza al transicionar (RN-GL-03 soft-state, sin borrado físico).

#### Scenario: Respuesta incluye estado y motivo

- **WHEN** `SECRETARIA` cancela un turno con motivo
- **THEN** responde 200 con `estado=CANCELADO`, `motivoCancelacion` igual al enviado y `createdAt` sin cambios.
