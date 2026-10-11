# Spec Delta — profesionales

## MODIFIED Requirements

### Requirement: Desactivar con guard de turnos futuros (RN-PR-04)

El sistema SHALL exponer `PATCH /api/profesionales/{id}/desactivar` que pone `activo=false` salvo con turnos futuros `RESERVADO` (→ 400, ficha intacta); reactivar vía `PATCH /{id}` con `{"activo":true}` siempre ok; el check consulta la tabla `turno` (`fecha >= hoy AND estado='RESERVADO'`), cerrando el TODO(C-08).

#### Scenario: Desactivar sin turnos ok

- **WHEN** `ADMIN` llama `PATCH /api/profesionales/{id}/desactivar` sin turnos futuros
- **THEN** responde 200 con `activo=false`.

#### Scenario: Desactivar con turno futuro devuelve 400

- **WHEN** `ADMIN` llama `PATCH /api/profesionales/{id}/desactivar` existiendo un turno futuro RESERVADO del profesional
- **THEN** responde 400 y la ficha sigue `activo=true`.
