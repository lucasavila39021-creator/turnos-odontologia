# Auditoría de escenarios — C-13 `turnos-por-sillon` (revisión 2)

**Skill aplicada:** `revisor-escenarios-turnos`
**Fecha:** 2026-11-06
**Change auditado:** `openspec/changes/c-13-turnos-por-sillon/`
**Motivo:** re-auditoría posterior a la aplicación del veredicto **AJUSTAR** del informe `c13-revision-skill.md`.
**Alcance:** delta `specs/turnos/spec.md` (21 escenarios) y tabla de cobertura de `tasks.md`.

**Fuentes consultadas**

- `openspec/changes/c-13-turnos-por-sillon/specs/turnos/spec.md` (versión corregida)
- `openspec/changes/c-13-turnos-por-sillon/tasks.md` (tabla de cobertura actualizada)
- `openspec/changes/c-13-turnos-por-sillon/proposal.md` y `design.md`
- `openspec/specs/turnos/spec.md` (spec vigente para reglas no modificadas)
- `knowledge-base/05_reglas_de_negocio.md`
- `docs/skills-mcp/evidencias/c13-revision-skill.md` (informe anterior)
- `openspec validate c-13-turnos-por-sillon` → **Change is valid**

**Veredicto:** **OK**

---

## 1. Tabla regla → escenario

| Regla | ¿La toca el change? | Escenario que la cubre | Estado |
|-------|---------------------|------------------------|--------|
| RN-TU-01 | Sí (revalidación en crear/reprogramar; RN-TU-11 va después) | "Solape profesional devuelve 409" y "Turno adyacente no solapa" (`openspec/specs/turnos/spec.md`) | Cubierta por spec vigente |
| RN-TU-02 | No (duración fija sin cambios) | — | No aplica |
| RN-TU-03 | No (horario de atención sin cambios) | — | No aplica |
| RN-TU-04 | No (fecha pasada sin cambios) | — | No aplica |
| RN-TU-05 | Sí (revalidación en reprogramar) | "Paciente con doble reserva devuelve 409" (`openspec/specs/turnos/spec.md`) | Cubierta por spec vigente |
| RN-TU-06 | No (no cambian las transiciones de estado) | — | No aplica |
| RN-TU-07 | No (cancelación sin cambios) | — | No aplica |
| RN-TU-08 | Sí (reprogramar = cancelar + crear) | "Reprogramar conserva el sillón" | Cubierta |
| RN-TU-09 | No (profesional y especialidad obligatorios sin cambios) | — | No aplica |
| RN-TU-10 | No (fuera de alcance de v1) | — | No aplica |
| RN-TU-11 | Sí — regla propuesta por el change | "Mismo sillón y horario con profesionales distintos devuelve 409", "Mismo horario en sillones distintos se crea", "Turno contiguo en el mismo sillón se crea", "Turno CANCELADO libera el sillón", "Turno ATENDIDO no bloquea el sillón", "Turnos legacy sin sillón no participan", "Mismo horario en otro día no solapa", "Sugeridos filtran por profesional y sillón", "Dos altas simultáneas al mismo sillón: una se crea y la otra devuelve 409" | Cubierta |
| RN-PR-05 | No (sin cambios) | — | No aplica |
| RN-GL-02 | Sí (el change crea y valida turnos) | "Alta cerca de medianoche no corre la fecha" | Cubierta |

**Notas de cobertura**

- RN-TU-01 y RN-TU-05 no cambian de comportamiento y siguen cubiertas por el spec vigente. El delta no las duplica.
- RN-TU-11 ya incluye en su texto los `sugeridos` y el filtro por profesional **y** sillón, y tiene escenarios para: solape por sillón con profesionales distintos, sillones distintos, contiguo, `CANCELADO`, `ATENDIDO`, legacy `NULL`, otro día y concurrencia.
- El requisito de reprogramar quedó dividido en dos: "Reprogramar conserva el sillón del turno original" (conservación, legacy, sillón distinto y dos filas auditables) y "Reprogramar valida el sillón y el destino" (inexistente 404, inactivo 400, RN-TU-11 en destino con original intacto).
- RN-GL-02 quedó cubierta con el caso corregido de medianoche en hora de la clínica, dentro del horario de atención (`08:00`), sin violar RN-TU-03.

