# Prompt usado para explorar el C-13 (`/opsx-explore`)

> Evidencia del paso 4.1 (TP Skills + MCP). Se invocó `/opsx-explore` indicando al agente (OpenCode 1.19.5, Big Pickle) que siguiera estas pautas.

---

Queremos explorar el change **C-13 `turnos-por-sillon`** antes de proponerlo. Es exploración: no escribas código, no crees el change todavía y **nunca** corras `openspec init` (el repo ya está inicializado).

## 1. Contexto a leer

- `CHANGES.md`: la entrada del C-13 (scope, dependencias C-08 y C-10, fuera de alcance y "Leer antes").
- Los archivos de "Leer antes": `knowledge-base/05_reglas_de_negocio.md` (RN-TU-01, RN-TU-05, RN-TU-06), `04_modelo_de_datos.md` (Turno), `09_decisiones_y_supuestos.md` (SU-01) y `10_preguntas_abiertas.md`.
- El spec vigente de turnos (`openspec/specs/turnos/spec.md`, o `openspec show` del spec correspondiente).

## 2. Verificación contra la base real (obligatorio, con el MCP `mysql`)

Usá **solo** la herramienta `mysql_query`. Si no está disponible, decilo y frená esta parte: no la reemplaces leyendo migraciones.

1. `SHOW TABLES` → confirmar que no existe una tabla de sillones.
2. `SHOW CREATE TABLE turno` → columnas, índices y claves foráneas actuales.
3. `SELECT id, profesional_id, fecha, hora_inicio, hora_fin, estado FROM turno ORDER BY fecha, hora_inicio` → buscar turnos `RESERVADO` de profesionales distintos en el mismo horario: hoy el sistema los acepta, y con un solo sillón serían un choque.

Citá qué dato salió de `mysql_query`.

## 3. Puntos de diseño a pensar (recomendá una opción para cada uno, con el porqué)

1. Modelo: tabla `sillon` (`id`, `nombre`, `activo`) y `turno.sillon_id` nullable, con FK.
2. Índice para validar el solape por sillón (comparalo con `idx_turno_prof_fecha`).
3. Turnos existentes sin sillón: ¿cómo se tratan en la validación?
4. ¿`sillon_id` es obligatorio para los turnos **nuevos**?
5. Texto de la regla nueva **RN-TU-11 — No superposición por sillón**, en el estilo de RN-TU-01.
6. Error: 409 con el envelope existente. Si fallan a la vez RN-TU-01 (profesional) y RN-TU-11 (sillón), ¿qué error se informa?
7. Concurrencia: la validación tiene que ir en la misma transacción que RN-TU-01.
8. Seed de desarrollo: qué sillones cargar.

## 4. Lo que tenés que entregar

Un **resumen de 25 líneas como máximo** con tres partes:

- **Hallazgos de la base** (citando `mysql_query`).
- **Decisiones recomendadas** (una línea por punto de diseño).
- **Preguntas abiertas** que solo puede responder la persona.

Al final, **ofrecé guardar ese resumen** en `docs/skills-mcp/evidencias/c13-explore.md` y esperá mi confirmación antes de escribirlo. No escribas ningún otro archivo.
