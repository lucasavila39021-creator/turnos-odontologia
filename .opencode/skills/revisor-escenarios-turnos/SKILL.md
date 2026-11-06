---
name: revisor-escenarios-turnos
description: Audita el spec de un change de turnos, agenda, profesionales o recurso físico (sillón/box) contra las reglas de negocio de knowledge-base/05_reglas_de_negocio.md y detecta escenarios faltantes, ambiguos o mal formados. Usarla SIEMPRE después de un propose que toque turnos/agenda/profesionales/sillón-box y antes de aprobar el spec, y también cuando el usuario diga "revisá los escenarios", "¿le falta algún caso al spec?", "auditá el spec de turnos" o pida validar que un spec cubre las reglas RN-TU. No modifica archivos: solo informa.
---

# Revisor de escenarios de turnos

Auditás specs de changes que tocan turnos contra las reglas de negocio del dominio. El objetivo es detectar, antes de aprobar el spec, qué escenarios faltan, cuáles son ambiguos y cuáles no prueban nada observable. Una regla sin escenario es una regla que se va a romper en producción sin que ningún test lo note: por eso el entregable es un cruce explícito regla → escenario, no una impresión general.

## Entradas

Antes de auditar, reuní este contexto:

1. **Spec del change**: `openspec/changes/<change>/specs/**/spec.md`. Puede haber más de una capability; auditá todas las que toquen turnos, agenda, profesionales o el recurso físico (sillón/box).
2. **Reglas de negocio**: `knowledge-base/05_reglas_de_negocio.md`. Es la fuente de verdad. No inventes reglas que no estén ahí.
3. **Contexto del change**: si existe, la sección "Leer antes" del change en `CHANGES.md`. Sirve para saber qué reglas espera tocar el change y qué decisiones ya están tomadas.
4. **Opcional, solo si el spec menciona columnas, índices o constraints**: verificá el esquema real con el MCP `mysql` (`mysql_query`, por ejemplo `SHOW CREATE TABLE turno`). Nunca contra las migraciones ni contra suposiciones. Si la herramienta no está disponible, decilo explícitamente y no reemplaces la verificación por otra cosa.

Si el change no toca turnos/agenda/profesionales/sillón-box, no apliques esta skill: avisá que no corresponde y por qué.

## Procedimiento

### 1. Inventario y clasificación

Listá **todos** los escenarios del spec (todas las secciones `#### Scenario:`). Para cada uno anotá:

- título y ubicación (`archivo:línea` si es útil);
- clasificación: **feliz** (camino exitoso), **error de negocio** (rechazo por una regla) o **borde** (límite exacto, estado especial, concurrencia, zona horaria);
- requisito al que pertenece.

Si un spec no tiene ningún escenario de una clase, eso ya es un hallazgo.

### 2. Formato de cada escenario

Verificá que cada escenario tenga:

- estructura **dado / cuando / entonces** (o **GIVEN / WHEN / THEN**), con el "dado" explícito cuando el resultado dependa de un estado previo (turno existente, paciente con reserva, profesional inactivo);
- un **resultado observable y verificable**: código HTTP, estado resultante del turno (`RESERVADO`/`CANCELADO`/`ATENDIDO`), fila creada o no, campo de error concreto (`fieldErrors.<campo>`), mensaje. "Responde error" o "funciona bien" no son resultados observables;
- datos concretos cuando el caso lo requiera (horarios, fechas, ids, rol que ejecuta la acción);
- el **rol/actor** que dispara la acción si la regla depende de RBAC u ownership.

Marcá como **ambiguo** todo escenario que no permita decidir si pasó o falló sin adivinar la intención del autor, y como **mal formado** el que no tenga cuando/entonces o mezcle varias reglas en un solo caso.

### 3. Cruce regla → escenario

Determiná qué reglas toca el change. Como mínimo revisá `RN-TU-01` a `RN-TU-10`, y sumá `RN-PR-05` (especialidad del profesional) y `RN-GL-02` (zona horaria) cuando el change cree/valide turnos. Si el change toca cancelación o reprogramación, `RN-TU-06`, `RN-TU-07` y `RN-TU-08` también son obligatorias. Si toca pacientes o profesionales, considerá las reglas `RN-PA-*` y `RN-PR-*` afectadas.

Para cada regla tocada, indicá **qué escenario la prueba** (por título) o **SIN CUBRIR**. Una regla puede estar cubierta por más de un escenario; lo que no se acepta es que ningún escenario la ejercite. Ojo: mencionar la regla en el texto del requisito no es lo mismo que tener un escenario que la pruebe.

### 4. Checklist de bordes del dominio

Recorré cada ítem y verificá si el spec lo cubre. Este checklist existe porque son los casos donde la agenda se rompe en la práctica:

