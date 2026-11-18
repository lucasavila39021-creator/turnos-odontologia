# Auditoría de escenarios — C-13 `turnos-por-sillon`

**Skill aplicada:** `revisor-escenarios-turnos`
**Fecha:** 2026-11-06
**Change auditado:** `openspec/changes/c-13-turnos-por-sillon/`
**Alcance:** únicamente los escenarios del delta `specs/turnos/spec.md`. No se modificó el spec, el design, las tasks ni la knowledge-base.

**Fuentes consultadas**

- `openspec/changes/c-13-turnos-por-sillon/specs/turnos/spec.md`
- `openspec/changes/c-13-turnos-por-sillon/proposal.md`
- `openspec/changes/c-13-turnos-por-sillon/design.md`
- `openspec/changes/c-13-turnos-por-sillon/tasks.md`
- `openspec/specs/turnos/spec.md` (spec vigente, para reglas no modificadas)
- `knowledge-base/05_reglas_de_negocio.md`
- `CHANGES.md` — entrada C-13 (scope, dependencias, "Leer antes")
- Esquema real vía MCP `mysql_query` (ver anexo B)

**Veredicto:** **AJUSTAR** — hay al menos una cobertura bloqueante faltante (concurrencia por sillón, parte explícita de RN-TU-11) y varios bordes/ambigüedades que deben corregirse antes de aprobar el spec.

---

## 1. Tabla regla → escenario

| Regla | ¿La toca el change? | Escenario que la cubre | Estado |
|-------|---------------------|------------------------|--------|
| RN-TU-01 | Sí (crear/reprogramar siguen validando; RN-TU-11 va después) | "Solape profesional devuelve 409" y "Turno adyacente no solapa" (`openspec/specs/turnos/spec.md`) | Cubierta por spec vigente; el delta no la repite |
| RN-TU-02 | No (duración fija sin cambios) | — | No aplica |
| RN-TU-03 | No (horario de atención sin cambios) | — | No aplica |
| RN-TU-04 | No (fecha pasada sin cambios) | — | No aplica |
| RN-TU-05 | Sí (se revalida en reprogramar) | "Paciente con doble reserva devuelve 409" (`openspec/specs/turnos/spec.md`) | Cubierta por spec vigente |
| RN-TU-06 | No (no cambian las transiciones de estado) | — | No aplica |
| RN-TU-07 | No (cancelación sin cambios) | — | No aplica |
| RN-TU-08 | Sí (reprogramar = cancelar + crear) | "Reprogramar conserva el sillón" | Parcial: no afirma dos filas auditables ni `motivo="reprogramado"` |
| RN-TU-09 | No (profesional y especialidad obligatorios sin cambios) | — | No aplica |
| RN-TU-10 | No (fuera de alcance de v1) | — | No aplica |
| RN-TU-11 | Sí — **regla propuesta por el change** | "Mismo sillón y horario con profesionales distintos devuelve 409", "Mismo horario en sillones distintos se crea", "Turno contiguo en el mismo sillón se crea", "Turno CANCELADO libera el sillón", "Turnos legacy sin sillón no participan", "Sugeridos filtran por profesional y sillón" | **Parcial**: faltan concurrencia, `ATENDIDO` y mismo horario en otro día |
| RN-PR-05 | No (sin cambios) | — | No aplica |
| RN-GL-02 | Sí (el change crea y valida turnos) | — | **SIN CUBRIR** (borde de zona horaria) |

**Lectura de la tabla**

- RN-TU-01 y RN-TU-05 siguen cubiertas por el spec vigente, pero el delta no agrega ningún escenario de regresión. No es bloqueante porque el change declara que no cambia su comportamiento; sí conviene que la implementación mantenga los tests existentes (task 3.3).
- RN-TU-11 es una regla **propuesta por el change** y todavía **no existe** en `knowledge-base/05_reglas_de_negocio.md` (el archivo llega hasta RN-TU-10). La task 4.2 la agrega; hasta que eso ocurra, la regla no está en la fuente de verdad. Ver veredicto.
- El escenario "Sugeridos filtran por profesional y sillón" está bajo el requisito de RN-TU-11, pero el texto del requisito no menciona `sugeridos`. El escenario prueba algo que el SHALL no exige: falta incorporarlo al texto del requisito.

---

## 2. Escenarios faltantes

### 2.1 Faltantes bloqueantes

**1. Concurrencia: dos reservas simultáneas del mismo sillón**

