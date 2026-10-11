# Tasks: C-10 Turnos Cancelar / Atendido / Reprogramar

## 1. DTOs y response con motivo

- [x] 1.1 Crear `TurnoCancelarRequest(motivo @Size(max=255))` y `TurnoReprogramarRequest(nuevaFecha @NotNull, nuevaHoraInicio @NotNull)` con `@Valid`, y sumar `motivoCancelacion` a `TurnoResponse.from()`, y verificar con test de validación (motivo >255 → 400 `fieldErrors.motivo`; fecha ausente en reprogramar → 400).
- [x] 1.2 Exponer `AuthzService` sin cambios (reutiliza `isPropioPaciente/isPropioProfesional`) y verificar con test existente verde (sin regresión en ownership C-08/C-09).

## 2. Servicio: cancelar y atendido con máquina cerrada

- [x] 2.1 Implementar `TurnoService.cancelar(id, motivo)` y `atendido(id)` (guarda RESERVADO → 400 `TurnoInvalidoException`, ownership 403 por rol según D-04, `saveAndFlush`, log `op=cancelar/atendido id=`) y verificar con tests servicio H2 (cancelar ok persiste motivo, cancelar CANCELADO → 400 intacto, atendido ok, atendido desde CANCELADO → 400).
- [x] 2.2 Añadir endpoints `PATCH /api/turnos/{id}/cancelar` (`@PreAuthorize` PACIENTE/SECRETARIA/ADMIN) y `PATCH /api/turnos/{id}/atendido` (SECRETARIA/ODONTOLOGO/ADMIN) con `@Valid`, y verificar con MockMvc (cancelar ok 200, ya-cancelado 400, paciente-ajeno 403, odontólogo-cancela 403, atendido ok 200, paciente-atendido 403, odontólogo-ajeno 403, inexistente 404).

## 3. Servicio: reprogramar transaccional + controller

- [x] 3.1 Implementar `TurnoService.reprogramar(id, nuevaFecha, nuevaHora)` en TX SERIALIZABLE (orden D-03: 404 → RBAC → RN-TU-03/04 destino → solapes excluyendo original → cancelar-en-TX + crear + `saveAndFlush`, `TurnoOcupadoException` con `sugeridos` ante choque) y verificar con tests servicio H2 (reprogramar ok deja original CANCELADO+motivo reprogramado y nuevo RESERVADO, destino ocupado → 409 con original RESERVADO intacto, mismo-hueco → 200).
- [x] 3.2 Añadir endpoint `POST /api/turnos/{id}/reprogramar` (`@PreAuthorize` SECRETARIA/ADMIN, 201 con turno nuevo) y verificar con MockMvc (reprogramar ok 201, hueco ocupado 409 + original intacto vía `GET`, PACIENTE 403, ODONTOLOGO 403, original CANCELADO 400, destino fuera-horario 400, destino pasado 400).

## 4. Integración y cierre

- [x] 4.1 Test integración punta a punta H2 (reserva → cancelar → re-reservar mismo hueco 201; reserva → reprogramar → original CANCELADO + nuevo en agenda; reserva → atendido → visible en `?estado=ATENDIDO`) y verificar con `mvn test -Dtest=TurnoTransicionesIT` verde.
- [x] 4.2 Validación final con `mvn test` verde + `openspec validate --change c-10-turnos-cancelar-atendido-reprogramar` sin errores.
