# Prompt usado para aplicar el veredicto AJUSTAR al spec del C-13

> Evidencia del paso 4.4 (TP Skills + MCP). La skill `revisor-escenarios-turnos` devolvió **AJUSTAR** (`evidencias/c13-revision-skill.md`). Lucas revisó el informe con Claude y decidió qué aplicar. Se le pidió al agente (OpenCode 1.19.5, Big Pickle) que siguiera estas pautas.

---

Aplicá al spec del change `c-13-turnos-por-sillon` las correcciones del informe `docs/skills-mcp/evidencias/c13-revision-skill.md`, con estas decisiones mías:

## Aplicar tal cual

- Faltantes 1 a 7 de la sección 2 (concurrencia, reprogramar con sillón distinto, sillón inexistente/inactivo al reprogramar, `ATENDIDO` no bloquea, otro día no solapa, legacy legible con `sillon: null`, dos filas auditables con `motivo="reprogramado"`).
- Las 7 correcciones de ambigüedad de la sección 3: estado previo en **GIVEN** explícito, **profesionales distintos** cuando el escenario prueba RN-TU-11, sillón y fecha concretos, y lista esperada de `sugeridos`.
- Punto 11 del veredicto: el texto del requisito de RN-TU-11 tiene que mencionar los `sugeridos`; y el requisito de reprogramar tiene que incluir el 400 por sillón distinto y el 404/400 por sillón inexistente/inactivo.

## Aplicar con corrección (error del informe)

- **Faltante 8 (zona horaria):** el ejemplo del informe pide un turno a las **23:40**, pero eso viola **RN-TU-03** (horario de atención 08:00–18:00) y daría 400 por otro motivo. Reemplazalo por este caso: *Dado* que el servidor corre en UTC y son las 22:00 hora de la clínica (01:00 UTC del día siguiente), *cuando* se pide un turno para el día siguiente **en hora de la clínica** a las 08:00 en un sillón válido, *entonces* responde 201 y la fecha persistida es la del día siguiente según `America/Argentina/Buenos_Aires` (RN-GL-02), no un día más.

## No aplicar ahora

- Punto 10 del veredicto (agregar RN-TU-11 a la knowledge-base): queda como la tarea 4.2 de `tasks.md`, porque el change está **preparado, no implementado**.

## Restricciones

- Modificá **solo** `openspec/changes/c-13-turnos-por-sillon/specs/turnos/spec.md` y, si un escenario nuevo necesita su test, la tabla de cobertura de `tasks.md`.
- No toques `src/`, la knowledge-base ni otros archivos. Nunca `openspec init`.
- Al terminar, corré `openspec validate c-13-turnos-por-sillon`.
- Después **volvé a correr la skill `revisor-escenarios-turnos`** sobre el change y guardá el nuevo informe en `docs/skills-mcp/evidencias/c13-revision-skill-2.md`. Mostrame solo el veredicto final.