- **Origen**: RN-TU-11 ("Validación en servicio + transacción DB; si hay carrera, gana el primero") + checklist borde 7.
- **Dado** que el sillón `S` está libre y existen dos profesionales distintos `P1` y `P2`,
- **cuando** se envían simultáneamente dos `POST /api/turnos` para el sillón `S`, ambos con fecha `2026-11-10` y horario `10:00–10:30`,
- **entonces** exactamente una petición responde `201` y crea la fila `RESERVADO`; la otra responde `409` con `{"error":"Horario ocupado","sugeridos":[...]}` y no crea fila.
- **Resultado esperado observable**: una sola fila `RESERVADO` para `(sillon_id=S, fecha=2026-11-10, 10:00–10:30)`.
- **Nota**: `tasks.md` 2.4 ya prevé un test de concurrencia, pero el spec no tiene el escenario. Sin escenario, la garantía de "gana el primero" no forma parte del contrato del change. **Bloqueante.**

**2. Reprogramar con un sillón distinto al original**

- **Origen**: `design.md` D-06 ("si el request trae otro distinto → 400 (no se ignora en silencio)") + RN-TU-08.
- **Dado** un turno `RESERVADO` con `sillon_id = S1`,
- **cuando** `SECRETARIA` envía `POST /api/turnos/{id}/reprogramar` informando `sillon_id = S2`,
- **entonces** responde `400` con `fieldErrors.sillon_id`, no crea fila y el original sigue `RESERVADO` con `sillon_id = S1`.
- **Resultado esperado observable**: `400` + `fieldErrors.sillon_id`; original intacto.
- **Nota**: el comportamiento está definido en `design.md` D-06 y en `tasks.md` 2.3, pero no aparece en el texto del requisito ni como escenario. **Bloqueante** por ser comportamiento definido por el change sin contrato en el spec.

**3. Validación del sillón efectivo al reprogramar (inexistente / inactivo)**

- **Origen**: `design.md` D-06 ("El sillón efectivo se valida (404/400)") + RN-TU-11.
- **Dado** un turno `RESERVADO` legacy con `sillon_id NULL`,
- **cuando** se reprograma informando un `sillon_id` inexistente,
- **entonces** responde `404`, no crea fila y el original sigue `RESERVADO`.
- **Dado** el mismo caso con un `sillon_id` existente pero `activo=false`,
- **cuando** se reprograma,
- **entonces** responde `400` con `fieldErrors.sillon_id`, no crea fila y el original sigue `RESERVADO`.
- **Nota**: el requisito 3 solo exige validar RN-TU-11 y RN-TU-01/05 en el destino; no menciona 404/400 del sillón. **Ajuste** (comportamiento definido en design, ausente del spec).

### 2.2 Faltantes de borde / ajuste

**4. Un turno `ATENDIDO` tampoco bloquea el sillón**

- **Origen**: RN-TU-11 ("Turnos `CANCELADO`/`ATENDIDO` no bloquean") + checklist borde 2.
- **Dado** que el único turno del sillón `S` en `2026-11-10` `10:00–10:30` está `ATENDIDO` y pertenece a otro profesional,
- **cuando** se pide `10:00` para el sillón `S` con un profesional distinto,
- **entonces** responde `201` y se crea la nueva fila `RESERVADO`.
- **Resultado esperado observable**: `201`; la fila nueva se crea.
- **Nota**: el spec solo prueba `CANCELADO` (escenario de la línea 48). `ATENDIDO` queda sin cubrir aunque la regla lo nombra explícitamente. **Ajuste.**

**5. Mismo horario en otro día no solapa**

- **Origen**: RN-TU-11 ("se solapen en tiempo **el mismo día**").
- **Dado** un turno `RESERVADO` `2026-11-10` `10:00–10:30` en el sillón `S`,
- **cuando** se pide `2026-11-11` `10:00–10:30` en el mismo sillón `S`,
- **entonces** responde `201` y crea la fila.
- **Resultado esperado observable**: `201`; dos filas en fechas distintas.
- **Nota**: ningún escenario fija que el filtro por fecha exista. **Ajuste.**

**6. Un turno legacy sin sillón sigue siendo legible**

- **Origen**: Requisito "Alta nueva exige sillón" ("Los turnos legacy pueden conservar `sillon_id NULL` y siguen siendo legibles") + checklist borde 11.
- **Dado** un turno `RESERVADO` legacy persistido con `sillon_id NULL`,
- **cuando** `SECRETARIA` pide `GET /api/turnos` o `GET /api/turnos/{id}`,
- **entonces** responde `200` y el turno aparece con `sillon: null`, sin excepción.
- **Resultado esperado observable**: `200` + `sillon: null`.
- **Nota**: `tasks.md` 2.5 lo prevé como test, pero el requisito no tiene escenario. **Ajuste.**

**7. Reprogramar deja dos operaciones auditables (RN-TU-08)**

