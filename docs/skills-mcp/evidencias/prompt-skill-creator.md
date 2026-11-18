# Prompt usado para generar la skill propia con `skill-creator`

> Evidencia del paso 2.2 (TP Skills + MCP). Se le pidió al agente (OpenCode 1.19.5, modelo Big Pickle) que leyera este archivo y siguiera las instrucciones.

---

Usá la skill **skill-creator** para crear una skill nueva para este proyecto. Ya te dejo respondidas las preguntas de la entrevista; si algo no queda claro, preguntame antes de escribir.

## Qué tiene que hacer la skill

- **Nombre:** `revisor-escenarios-turnos`
- **Propósito:** auditar el spec de un change que toque turnos (`openspec/changes/<change>/specs/**/spec.md`) contra las reglas de negocio de `knowledge-base/05_reglas_de_negocio.md`, y detectar escenarios faltantes, ambiguos o mal formados.
- **Cuándo se activa:** después de `propose` de un change que toque turnos, agenda, profesionales o el recurso físico (sillón/box), y antes de aprobar el spec. También si el usuario pide "revisá los escenarios" o "¿le falta algún caso al spec?".
- **Entradas:** la ruta del spec del change; `knowledge-base/05_reglas_de_negocio.md`; si existe, la sección "Leer antes" del change en `CHANGES.md`.

## Procedimiento que tiene que seguir la skill

1. Listar todos los escenarios del spec y clasificarlos en **feliz**, **error de negocio** o **borde**.
2. Verificar el formato de cada escenario: *dado / cuando / entonces* (o *GIVEN / WHEN / THEN*), con un resultado observable (código HTTP, estado del turno, mensaje).
3. Identificar qué reglas `RN-TU-xx` (y `RN-PR-05`, `RN-GL-02` si aplican) toca el change, y cruzar cada una con al menos un escenario que la pruebe.
4. Recorrer este checklist de bordes del dominio:
   - turnos **contiguos**: uno termina 10:30 y el otro empieza 10:30 → **no** se solapan (criterio `hora_inicio < :fin AND hora_fin > :inicio`);
   - un turno **CANCELADO** no bloquea el horario (RN-TU-01 solo cuenta `RESERVADO`);
   - límites del **horario de atención** (inicio exacto y último bloque, RN-TU-03);
   - **fecha u hora pasada** (RN-TU-04);
   - el mismo **paciente** en dos turnos solapados con profesionales distintos (RN-TU-05);
   - **zona horaria** de la clínica (RN-GL-02);
   - **concurrencia**: dos reservas simultáneas del mismo hueco (RN-TU-01, transacción).
5. Proponer los escenarios que faltan, cada uno escrito en *dado / cuando / entonces* y con la regla o el ítem del checklist del que sale.

## Resultado que tiene que entregar

1. Tabla **regla → escenario que la cubre** (o "SIN CUBRIR").
2. Lista de **escenarios faltantes**, cada uno con su origen.
3. Veredicto: **OK** (hay al menos un feliz, un error de negocio y un borde, y todas las reglas tocadas están cubiertas) o **AJUSTAR** (con la lista de qué agregar).

## Criterios de calidad (anti-alucinación)

- No inventar reglas: si propone algo que no está en la knowledge-base, marcarlo como **"decisión de dominio a justificar"**.
- Cada hallazgo cita una regla `RN-xx-nn` o un ítem del checklist.
- Si el spec menciona columnas o índices, sugerir verificarlos contra la base real con el MCP `mysql` (`mysql_query`), no contra las migraciones.
- La skill **no modifica** el spec: solo informa. Los cambios los aplica la persona.

## Restricciones para vos ahora

- Escribí **un solo archivo**: `.opencode/skills/revisor-escenarios-turnos/SKILL.md` (con frontmatter `name` y `description`; la descripción tiene que decir cuándo usarla).
- **No** ejecutes scripts, evaluaciones ni comandos de consola (no uses las partes de evals de `skill-creator`).
- **No** modifiques ningún otro archivo del repo.
- Escribí la skill en español.
- Al terminar, mostrame el contenido completo del `SKILL.md`.
