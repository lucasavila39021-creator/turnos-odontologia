# Prompt usado para proponer el C-13 (`/opsx-propose`)

> Evidencia del paso 4.2 (TP Skills + MCP). Se invocó `/opsx-propose` indicando al agente (OpenCode 1.19.5, Big Pickle) que siguiera estas pautas.

---

Creá el change **`c-13-turnos-por-sillon`** con sus cuatro artefactos: `proposal.md`, `design.md`, `specs/` y `tasks.md`. Es **solo preparación**: no implementes código, no toques `src/`, no corras tests ni migraciones, y **nunca** corras `openspec init` (el repo ya está inicializado). Escribí todo en español, con el mismo estilo de los changes archivados en `openspec/changes/archive/`.

## Fuentes que mandan

1. `docs/skills-mcp/evidencias/c13-explore.md`: hallazgos de la base y **decisiones confirmadas por Lucas**. No las cambies; si algo no cierra, preguntame.
2. `CHANGES.md`, entrada del C-13 (alcance, dependencias C-08 y C-10, fuera de alcance).
3. `knowledge-base/05_reglas_de_negocio.md` y el spec vigente de la capability `turnos`.
4. Si necesitás confirmar algo del esquema, usá el MCP `mysql` (`mysql_query`), no las migraciones.

## Qué tiene que tener cada artefacto

- **proposal.md:** por qué (el choque de sillón que hoy se acepta, con el dato de la base), qué cambia, la regla nueva propuesta **RN-TU-11** con su texto, alcance y fuera de alcance.
- **design.md:** modelo (`sillon` + `turno.sillon_id` nullable), migración forward-only, índice `idx_turno_sillon_fecha`, tratamiento de los turnos sin sillón, orden de validación y envelope 409, concurrencia (cómo convive el lock por sillón con el actual por profesional, que hoy evita anidar locks) y seed.
- **specs/:** delta de la capability `turnos`, con escenarios **dado / cuando / entonces** y resultado observable. Como mínimo:
  - **feliz:** mismo horario en sillones distintos → se crea;
  - **error de negocio:** mismo sillón, horario solapado, profesionales distintos → 409;
  - **borde:** turno contiguo en el mismo sillón → se crea;
  - **borde:** un turno `CANCELADO` libera el sillón;
  - **validación:** alta nueva sin `sillon_id` → 400;
  - **legado:** turnos existentes sin sillón no bloquean ni son bloqueados por RN-TU-11.
- **tasks.md:** tareas chicas y ordenadas; **cada escenario del spec con la forma en que se va a testear** (integración con Testcontainers MySQL, `@WebMvcTest`, etc.). Incluí una tarea para agregar RN-TU-11 a la knowledge-base.

## Al terminar

- Corré `openspec validate c-13-turnos-por-sillon` (solo lectura) y mostrame el resultado.
- Mostrame la lista de archivos creados y un resumen de 10 líneas como máximo.
- **No** corras todavía la skill `revisor-escenarios-turnos`: eso va en el paso siguiente.