- **Origen**: RN-TU-08 + escenario "Reprogramar conserva el sillón".
- **Dado** un turno `RESERVADO`,
- **cuando** `SECRETARIA` lo reprograma a una fecha/hora libre,
- **entonces** responde `201`, existen **dos filas**: el original `CANCELADO` con `motivo_cancelacion="reprogramado"` y el nuevo `RESERVADO` con el mismo sillón; `created_by` del original no muta.
- **Resultado esperado observable**: dos filas auditables con los estados/motivos indicados.
- **Nota**: el escenario actual solo dice "el original queda CANCELADO"; no fija el motivo ni las dos filas. **Ajuste.**

**8. Zona horaria de la clínica en el borde de medianoche**

- **Origen**: RN-GL-02 + checklist borde 6.
- **Dado** que el servidor corre en UTC y se pide un turno para `2026-11-06` a las `23:40` hora de `America/Argentina/Buenos_Aires`,
- **cuando** se crea el turno con un sillón válido,
- **entonces** la `fecha` persistida es `2026-11-06` y `hora_inicio` es `23:40`, sin corrimiento al día siguiente.
- **Resultado esperado observable**: fila con fecha/hora de la clínica, no UTC.
- **Nota**: el spec vigente cubre RN-TU-04 con TZ, pero el delta no dice nada. El checklist lo marca como borde faltante, no como error. **Ajuste.**

---

## 3. Escenarios ambiguos o mal formados

Los siguientes escenarios existen pero no permiten decidir si prueban RN-TU-11 o si pasan por otra regla. Deben corregirse antes de aprobar el spec.

1. **Falta GIVEN explícito en casi todos los escenarios.** Varios ponen el estado previo dentro del WHEN ("existe un turno RESERVADO…", "el único turno del sillón…"). Según el formato de la skill, el estado previo debe ir en un **Dado/GIVEN** explícito cuando el resultado depende de él. Afecta a los escenarios de las líneas 33, 43, 48, 53 y 77; en menor medida a los de las líneas 19, 24 y 26.
2. **"Mismo horario en sillones distintos se crea" (línea 38).** No indica el profesional. Si el segundo turno es del mismo profesional, RN-TU-01 lo rechaza y el escenario no prueba RN-TU-11. Debe decir explícitamente que son **profesionales distintos**.
3. **"Turno CANCELADO libera el sillón" (línea 48).** No indica de qué profesional es el turno cancelado. Si es el mismo profesional que pide el nuevo turno, RN-TU-01 tampoco bloquea y el escenario no aísla RN-TU-11. Debe ser un turno `CANCELADO` de **otro profesional**.
4. **"Turnos legacy sin sillón no participan" (línea 53).** Mismo problema: el turno legacy debe ser de **otro profesional** para que el único motivo posible de bloqueo sea el sillón.
5. **"Reprogramar a sillón ocupado devuelve 409 con original intacto" (línea 77).** No dice que el turno que ocupa el hueco sea de **otro profesional** ni que sea el **mismo sillón** del original. Si es del mismo profesional, el 409 lo produce RN-TU-01 y no RN-TU-11. Tampoco fija la fecha. Debe aclarar: mismo sillón `S`, fecha concreta, turno ocupante de otro profesional.
6. **"Sugeridos filtran por profesional y sillón" (línea 58).** No define un conjunto concreto de candidatos ni la lista esperada. Para ser verificable debe indicar, por ejemplo, un hueco libre para el profesional pero ocupado para el sillón (excluido) y otro libre para ambos (incluido).
7. **Faltan datos concretos** (fechas, ids de profesional/paciente/sillón, rol que dispara la acción) en casi todos los escenarios. La skill exige datos concretos cuando el caso lo requiera, sobre todo para distinguir RN-TU-01 de RN-TU-11.

---

## 4. Veredicto

**AJUSTAR.**

Hay escenarios de las tres clases (feliz: "Mismo horario en sillones distintos se crea" y "Reprogramar conserva el sillón"; error de negocio: altas sin sillón, sillón inexistente/inactivo, solape de sillón, reprogramar legacy; borde: contiguo, `CANCELADO`, legacy, sugeridos). El problema no es la ausencia de clases, sino que **RN-TU-11 no queda completamente cubierta** y hay comportamientos del change sin escenario.

**Qué agregar o corregir antes de aprobar**

