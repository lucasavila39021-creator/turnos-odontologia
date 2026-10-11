## ADDED Requirements

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
