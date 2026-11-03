# Bitácora — TP Skills + MCP (Metodología I)

> Archivo anti-olvido. Va en `docs/skills-mcp/bitacora.md`. Se lee al empezar cada sesión
> y se actualiza al terminar. Un commit por casilla tildada (sin firma de IA) + `git push`.
> No reemplaza a AGENTS.md / CLAUDE.md: esas son reglas del agente, esto es el avance.

## Reglas fijas de este TP
- No regenerar la fundación ni tocar `.active-orchestrator-state.json` para "maquillar" etapas.
- El change nuevo queda **preparado**, NO implementado, verificado ni archivado.
- El MCP se documenta como **integración** (README / AGENTS.md), nunca en `.atl/skill-registry.md`.
- Datos ficticios siempre. Cero tokens/claves en repo, capturas o video.
- Todo dato de una herramienta (versión, ruta, comando) se contrasta con su doc oficial: URL + fecha.

## Estado actual
- Fase: 0
- Último commit:
- Próximo paso concreto:
- Bloqueos / dudas para el profe:

---

## Fase 0 — Preparar el terreno
- [x] 0.1 `git pull` y rama nueva `tp-skills-mcp`
- [x] 0.2 Agregar change nuevo a `CHANGES.md` (C-13) con dependencias y justificación desde la KB
- [x] 0.3 Crear `docs/skills-mcp/` con esta bitácora y carpeta `evidencias/`

## Fase 1 — Ficha de investigación (en clase) → `docs/skills-mcp/investigacion.md` (máx 2 páginas)
- [ ] 1.1 Necesidad detectada + change elegido (por qué ese)
- [ ] 1.2 Skill creadora: nombre, repo del autor, compatibilidad con OpenCode, cómo se instala
- [ ] 1.3 Diseño de la skill propia: propósito, cuándo se activa, entradas, procedimiento, salida, criterios de revisión, dónde se guarda
- [ ] 1.4 Comparación de 2 MCP (propósito, tools, compatibilidad, instalación, permisos, credenciales, costo) + elección
- [ ] 1.5 Pasos de configuración y cómo probar la conexión
- [ ] 1.6 Archivos de la fundación que se van a tocar
- [ ] 1.7 Fuentes con URL y fecha; lo no verificado marcado "Pendiente"

## Fase 2 — Skill propia
- [ ] 2.1 Instalar la skill creadora y comprobar que el agente la ve (captura)
- [ ] 2.2 Generar la skill propia con ella (guardar el prompt usado)
- [ ] 2.3 Revisar y adaptar lo generado al dominio (anotar qué se cambió → sirve para la revisión crítica)
- [ ] 2.4 Guardarla versionada en el repo (`.opencode/skills/<nombre>/SKILL.md` o la ruta que confirme la doc)
- [ ] 2.5 Registrar en `.atl/skill-registry.md` (nombre, propósito, ubicación, cuándo usarla)
- [ ] 2.6 Actualizar `AGENTS.md` **y** `CLAUDE.md` con el mismo texto (cuándo usarla, qué necesita, cómo revisar)
- [ ] 2.7 Sesión NUEVA del agente: comprobar que la encuentra sin ayuda (captura)

## Fase 3 — MCP
- [ ] 3.1 Instalar/conectar según doc oficial
- [ ] 3.2 Probar conexión y ejecutar 1 herramienta real (captura del pedido y el resultado)
- [ ] 3.3 `opencode.json` de ejemplo SIN secretos (variables de entorno por nombre)
- [ ] 3.4 Sección MCP en README (requisitos, pasos, variables, cómo probar, problema encontrado y solución)
- [ ] 3.5 AGENTS.md / CLAUDE.md: propósito del MCP, tools que se usan, cuándo, límites de acceso

## Fase 4 — Preparar el change C-13 (SDD)
- [ ] 4.1 Explore (usando una tool del MCP) → guardar resumen
- [ ] 4.2 Propose: proposal.md, design.md, specs/, tasks.md
- [ ] 4.3 Spec con ≥3 escenarios dado/cuando/entonces: feliz, error de negocio, borde
- [ ] 4.4 Pasar la skill propia sobre el spec → corregir con Ajustar lo que marque
- [ ] 4.5 tasks.md indica cómo se va a testear cada escenario
- [ ] 4.6 Revisar reglas contra `knowledge-base/05_reglas_de_negocio.md`
- [ ] 4.7 Actualizar C-13 en CHANGES.md como "preparado" (NO archivado)

## Fase 5 — Revisión crítica y entrega
- [ ] 5.1 1 error/omisión del agente: cómo se detectó, qué fuente lo corrigió, antes/después
- [ ] 5.2 `docs/skills-mcp/aplicacion.md` (máx 2 páginas): resultados, evidencias, archivos tocados y por qué, revisión crítica, limitaciones
- [ ] 5.3 Video ≤ 5 min: skill en uso, MCP en uso, integración a la fundación, aporte al change
- [ ] 5.4 `ENTREGA.md`: integrantes, link repo, hash del commit de entrega, link al video
- [ ] 5.5 Zip `TP-Skills-MCP-Apellido.zip` con ficha + informe + ENTREGA.md
- [ ] 5.6 Clonar el repo en limpio y seguir el README como si fuera otra persona

---

## Registro de sesiones
| Fecha | Qué hice | Commit | Qué sigue |
|-------|----------|--------|-----------|
|       |          |        |           |

## Hallazgos para la revisión crítica (anotar apenas aparecen)
-