---

## 2. Escenarios faltantes

**No se detectan escenarios faltantes** para las reglas que toca el change.

Se aplicaron las correcciones del informe anterior:

| Hallazgo previo | Estado en esta revisión |
|-----------------|-------------------------|
| Faltante 1 — concurrencia por sillón | Agregado "Dos altas simultáneas al mismo sillón: una se crea y la otra devuelve 409" |
| Faltante 2 — reprogramar con sillón distinto | Agregado "Reprogramar con sillón distinto devuelve 400" |
| Faltante 3 — sillón inexistente/inactivo al reprogramar | Agregados "Reprogramar con sillón inexistente devuelve 404" y "Reprogramar con sillón inactivo devuelve 400" |
| Faltante 4 — `ATENDIDO` no bloquea | Agregado "Turno ATENDIDO no bloquea el sillón" |
| Faltante 5 — otro día no solapa | Agregado "Mismo horario en otro día no solapa" |
| Faltante 6 — legacy legible con `sillon:null` | Agregado "Turno legacy sin sillón sigue siendo legible" |
| Faltante 7 — dos filas auditables | "Reprogramar conserva el sillón" ahora exige original `CANCELADO` con `motivo_cancelacion="reprogramado"` y nuevo `RESERVADO` |
| Faltante 8 — zona horaria (corregido por indicación) | Agregado "Alta cerca de medianoche no corre la fecha" con `08:00` hora local |
| Ambigüedad 1 — GIVEN explícito | Todos los escenarios usan `GIVEN` / `WHEN` / `THEN` |
| Ambigüedad 2 — profesionales distintos | Aplicado en los escenarios de RN-TU-11 y reprogramación con sillón ocupado |
| Ambigüedad 3 — `CANCELADO` de otro profesional | El turno cancelado pertenece a `P1` y la nueva reserva a `P2` |
| Ambigüedad 4 — legacy de otro profesional | El turno legacy es de `P1` y la nueva reserva de `P2` |
| Ambigüedad 5 — reprogramar con sillón ocupado | Se fijó fecha, sillón `S` y turno ocupante de `P2`, distinto del original de `P1` |
| Ambigüedad 6 — lista esperada de `sugeridos` | Se fijaron los turnos que ocupan `10:00` y `11:00`, y se espera incluir `10:30` y excluir `11:00` |
| Ambigüedad 7 — datos concretos | Se agregaron fechas, sillones y profesionales concretos |
| Punto 11 — textos de requisitos | RN-TU-11 menciona `sugeridos`; reprogramar incluye 400 por sillón distinto y 404/400 por inexistente/inactivo |
| Punto 10 — RN-TU-11 en la knowledge-base | **No aplicado**, según indicación: queda para la task 4.2 cuando se implemente el change |

---

## 3. Veredicto

**OK.**

El spec corregido tiene escenarios de las tres clases (feliz, error de negocio y borde), todos con `GIVEN` / `WHEN` / `THEN`, resultados observables y datos concretos. Todas las reglas que toca el change quedan cubiertas: RN-TU-11 con nueve escenarios (incluida concurrencia), RN-TU-08 con la reprogramación auditable, RN-GL-02 con el borde de medianoche, y RN-TU-01/05 siguen cubiertas por el spec vigente. `openspec validate c-13-turnos-por-sillon` devuelve **valid**.

Queda pendiente, por decisión expresa, volcar RN-TU-11 a `knowledge-base/05_reglas_de_negocio.md` en la task 4.2; no afecta el veredicto de esta auditoría porque el change está preparado, no implementado.

