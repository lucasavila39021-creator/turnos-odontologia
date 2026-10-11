# Spec Delta — turnos (consulta y agenda)

## ADDED Requirements

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
