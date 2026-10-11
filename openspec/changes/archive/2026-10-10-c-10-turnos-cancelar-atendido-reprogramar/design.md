# Design: C-10 Turnos Cancelar / Atendido / Reprogramar

## Context

Ver `proposal.md` (Why). Estado actual: `Turno` (`estado` ENUM default RESERVADO, `motivo_cancelacion` NULLABLE, `created_by`) + `TurnoRepository.existsSolapeProfesional/Paciente` + `TurnoService.crear/listar/detalle/agenda` (C-08/C-09) + `TurnoController` + `GlobalExceptionHandler` (400/403/409 problem-details) + `AuthzService.isPropioPaciente/isPropioProfesional` + índices V6. Sin endpoints de transición; `TurnoResponse` no expone `motivo`. Sin migración nueva.

## Goals / Non-Goals

**Goals:**
- Tres transiciones con máquina de estados cerrada RESERVADO→{CANCELADO,ATENDIDO} y RBAC por acción.
- Reprogramar atómico cancelar+crear con rollback total ante 409.
- Auditoría motivo/updated/created_by sin borrado físico (RN-GL-03).

**Non-Goals:**
- Recordatorios, historial clínico, pagos (fuera v1); horarios por profesional/día (pregunta Alta fuera de v1); UI (C-11); cambios a reserva/consulta.

## Decisions

### D-01 — Máquina de estados cerrada en servicio, sin retorno

`cancelar`/`atendido` exigen `estado == RESERVADO` al inicio de la TX (`TurnoInvalidoException("estado","No se puede cancelar/marcar")` → 400); cualquier otro estado se rechaza antes de mutar. `CANCELADO` nunca vuelve a `RESERVADO` (RN-TU-06): reprogramar crea fila nueva. Alternativa check en controller descartada: el estado puede cambiar entre lectura y escritura; el servicio es dueño de la guarda dentro de la TX.

### D-02 — Cancelar como UPDATE parcial con motivo opcional

`TurnoCancelarRequest(motivo @Size(max=255))` nullable; servicio hace `setEstado(CANCELADO) + setMotivo(motivo trimmeado o null) + saveAndFlush`. `updated_at` lo mueve JPA (`BaseEntity`). `created_by` inmutable por diseño (sin setter invocado). La fila sigue visible en lista/historial con `estado=CANCELADO` y el hueco libera porque las queries de solape y agenda solo miran `RESERVADO` (sin cambio en repository).

### D-03 — Reprogramar en TX SERIALIZABLE única con validación de destino reutilizada

`reprogramar(id, nueva_fecha, nueva_hora)` en `@Transactional(isolation=SERIALIZABLE)`: 1. carga original + guarda RESERVADO (400 si no); 2. RBAC (403 antes de validar destino, espejo D-05 C-08); 3. calcula `hora_fin = nueva_hora + duracionMin` y revalida RN-TU-03 (horario) / RN-TU-04 (no pasada) con las mismas ramas de `doCrear`; 4. solape profesional/paciente en destino con `existsSolape*` (excluyendo el original: el original aún es RESERVADO dentro de la TX — se cancela primero y luego se valida, o se valida excluyendo `id`; orden elegido: cancelar-primero-en-memoria + flush, luego validar, luego crear); 5. `saveAndFlush` ambos. Ante 409 (`TurnoOcupadoException` con `sugeridos` de `calcularSugeridos`) la TX hace rollback → original intacto RESERVADO, cero filas nuevas. Alternativa dos TX separadas descartada: deja original CANCELADO huérfano si la creación falla. Reutiliza `calcularSugeridos` y el serializador por profesional (`locks`) de C-08 para la misma garantía 1-gana en destino.

### D-04 — RBAC por acción en controller + ownership en servicio

Controller: `cancelar → @PreAuthorize("hasAnyRole('PACIENTE','SECRETARIA','ADMIN')")` (ODONTOLOGO cae a 403); `atendido → hasAnyRole('SECRETARIA','ODONTOLOGO','ADMIN')` (PACIENTE cae a 403); `reprogramar → hasAnyRole('SECRETARIA','ADMIN')` (PACIENTE/ODONTOLOGO caen a 403). Servicio refina ownership: cancelar con rol PACIENTE exige `authz.isPropioPaciente(turno.pacienteId)`; atendido con rol ODONTOLOGO exige `authz.isPropioProfesional(turno.profesionalId)`; reprogramar no necesita ownership (SECRETARIA/ADMIN sin scoping). Orden: `@PreAuthorize` → 404 inexistente → ownership 403 → estado 400 → solape 409. Detalle ajeno sigue 404 (C-09) para no filtrar existencia; en transiciones el 403 es correcto porque el id ya fue revelado por agenda propia.

### D-05 — DTOs nuevos + `motivo` en response, sin DDL

`TurnoCancelarRequest(motivo)` y `TurnoReprogramarRequest(nuevaFecha @NotNull, nuevaHoraInicio @NotNull)` con `@Valid`; `TurnoResponse` suma campo `motivoCancelacion` (record + `from()`). Sin migración: columnas `estado`/`motivo_cancelacion` e índices V6 ya cubren las escrituras (`UPDATE estado` usa PK; agenda/lista usan `(fecha,estado)` y `(profesional_id,fecha)` existentes). `POST reprogramar` devuelve 201 (crea recurso) con el turno nuevo; cancelar/atendido devuelven 200 con el turno mutado.

## Risks / Trade-offs

- [Reprogramar al mismo hueco se auto-solapa] → Mitigación: validar destino excluyendo el `id` original (o cancelar-en-memoria antes de validar); test mismo-hueco → 200 con dos filas.
- [Doble click cancela dos veces] → Mitigación: segunda llamada ve CANCELADO → 400 idempotente por estado; UI deshabilita botón tras 200.
- [Carrera reprogramar vs reserva concurrente en destino] → Mitigación: misma TX serializable + lock por profesional de C-08; perdedor → 409 con original intacto.
- [Motivo >255] → Mitigación: `@Size` → 400 `fieldErrors.motivo`, nunca truncado silencioso.

## Migration Plan

1. Deploy: sin migración (V6 ya vigente); `flyway info` verde sin cambios.
2. Verificar: cancelar→re-reservar mismo hueco 201; reprogramar a ocupado 409 + original RESERVADO; atendido visible en `GET /api/turnos?estado=ATENDIDO`.
3. Rollback: revertir código; filas CANCELADO/ATENDIDO ya creadas siguen legibles por C-09 (sin fix-forward necesario).

## Open Questions

- Ninguna que cambie specs o tasks.
