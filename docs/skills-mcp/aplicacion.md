# Informe de aplicación — Skill propia y MCP

**Materia:** Metodología I · **Proyecto:** turnos-odontologia · **Rama:** `tp-skills-mcp` · **Integrante:** Lucas Ávila (trabajo grupal realizado individualmente) · **Fecha:** 2026-11-06

Detalle cronológico, comandos y hallazgos H1–H24: [bitácora](bitacora.md). Ficha previa: [investigación](investigacion.md).

## 1. Resultados

| Pieza | Resultado | Evidencia |
|---|---|---|
| **Skill propia** `revisor-escenarios-turnos` | Generada con `skill-creator` (Anthropic), adaptada al dominio (5 ajustes), registrada y encontrada por el agente en una sesión nueva sin indicarle dónde estaba. | [prompt](evidencias/prompt-skill-creator.md) · versión original `7b3928b` · `git diff 7b3928b 65c2286 -- .opencode/skills/revisor-escenarios-turnos/SKILL.md` |
| **MCP** `mysql` (`@benborla29/mcp-server-mysql`) | Conectado a OpenCode 1.19.5 en solo lectura; usado con `mysql_query` en el explore, en el propose y en la auditoría de la skill. | [log de uso](evidencias/log-mcp-uso.txt) · sección MCP del [README](../../README.md) |
| **Change C-13** `turnos-por-sillon` | Preparado: proposal, design, spec delta (21 escenarios) y tasks con cobertura de tests; `openspec validate` válido. No implementado ni archivado. | [explore](evidencias/c13-explore.md) · `openspec/changes/c-13-turnos-por-sillon/` · `CHANGES.md` |

**Qué aportó cada incorporación al change:**

- **MCP:** confirmó contra la base real (no contra las migraciones) que no existe tabla `sillon` ni columna `sillon_id`, y encontró en los datos de desarrollo dos turnos `RESERVADO` de profesionales distintos a la misma hora: el choque que el C-13 viene a impedir. También mostró dos índices que MySQL crea solo para las claves foráneas y que no figuran en `V6__turno.sql`; de ahí salió que el índice nuevo empiece por `sillon_id`.
- **Skill:** auditó el spec y devolvió **AJUSTAR** con 11 puntos (veredicto 1). Tras aplicarlos, la segunda pasada dio **OK** ([informe 1](evidencias/c13-revision-skill.md) → [ajustes](evidencias/prompt-c13-ajustar.md) → [informe 2](evidencias/c13-revision-skill-2.md)). El spec pasó de 13 a 21 escenarios, todos con GIVEN explícito, e incorporó concurrencia, `ATENDIDO`, otro día y zona horaria.

## 2. Archivos modificados y motivo

| Archivo | Motivo |
|---|---|
| `.opencode/skills/revisor-escenarios-turnos/SKILL.md` (nuevo) | Skill propia, versionada para que la reciba quien clone el repo. |
| `.atl/skill-registry.md` | Sección "Project Skills" y compact rules de la skill. |
| `AGENTS.md` y `CLAUDE.md` (idénticos) | Cuándo usar la skill, qué necesita, cómo revisar su resultado; paso 3b del flujo; reglas del MCP (propósito, herramienta, límites). |
| `opencode.json` (nuevo) | Configuración reproducible del MCP, sin secretos (`{env:MYSQL_PASS_AGENTE}`) y con permisos `ask` para `bash` y `edit`. |
| `README.md` | Sección del MCP: requisitos, configuración, variables, prueba y tabla de problemas resueltos. |
| `scripts/dev-db-nativo.sql` (nuevo) | Base de desarrollo sin Docker (migraciones V2–V6 y datos ficticios). |
| `CHANGES.md` | Alta del C-13 en el roadmap y su estado "preparado". |
| `openspec/changes/c-13-turnos-por-sillon/` (nuevo) | Artefactos SDD del change. |
| `docs/skills-mcp/` | Ficha, informe, bitácora, prompts versionados, informes de la skill, log del MCP y capturas (`evidencias/capturas/`). |

No se regeneró la fundación ni se modificó `.active-orchestrator-state.json`. RN-TU-11 queda como tarea del change para la knowledge-base, porque el change no está implementado.

## 3. Revisión crítica (antes y después)

1. **El agente respondió "según la base" sin tener el MCP (H12, H14).** *Antes:* con el MCP caído, el agente describió la tabla `turno` leyendo las migraciones sin avisarlo; el log mostró además que escribió un script propio para consultar MySQL y que buscó la contraseña en el repo. *Cómo se detectó:* el panel lateral de OpenCode marcaba el MCP con error, y `opencode.log` registraba comandos `bash` en lugar de `mysql_query`. *Fuente que corrigió:* el diagnóstico de conexión (`ER_ACCESS_DENIED_ERROR`: contraseña desincronizada). *Después:* permisos `ask`, regla en `AGENTS.md` ("nunca responder según la base sin `mysql_query`") y uso real comprobado en el log (`permission=mysql_mysql_query`), con índices que solo la base conoce.
2. **La skill generada casi inventa una regla (H19).** *Antes:* el borrador de `skill-creator` daba por rechazado un turno que **termina** después del cierre. *Fuente:* RN-TU-03 solo exige que `hora_inicio` esté dentro del horario. *Después:* el caso se marca como "decisión de dominio a justificar". Otros 4 ajustes: RN-TU-10 fuera de alcance según el README, cita a RN-TU-01, reglas propuestas por el change y checklist de sillón.
3. **La propia skill se equivocó en un escenario (H24).** *Antes:* para probar la zona horaria propuso un turno a las 23:40. *Fuente:* RN-TU-03 (horario 08:00–18:00): ese escenario fallaría por otra regla. *Después:* caso a las 22:00 de la clínica (01:00 UTC) con turno del día siguiente a las 08:00.
4. **Configuración incorrecta de otra IA (H1, H2).** Gemini propuso la configuración de OpenCode V2 (`servers`, `env`, `MYSQL_PASSWORD`). Se corrigió fijando la versión instalada (1.19.5 = V1) y su documentación oficial.

## 4. Limitaciones

- Trabajo grupal realizado por un solo integrante, por decisión propia. En el historial aparecen dos nombres de autor, `Lucas Avila` y `lucasavila39021-creador`: son la misma persona (nombre configurado en el equipo y cuenta de GitHub).
- El escritorio usa Windows 10 LTSC 1809: sin Docker ni WSL 2. Se usó MySQL 26.10 nativo en lugar del 8.0 del `docker-compose.yml`; el esquema carga igual, pero no es idéntico al entorno del proyecto.
- La ruta de `command` en `opencode.json` es la de este equipo; el README explica cómo adaptarla.
- Del `skill-creator` se usó el flujo de creación; su ciclo de evaluación automática (pensado para Claude Code y Python) no se ejecutó.
- El modelo usado fue Big Pickle (gratuito); los resultados podrían variar con otro modelo.
- El C-13 no está implementado: los escenarios son verificables, pero todavía no hay tests que los ejecuten.