1. **Bloqueante** — Agregar el escenario de **concurrencia** por sillón (faltante 1): dos altas simultáneas de profesionales distintos al mismo sillón y horario → una `201` y otra `409`.
2. **Bloqueante** — Agregar el escenario de **reprogramar con sillón distinto al original** → `400 fieldErrors.sillon_id` con original intacto (faltante 2), y reflejar esa regla en el texto del requisito 3.
3. **Ajuste** — Agregar validación de **sillón inexistente/inactivo al reprogramar** (faltante 3).
4. **Ajuste** — Agregar `ATENDIDO` no bloquea (faltante 4).
5. **Ajuste** — Agregar mismo horario en **otro día** no solapa (faltante 5).
6. **Ajuste** — Agregar **legacy legible con `sillon:null`** (faltante 6).
7. **Ajuste** — Precisar que reprogramar deja **dos filas auditables** con `motivo="reprogramado"` (faltante 7).
8. **Ajuste** — Agregar borde de **zona horaria** cerca de medianoche (faltante 8).
9. **Ajuste** — Corregir las ambigüedades 1 a 7 de la sección 3: GIVEN explícito, profesionales distintos, sillones/fechas concretas y lista esperada de `sugeridos`. Sin esto, varios escenarios podrían pasar por RN-TU-01 y no por RN-TU-11.
10. **Knowledge-base** — RN-TU-11 es una regla **propuesta por el change** y no está en `knowledge-base/05_reglas_de_negocio.md` (verificado: el archivo termina en RN-TU-10). Debe agregarse (task 4.2) antes de archivar; mientras tanto, la regla no está en la fuente de verdad citada por la skill.
11. **Texto del requisito** — Incorporar al SHALL del requisito de RN-TU-11 el comportamiento de **`sugeridos`** que hoy solo aparece en un escenario, para que el escenario tenga respaldo normativo.

**No es bloqueante**: RN-TU-01/05/RN-PR-05 no cambian de comportamiento y siguen cubiertas por `openspec/specs/turnos/spec.md`. RN-TU-02/03/04/06/07/09/10 no aplican al alcance del change.

---

## Anexo A — Inventario de escenarios del delta

| # | Escenario | Línea | Requisito | Clase |
|---|-----------|-------|-----------|-------|
| 1 | Alta sin sillón devuelve 400 | 9 | Alta nueva exige sillón | Error de negocio |
| 2 | Alta SSR sin sillón devuelve 400 | 14 | Alta nueva exige sillón | Error de negocio |
| 3 | Sillón inexistente devuelve 404 | 19 | Alta nueva exige sillón | Error de negocio |
| 4 | Sillón inactivo devuelve 400 | 24 | Alta nueva exige sillón | Error de negocio |
| 5 | Mismo sillón y horario con profesionales distintos devuelve 409 | 33 | RN-TU-11 | Error de negocio |
| 6 | Mismo horario en sillones distintos se crea | 38 | RN-TU-11 | Feliz |
| 7 | Turno contiguo en el mismo sillón se crea | 43 | RN-TU-11 | Borde |
| 8 | Turno CANCELADO libera el sillón | 48 | RN-TU-11 | Borde |
| 9 | Turnos legacy sin sillón no participan | 53 | RN-TU-11 | Borde |
| 10 | Sugeridos filtran por profesional y sillón | 58 | RN-TU-11 | Borde / error |
| 11 | Reprogramar conserva el sillón | 67 | Reprogramar conserva el sillón | Feliz |
| 12 | Reprogramar legacy exige sillón | 72 | Reprogramar conserva el sillón | Error de negocio |
| 13 | Reprogramar a sillón ocupado devuelve 409 con original intacto | 77 | Reprogramar conserva el sillón | Error de negocio / borde |

**Conteo**: 2 felices, 7 de error de negocio, 4 de borde. No hay escenario de concurrencia.

---

## Anexo B — Verificación del esquema con MCP `mysql_query`

Consultas de solo lectura ejecutadas contra `turnos_db` (MCP `mysql`, herramienta `mysql_query`):

- `SHOW TABLES` → 6 tablas: `especialidad`, `paciente`, `profesional`, `profesional_especialidad`, `turno`, `usuario`. **No existe `sillon`.**
- `SHOW CREATE TABLE turno` → **no existe la columna `sillon_id`**. Índices actuales: `idx_turno_prof_fecha(profesional_id, fecha, hora_inicio)`, `idx_turno_pac_fecha`, `idx_turno_fecha_estado`, más los automáticos `fk_turno_especialidad` y `fk_turno_created_by`. CHECK `chk_turno_hora (hora_fin > hora_inicio)`.

Esto coincide con lo afirmado en `proposal.md` y `design.md`: el delta describe un esquema **futuro** (`sillon`, `turno.sillon_id`, `idx_turno_sillon_fecha`) que todavía no existe en la base de desarrollo. La auditoría no valida la migración (es posterior), solo confirma el punto de partida.

---

*Informe generado en modo solo lectura sobre el spec. No se modificó `spec.md`, `design.md`, `tasks.md` ni la knowledge-base.*