---

## Anexo A — Inventario de escenarios

| # | Escenario | Requisito | Clase |
|---|-----------|-----------|-------|
| 1 | Alta sin sillón devuelve 400 | Alta nueva exige sillón | Error de negocio |
| 2 | Alta SSR sin sillón devuelve 400 | Alta nueva exige sillón | Error de negocio |
| 3 | Sillón inexistente devuelve 404 | Alta nueva exige sillón | Error de negocio |
| 4 | Sillón inactivo devuelve 400 | Alta nueva exige sillón | Error de negocio |
| 5 | Turno legacy sin sillón sigue siendo legible | Alta nueva exige sillón | Borde |
| 6 | Alta cerca de medianoche no corre la fecha | Alta nueva exige sillón | Borde (RN-GL-02) |
| 7 | Mismo sillón y horario con profesionales distintos devuelve 409 | RN-TU-11 | Error de negocio |
| 8 | Mismo horario en sillones distintos se crea | RN-TU-11 | Feliz |
| 9 | Turno contiguo en el mismo sillón se crea | RN-TU-11 | Borde |
| 10 | Turno CANCELADO libera el sillón | RN-TU-11 | Borde |
| 11 | Turno ATENDIDO no bloquea el sillón | RN-TU-11 | Borde |
| 12 | Turnos legacy sin sillón no participan | RN-TU-11 | Borde |
| 13 | Mismo horario en otro día no solapa | RN-TU-11 | Borde |
| 14 | Sugeridos filtran por profesional y sillón | RN-TU-11 | Borde / error |
| 15 | Dos altas simultáneas al mismo sillón: una se crea y la otra devuelve 409 | RN-TU-11 | Borde (concurrencia) |
| 16 | Reprogramar conserva el sillón | Reprogramar conserva el sillón | Feliz |
| 17 | Reprogramar legacy exige sillón | Reprogramar conserva el sillón | Error de negocio |
| 18 | Reprogramar con sillón distinto devuelve 400 | Reprogramar conserva el sillón | Error de negocio |
| 19 | Reprogramar con sillón inexistente devuelve 404 | Reprogramar valida el sillón y el destino | Error de negocio |
| 20 | Reprogramar con sillón inactivo devuelve 400 | Reprogramar valida el sillón y el destino | Error de negocio |
| 21 | Reprogramar a sillón ocupado devuelve 409 con original intacto | Reprogramar valida el sillón y el destino | Error de negocio / borde |

**Conteo:** 2 felices, 10 de error de negocio, 9 de borde. Hay cobertura explícita de concurrencia (escenario 15).

---

## Anexo B — Observaciones no bloqueantes

Estas precisiones no impiden verificar las reglas y no cambian el veredicto; quedan como mejora opcional de redacción para los tests:

- En los escenarios de RN-TU-11 que esperan `201` con dos profesionales distintos, conviene indicar que los **pacientes también son distintos**, para que RN-TU-05 no interfiera y el caso pruebe exclusivamente el sillón.
- En "Reprogramar a sillón ocupado devuelve 409 con original intacto" conviene fijar la fecha/hora actual del turno original (por ejemplo `2026-11-10 09:00–09:30`) para que el estado previo sea inequívoco.

---

## Anexo C — Verificación del cambio y del esquema

- `openspec validate c-13-turnos-por-sillon` → `Change 'c-13-turnos-por-sillon' is valid` (sin warnings).
- La base de desarrollo sigue sin tabla `sillon` ni columna `turno.sillon_id`; es lo esperado porque el change está preparado y no implementado. La verificación de esquema del informe anterior se mantiene vigente.
- No se modificaron `src/`, la knowledge-base ni otros archivos. Solo se actualizaron `specs/turnos/spec.md`, la tabla de cobertura de `tasks.md` y este informe.

---

*Informe generado en modo solo lectura sobre el modelo de dominio, sin ejecutar tests ni migraciones.*