1. **Turnos contiguos**: uno termina 10:30 y otro empieza 10:30 → **no** se solapan. El criterio correcto es `hora_inicio < :fin AND hora_fin > :inicio`; el borde `hora_fin == hora_inicio` debe dar 201, no 409.
2. **Turno CANCELADO no bloquea**: solo `RESERVADO` cuenta para el solape (RN-TU-01). Un `ATENDIDO` tampoco debería bloquear un hueco futuro; si el spec dice lo contrario, marcalo.
3. **Límites del horario de atención** (RN-TU-03): inicio exactamente en la apertura, inicio en el último bloque que termina justo en el cierre, y un bloque que empieza dentro pero **termina después** del cierre (debe rechazarse si la regla exige que `hora_fin` esté dentro).
4. **Fecha u hora pasada** (RN-TU-04): fecha de ayer y, por separado, hoy con hora ya pasada; ambas en zona horaria de la clínica.
5. **Mismo paciente en dos turnos solapados con profesionales distintos** (RN-TU-05): el 409 debe salir aunque el profesional sea distinto.
6. **Zona horaria de la clínica** (RN-GL-02): un turno creado cerca de la medianoche o con hora del servidor en UTC no debe correrse de día/hora. Si el spec no dice nada, marcalo como borde faltante, no como error.
7. **Concurrencia**: dos reservas simultáneas del mismo hueco (RN-TU-01). El spec debe exigir validación en servicio dentro de una transacción y que la segunda falle con 409, no que ambas pasen. Un índice único por sí solo no cubre solapes parciales.
8. **Duración fija** (RN-TU-02): `hora_fin = hora_inicio + duración` configurada global; verificar que ningún escenario permita duración por turno en v1.
9. **Estados y transiciones** (RN-TU-06): no hay `CANCELADO → RESERVADO`; cancelar un turno ya `CANCELADO` o `ATENDIDO` falla (RN-TU-07).
10. **Reprogramar** (RN-TU-08): se comporta como cancelar + crear, con dos operaciones auditables, no como un "mover" atómico.

Si un ítem no aplica al alcance del change, decilo explícitamente en lugar de omitirlo.

### 5. Proponer los faltantes

Para cada escenario faltante, escribí el caso en **dado / cuando / entonces** y citá de dónde sale: la regla `RN-xx-nn` o el número del ítem del checklist. Proponé el resultado observable concreto (código HTTP, estado, fieldError), coherente con el resto del spec. Si el resultado depende de una decisión que no está en la knowledge-base, **no la inventes**: marcala como **"decisión de dominio a justificar"** y explicá qué falta definir.

## Formato del entregable

Respondé siempre con estas tres secciones, en este orden.

### 1. Tabla regla → escenario

| Regla | ¿La toca el change? | Escenario que la cubre | Estado |
|-------|---------------------|------------------------|--------|
| RN-TU-01 | Sí | "Solape profesional devuelve 409", "Turno adyacente no solapa" | Cubierta |
| RN-TU-03 | Sí | — | **SIN CUBRIR** |

Usá "—" y **SIN CUBRIR** cuando no haya escenario. Aclará "No aplica" cuando la regla quede fuera del alcance del change.

### 2. Escenarios faltantes

Lista numerada; cada ítem con:

- **Origen**: `RN-xx-nn` o "checklist borde N";
- **Caso propuesto** en dado / cuando / entonces;
- **Resultado esperado** observable;
- **Nota** si es "decisión de dominio a justificar".

Ejemplo del estilo esperado:

> **Origen**: RN-TU-01 (checklist borde 1).
> **Dado** que existe un turno RESERVADO 10:00–10:30 del profesional P,
> **cuando** se reserva 10:30–11:00 para P,
> **entonces** responde 201 y se crea la fila (borde contiguo, no hay solape).

### 3. Veredicto

- **OK** si hay al menos un escenario feliz, uno de error de negocio y uno de borde, y todas las reglas tocadas por el change están cubiertas.
- **AJUSTAR** en caso contrario, con la lista concreta de qué agregar o corregir (escenarios faltantes + escenarios ambiguos/mal formados).

Si el veredicto es AJUSTAR, no alcanza con decir "faltan bordes": enumerá cada escenario a agregar con su origen.

## Reglas de la auditoría

- **No inventar reglas**: si lo que proponés no está en `knowledge-base/05_reglas_de_negocio.md`, marcalo como "decisión de dominio a justificar". La knowledge-base manda; ante conflicto entre el spec y las reglas, señalá el conflicto, no lo resuelvas por tu cuenta.
- **Citar siempre**: cada hallazgo cita una regla `RN-xx-nn` o un ítem del checklist. Un hallazgo sin cita es una opinión.
- **No modificar nada**: la skill es de solo lectura sobre el spec y el resto del repo. No editás `spec.md`, `design.md`, `tasks.md` ni la knowledge-base. Los cambios los aplica la persona.
- **No ejecutar comandos que escriban**: solo lectura de archivos y, si hace falta, `mysql_query` de solo lectura. Nada de migraciones, tests ni scripts propios para suplantar la base.
- **Distinguir severidad**: un escenario faltante de una regla `RN-TU` es bloqueante; un borde no cubierto es un ajuste; una ambigüedad de redacción es un ajuste. Reflejalo en el veredicto.
