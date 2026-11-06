# Bitácora — TP Skills + MCP (Metodología I)

> Archivo anti-olvido. Va en `docs/skills-mcp/bitacora.md`. Se lee al empezar cada sesión
> y se actualiza al terminar. Un commit por paso que funciona (sin firma de IA) + `git push`.
> No reemplaza a AGENTS.md / CLAUDE.md: esas son reglas del agente, esto es el avance.

## Cómo actualizar este archivo (procedimiento fijo, desde el 5/11)
Claude manda la bitácora nueva → se descarga → en la terminal **PowerShell** de VS Code, parado en el repo:
```powershell
Get-ChildItem "$HOME\Downloads\bitacora*.md" | Sort-Object LastWriteTime | Select-Object -Last 1 | Copy-Item -Destination "docs\skills-mcp\bitacora.md" -Force
git status
```
`git status` tiene que decir `modified: docs/skills-mcp/bitacora.md`. Si dice `deleted` o aparece una `bitacora (N).md`, frenar antes del commit.

## Reglas fijas de este TP
- No regenerar la fundación ni tocar `.active-orchestrator-state.json` para "maquillar" etapas.
- El change nuevo queda **preparado**, NO implementado, verificado ni archivado.
- El MCP se documenta como **integración** (README / AGENTS.md), nunca en `.atl/skill-registry.md`.
- Datos ficticios siempre. Cero tokens/claves en repo, capturas o video.
- Todo dato de una herramienta (versión, ruta, comando) se contrasta con su doc oficial: URL + fecha.

## Estado actual
- Actualizado: 2026-11-05 14:50
- Fase: 3 — MCP usado de verdad por el agente (3.2); sigue documentar MCP (3.4–3.5) y la ficha
- Avance: 35 de 45 casillas. Hecho: entorno, base, MCP, ficha y skill propia. Falta: cerrar el C-13 (skill sobre el spec) y entrega
- Último commit: `ca2f1dc` feat: propose del change C-13 turnos-por-sillon (6/11)
- Próximo paso concreto: 4.4 — correr la skill `revisor-escenarios-turnos` sobre el spec del C-13 y guardar el informe
- **Entregas:** repositorio hasta el **domingo 8/11/2026**; video hasta el **20/11/2026** (dato de Lucas, 5/11)
- Bloqueos / dudas para el profe: `ENTREGA.md` pide el link del video, pero el video vence después. ¿Se entrega el domingo sin link y se agrega el 20, o el link va aparte? (preguntar)

## Entornos
| Máquina | Sistema | OpenCode | Node | Base de datos | Proveedor de modelo |
|---|---|---|---|---|---|
| Notebook Lenovo | Linux Mint | 1.19.5 (V1) | instalado | Docker (MySQL 8.0) | OpenCode Zen |
| Escritorio | Windows 10 Enterprise LTSC 1809 | 1.19.5 (V1) — NO actualizar a 2.0 | v26.13.0 / npm 11.20.0 | MySQL Server 26.10.0 nativo + Workbench 8.0 | OpenCode, modelo gratuito Big Pickle (sin API key) |

## Plan hasta el domingo 8/11 (acelerado)
Regla para ir rápido: Claude llega con los archivos **listos para copiar**, y cada bloque se cierra con **un solo commit** (no uno por casilla).

| Sesión | Bloque | Casillas | Lo prepara Claude antes | Lo hace Lucas |
|---|---|---|---|---|
| A | Cerrar MCP + ficha | 3.5 · 1.8 · 1.9 | Bloques para `AGENTS.md` y `CLAUDE.md`; ficha actualizada | Copiar, revisar, 1 commit |
| A | Skill propia | 2.1–2.7 | Comando de instalación de `skill-creator` + plan B; prompt para generar el revisor; texto para registry/AGENTS/CLAUDE | Instalar, generar con el agente, probar en sesión nueva, capturas |
| B | Change C-13 | E.10 · 4.1–4.7 | Comando de `openspec` (**sin** `openspec init`: el repo ya está inicializado); prompts para `/opsx-explore` y `/opsx-propose` | Ejecutar con el agente, pasar la skill al spec, capturas |
| C (domingo) | Entrega del repo | 5.0 · 5.1 · 5.2 · 5.4 · 5.5 · 5.6 | `aplicacion.md` armado desde la bitácora (H12/H14/H17 = revisión crítica); `ENTREGA.md`; ficha final | Mover capturas, clonar en limpio y seguir el README, commit final y hash |
| Después del 8/11 | Video | 5.3 | **Cuadro de guion** (tiempo · qué decir · qué se ve · dónde señalar en pantalla · escena de OBS) | Ensayar y grabar con OBS antes del 20/11 |

Datos ya verificados para el plan:
- `openspec`: `npm install -g @fission-ai/openspec@latest` (Node 20.19+). No correr `openspec init`.
- `skill-creator`: candidato `npx skills add anthropics/skills@skill-creator` (inferido, no confirmado para OpenCode). Riesgo: `npx` falló en este escritorio (H9) → plan B: traer la carpeta `skills/skill-creator` del repo `anthropics/skills` con git y copiarla a `.opencode/skills/`.

Borrador de estructura del video (≤ 5 min, se completa con el C-13 hecho):
| Tramo | Contenido | Pantalla |
|---|---|---|
| 0:00–0:30 | Problema: los turnos no conocen el sillón (C-13) | `CHANGES.md` / knowledge-base |
| 0:30–1:45 | MCP en uso: los tres candados y la consulta real | `opencode.json`, OpenCode con `mysql` conectado, Workbench con el DELETE denegado |
| 1:45–3:00 | Skill en uso: el revisor encuentra escenarios faltantes | OpenCode + spec del C-13 |
| 3:00–4:00 | Integración a la fundación y aporte al change | `AGENTS.md`, `skill-registry`, `openspec/changes/...` |
| 4:00–5:00 | Revisión crítica: el agente que respondió sin MCP (H12/H14) y cómo se detectó | log `log-mcp-uso.txt` + bitácora |

---

## Fase 0 — Preparar el terreno
- [x] 0.1 Rama nueva `tp-skills-mcp`
- [x] 0.2 Change C-13 `turnos-por-sillon` agregado a `CHANGES.md`
- [x] 0.3 Carpeta `docs/skills-mcp/` con bitácora y `evidencias/`

## Fase E — Entorno del escritorio (Windows)
- [x] E.1 Node.js LTS (v26.13.0)
- [x] E.2 OpenCode 1.19.5 por npm (misma versión que la notebook)
- [x] E.3 Docker Desktop → **no instalable** (ver hallazgo H4); se reemplaza por MySQL nativo
- [x] E.4 MySQL Server 26.10.0 + Configurator
- [x] E.5 Base `turnos_db` cargada con `scripts/dev-db-nativo.sql` (5 especialidades, 2 profesionales, 3 pacientes, 4 turnos)
- [x] E.6 Usuario `agente_mcp` solo `SELECT` + `SHOW VIEW`; contraseña rotada; DELETE denegado comprobado
- [x] E.7 `opencode.json` creado (formato V1, contraseña por `{env:MYSQL_PASS_AGENTE}`)
- [x] E.7b Servidor MCP instalado fijo (`npm install -g @benborla29/mcp-server-mysql`) y probado a mano: arranca sin errores
- [x] E.8 `opencode mcp list` muestra `mysql` conectado (comprobado también en un CMD nuevo, sin variables manuales)
- [x] E.8b Contraseña del agente sincronizada entre MySQL y Windows (5/11) — evidencia: `CONEXION OK [ { n: 4 } ]` con `mysql2` y `MYSQL_PASS_AGENTE`
- [x] E.9 Modelo configurado en el escritorio: Big Pickle (gratuito, sin API key)
- [x] E.10 Instalar el CLI `openspec` **1.14.1** (la versión que generó las skills OPSX del repo: `generatedBy: "1.14.1"`); nunca `openspec init` — 6/11, `openspec --version` = 1.14.1
- Nota: OpenCode se usa desde la terminal de VS Code; la consola vieja de Windows no dibuja los íconos (`?`) ni pega con Ctrl+V

## Fase 1 — Ficha de investigación → `docs/skills-mcp/investigacion.md` (máx 2 páginas)
- [x] 1.1–1.7 Borrador hecho
- [x] 1.8 Actualizar el borrador: versión V1 confirmada, MySQL nativo en lugar de Docker, `{env:}` verificado — commit `06247de` (6/11)
- [x] 1.9 Subir la ficha al repo — `docs/skills-mcp/investigacion.md`, commit `06247de` (6/11)

## Fase 2 — Skill propia
- [x] 2.1 Instalar la skill creadora y comprobar que el agente la ve — 6/11, global en `~/.config/opencode/skills/skill-creator` (git sparse, sin npx); el agente la lista (captura)
- [x] 2.2 Generar la skill propia con ella (guardar el prompt usado) — prompt en `docs/skills-mcp/evidencias/prompt-skill-creator.md`; versión original del agente en commit `7b3928b` (6/11, Big Pickle, 2 min 50 s, solo creó el `SKILL.md`)
- [x] 2.3 Revisar y adaptar lo generado al dominio (anotar qué se cambió) — 5 ajustes (H19); evidencia: `git diff 7b3928b 65c2286 -- .opencode/skills/revisor-escenarios-turnos/SKILL.md` (7 líneas agregadas, 4 quitadas)
- [x] 2.4 Guardarla en `.opencode/skills/revisor-escenarios-turnos/SKILL.md` — commit `7b3928b`
- [x] 2.5 Registrar en `.atl/skill-registry.md` — sección "Project Skills" + compact rules, commit `b677119`
- [x] 2.6 Actualizar `AGENTS.md` y `CLAUDE.md` con el mismo texto — fila en la tabla, sección de la skill, paso 3b del flujo; commit `b677119` (idénticos)
- [x] 2.7 Sesión nueva del agente: comprobar que la encuentra sin ayuda — 6/11, Big Pickle, 7,1 s: sin nombrarla, el agente identificó la skill, su ubicación, cuándo usarla, qué necesita y qué entrega (captura). Matiz honesto: su respuesta repite casi textual la sección de `AGENTS.md`, o sea que la encontró por la fundación además de por la carpeta de skills, que es justamente el objetivo de registrarla ahí

## Fase 3 — MCP
- [x] 3.1 Conexión comprobada (= E.8)
- [x] 3.2 Ejecutar 1 herramienta real (`SHOW CREATE TABLE turno`) — 5/11, Big Pickle, 10,6 s; ver H17. Evidencia: captura de la respuesta + `evidencias/log-mcp-uso.txt` (línea 566 del log: `permission=mysql_mysql_query`)
- [x] 3.3 `opencode.json` de ejemplo sin secretos
- [x] 3.3b Tercer candado: permisos `bash` y `edit` en `ask` dentro de `opencode.json` (por H14) — commit `549ed08` (5/11)
- [x] 3.4 Sección MCP en README (requisitos, pasos, variables, cómo probar, problema y solución) — redactada por Claude, aprobada por Lucas, commit `9506519` (5/11)
- [x] 3.5 AGENTS.md / CLAUDE.md: propósito del MCP, tools, cuándo, límites — sección idéntica en ambos, commit `06247de` (6/11)

## Fase 4 — Preparar el change C-13 (SDD)
- [x] 4.1 Explore (usando el MCP) → guardar resumen — 6/11, commit `0625159`: prompt `evidencias/prompt-c13-explore.md`, resumen `evidencias/c13-explore.md`; 3 llamadas a `mysql_query` en el log (19:16 UTC, sesión `386cc45d`); 2 min 30 s
- [x] 4.2 Propose: proposal.md, design.md, specs/, tasks.md — 6/11, commit `ca2f1dc`, `openspec/changes/c-13-turnos-por-sillon/` (prompt `evidencias/prompt-c13-propose.md`)
- [x] 4.3 Spec con ≥3 escenarios dado/cuando/entonces: feliz, error de negocio, borde — 13 escenarios en 3 requisitos (feliz: sillones distintos; error: mismo sillón 409; bordes: contiguo, cancelado, legacy, sugeridos; validación 400/404)
- [ ] 4.4 Pasar la skill propia sobre el spec → corregir con Ajustar
- [x] 4.5 tasks.md indica cómo se testea cada escenario — tabla "Cobertura de escenarios del spec" (13 filas: clase de test y tipo)
- [ ] 4.6 Revisar reglas contra `knowledge-base/05_reglas_de_negocio.md`
- [ ] 4.7 C-13 en CHANGES.md como "preparado" (NO archivado)

## Fase 5 — Revisión crítica y entrega
- [ ] 5.0 Pasar las capturas de la carpeta del escritorio a `docs/skills-mcp/evidencias/`
- [ ] 5.1 Error/omisión del agente: cómo se detectó, qué fuente lo corrigió, antes/después
- [ ] 5.2 `docs/skills-mcp/aplicacion.md` (máx 2 páginas)
- [ ] 5.3 Video ≤ 5 min
- [ ] 5.4 `ENTREGA.md`: integrantes, link repo, hash del commit de entrega, link al video
- [ ] 5.5 Zip `TP-Skills-MCP-Avila.zip`
- [ ] 5.6 Clonar en limpio y seguir el README como otra persona

---

## Registro de sesiones
| Fecha | Qué hice | Commits | Qué sigue |
|-------|----------|---------|-----------|
| 2026-11-06 | Propose del C-13: 4 artefactos, 13 escenarios, tabla de cobertura de tests; revisado por Claude (H23) | `ca2f1dc` | Correr la skill (4.4) |
| 2026-11-06 | MySQL estaba detenido (H21) → servicio automático; explore del C-13 con el MCP; 4 decisiones confirmadas por Lucas; resumen revisado por Claude contra el código (H22) | `0625159` | Propose |
| 2026-11-06 | 2.7: sesión nueva encuentra la skill sola → fase 2 cerrada; `openspec` 1.14.1 instalado | `a466d3a` | Explore del C-13 |
| 2026-11-06 | Commit de ajustes (registro y AGENTS/CLAUDE correctos; `SKILL.md` pisado, H20) y corrección | `b677119` · `65c2286` | 2.7 |
| 2026-11-06 | `skill-creator` instalada (git, global); skill generada por el agente con el prompt versionado; Claude revisa y propone 5 ajustes; registro y AGENTS/CLAUDE preparados | `7b3928b` | Commit de ajustes; 2.7 |
| 2026-11-06 | Sesión A: sección MCP en `AGENTS.md`/`CLAUDE.md` (idénticos) y ficha actualizada. El pegado de varias líneas en PowerShell dejó copias a medias; se rehízo en una sola línea | `06247de` | Skill propia |
| 2026-11-05 (cierre) | Fechas de entrega recibidas; plan acelerado A/B/C + video | sin commit (esta versión se sube al empezar la sesión A) | Sesión A |
| 2026-11-05 | Bitácora restaurada; sección MCP del README aprobada y subida | `b9866d5` · `9506519` | 3.5 |
| 2026-11-05 | La bitácora se volvió a borrar en `1481cb0` (H16 bis); se fija un comando único para actualizarla | `1481cb0` (log) | Restaurar bitácora; README del MCP |
| 2026-11-05 | Prueba real del MCP: `SHOW CREATE TABLE turno` respondido desde la base (H17); invocación confirmada en el log; sin pedidos de permiso | `1481cb0` | README del MCP (3.4) |
| 2026-11-05 | Retomamos; paso 18: `ALTER USER` + `setx` con la misma clave → `CONEXION OK [ { n: 4 } ]`. Se detectó que la bitácora descargada quedó como `bitacora (8).md` sin reemplazar la del repo (las versiones con H9b–H15 nunca se habían subido) | `549ed08` | Prueba real del MCP (3.2) |
| 2026-11-03 (mañana) | Log de OpenCode analizado (H14); tercer candado de permisos; prueba directa de conexión → `ER_ACCESS_DENIED_ERROR` (H15) | sin confirmar | Paso 18 |
| 2026-11-03 | Big Pickle configurado; primer pedido al agente: respondió SIN usar el MCP (H12) | — | Reabrir VS Code y repetir el pedido |
| 2026-11-03 | Servidor MCP instalado fijo; `opencode.json` apuntando a `node` + ruta del servidor; MCP conectado y estable | bitácora H1-H9 · integración MCP | Configurar Zen; ejecutar la primera herramienta real |
| 2026-11-02 | Revisión del TP anterior; C-13 al roadmap; bitácora; investigación de MCP con Gemini y verificación; borrador de ficha; entorno del escritorio (Node, OpenCode, MySQL nativo, base cargada, usuario de solo lectura); `opencode.json` | C-13 en roadmap · bitácora · script de base | Resolver conexión del MCP; configurar Zen |

## Evidencias (hoy en una carpeta del escritorio, pasar a `evidencias/` en 5.0)
- Búsquedas en los README de GitHub: `mysql_query`, `MYSQL_PASS` (mcp-server-mysql), `resolve-library-id`, `query-docs` (context7)
- `candado2-delete-denegado.png`: Error 1142 al intentar DELETE con `agente_mcp`
- ⚠ NO usar la captura del `CREATE USER` (mostraba la contraseña; ya fue rotada)

## Hallazgos para la revisión crítica
- **H1 — Config de Gemini inválida para la versión instalada (2/11).** Gemini propuso `"mcp": {"servers": {...}}`, `"env"` y `MYSQL_PASSWORD`, y presentó Context7 como pago. Contra la doc oficial: OpenCode V1 no usa `servers` (eso es V2), el campo es `environment`, la variable es `MYSQL_PASS`, y Context7 es gratis con API key opcional.
- **H2 — La corrección también falló al principio (2/11).** Claude marcó `servers` y `/mcps` como errores mirando solo la doc de V1; en V2 son correctos. Se resolvió fijando la versión instalada (`opencode --version` = 1.19.5 → V1) y usando su documentación. Lección: verificar primero la versión, después la sintaxis.
- **H3 — Verificación personal en GitHub (2/11).** Confirmados a mano los 4 nombres en los README oficiales.
- **H4 — Docker no instala en el escritorio (2/11).** Docker Desktop 4.97 exige Windows 10 22H2 (19045+); el escritorio es Windows 10 Enterprise LTSC 1809 (17763), que no recibe actualizaciones de versión. WSL 2 tampoco está disponible. Solución: MySQL nativo + `scripts/dev-db-nativo.sql`, que aplica las migraciones V2–V6 del proyecto sin cambios.
- **H5 — Versión de MySQL distinta (2/11).** El proyecto usa MySQL 8.0 (Docker); en el escritorio quedó MySQL Server 26.10.0. El esquema carga sin errores. Workbench 8.0 avisa versión no probada pero funciona.
- **H6 — Credencial expuesta en captura (2/11).** La captura del `CREATE USER` mostraba la contraseña. Se rotó con `ALTER USER` y la captura se descarta.
- **H7 — Segundo candado comprobado (2/11).** `agente_mcp` puede hacer SELECT; DELETE devuelve Error 1142 (denegado).
- **H8 — npm 11 bloquea scripts de instalación (2/11).** Al instalar `opencode-ai@1.19.5`, npm no ejecutó su `postinstall` (medida anti-supply-chain). OpenCode funcionó igual; no hizo falta habilitarlo.
- **H9 — MCP no conecta (2/11, en curso).** (1) `Operation timed out after 30000ms`: la primera descarga de `npx` superó el límite → se agregó `"timeout": 120000` y se precargó con `npx`. (2) La precarga dejó avisos `EPERM` al limpiar la caché de npx y después apareció `MCP error -32000: Connection closed` (el proceso del servidor arranca y se cierra). (3) **Diagnóstico (3/11):** se corrió el servidor a mano con las mismas variables de entorno y arrancó sin errores → el problema era npx (caché a medio descargar), no la configuración ni MySQL. Solución: instalación fija con `npm install -g`. (4) Con `"command": ["mcp-server-mysql"]` OpenCode quedó colgado: no puede lanzar el atajo `.cmd` que crea npm en Windows. Se reemplazó por `["node", "<ruta>\\dist\\index.js"]`, ruta sacada del propio `.cmd`. (5) El primer intento con `node` dio `Failed to get tools` y el segundo conectó sin cambios reales en el archivo (se movió la indentación, que JSON ignora). Se repitió en un CMD nuevo, sin variables manuales, y volvió a conectar. Causa del fallo puntual: **no determinada**; lo más probable, el primer arranque en frío. Se registra así, sin inventar una causa.
- **H9b — `{env:}` funciona en OpenCode V1 (3/11).** `"MYSQL_PASS": "{env:MYSQL_PASS_AGENTE}"` toma la variable de Windows creada con `setx`. Cierra el pendiente de la ficha: la contraseña nunca está en el repo.
- **H10 — npm bloqueó otro script de instalación (3/11).** Al instalar el servidor MCP, npm no ejecutó el `postinstall` de `esbuild`. El servidor funcionó igual, así que no se habilitó.
- **H11 — Trabajo en dos máquinas (3/11).** Regla adoptada: `git pull` al empezar en cualquier máquina; commit + `git push` al terminar.
- **H12 — El agente respondió sin usar el MCP (3/11).** Se le pidió ejecutar `SHOW CREATE TABLE turno` con el MCP. En la sesión, el MCP figuraba como `mysql Operation timed out after 120000ms` (no conectado), pero el agente igual entregó columnas, índices y restricciones **leyendo los archivos de migración** del repo, sin avisar claramente que no había usado la herramienta (solo sugirió revisar la contraseña al final). El contenido coincide con `V6__turno.sql`, pero no está verificado contra la base real: es exactamente la diferencia entre "funcionalidad comprobada" y "afirmación plausible". Se detectó mirando el panel lateral (estado del MCP). Causa probable del timeout: VS Code estaba abierto desde antes del `setx`, así que su terminal no tenía `MYSQL_PASS_AGENTE`. Corrección: reabrir VS Code y repetir el pedido exigiendo que avise si el MCP no está disponible. Evidencia: captura de la respuesta con el panel lateral.
- **H13 — OpenCode ofrece actualizar a 2.0 (3/11).** No se actualiza: la configuración del MCP es de V1 y la notebook usa 1.19.5.
- **H14 — Sin MCP, el agente improvisó por su cuenta (3/11, del log `opencode.log`).** En la misma sesión de H12, el agente: (1) escribió un script propio `mcp-client.js` en `%TEMP%\opencode\` y lo ejecutó con `node ... sql 'SHOW CREATE TABLE turno'` y `'SELECT 1 AS x'`, salteando el MCP; (2) leyó el código fuente del servidor MCP instalado (`dist/index.js`, `src/config`, `src/db`); (3) buscó en el repositorio `agente_mcp|MYSQL_PASS_AGENTE|MYSQL_PASS` con `rg` y `grep`, o sea, **buscó la contraseña**. No la encontró porque nunca estuvo en el repo (el diseño con `{env:}` funcionó). Casi todo corrió **sin pedir permiso**, porque los permisos de OpenCode por defecto son `allow`; solo preguntó al salir de la carpeta del proyecto (`external_directory`). Corrección: tercer candado, `"permission"` en `opencode.json` con `bash` y `edit` en `ask`.
- **H15 — Causa raíz: contraseña desincronizada (3/11).** El log mostró `failed to get resources ... Connection closed`: el servidor MCP arrancaba y listaba sus herramientas, pero se caía al tocar la base. Se probó la conexión con la misma librería que usa el servidor (`mysql2`), el usuario `agente_mcp` y la variable `MYSQL_PASS_AGENTE`: `ER_ACCESS_DENIED_ERROR ... (using password: YES)`. La variable de Windows (`setx`) y la contraseña de MySQL (`ALTER USER`, paso 5e) no coinciden. **Corrige la hipótesis de H12** ("VS Code no tenía la variable"): la variable estaba (`variable OK`), pero con una clave vieja. Explica también por qué `opencode mcp list` decía "conectado" mientras la sesión no podía usar la base: listar herramientas no requiere entrar a MySQL. El fallo puntual de H9 (5) probablemente tenga la misma causa, pero no se comprobó. Solución: misma contraseña nueva en los dos lugares (paso 18). **Resuelto 5/11:** la prueba directa devolvió `CONEXION OK [ { n: 4 } ]`.
- **H16 — La bitácora se borró del repo sin querer (5/11, revisando el historial).** El commit `04f504c` (integración MCP) incluyó el borrado de `docs/skills-mcp/bitacora.md`, y las versiones descargadas después quedaron con otro nombre (`bitacora (8).md`). Se restauró completa en `549ed08` y se verificó contra la copia de trabajo: idéntica. Lección: después de reemplazar un archivo, mirar `git status` antes del commit (una `U` o un `D` inesperado avisa). **Se repitió en `1481cb0`** (el commit del log borró la bitácora: 121 líneas eliminadas) y el `git status` previo no llegó a verse completo. Causa: el reemplazo a mano desde el Explorador (renombrar + reemplazar) es frágil y el error del método fue de Claude, que lo indicó así. Corrección: comando único de PowerShell que copia la última bitácora descargada encima de la del repo (sección "Cómo actualizar este archivo").
- **H17 — Prueba real del MCP: la base muestra lo que el archivo no dice (5/11).** Con la contraseña sincronizada, el agente (Big Pickle) respondió en 10,6 s con columnas, índices y restricciones de `turno`, y confirmó que no hay columna de sillón/box. Diferencia con H12: ahora lista `PRIMARY (id)` y dos índices **que no están escritos en `V6__turno.sql`**: `fk_turno_especialidad (especialidad_id)` y `fk_turno_created_by (created_by)`. MySQL los crea solo para las claves foráneas que no tienen un índice que empiece por esa columna; `paciente_id` y `profesional_id` no los necesitan porque ya encabezan `idx_turno_pac_fecha` e `idx_turno_prof_fecha`. Es la huella de que la respuesta salió de la base real y no del archivo, y un dato útil para el diseño del C-13: el futuro `sillon_id` necesitará su propio índice compuesto (`sillon_id, fecha, hora_inicio`) para validar solapes, como ya pasa con profesional. **Prueba en el log** (`opencode.log`, línea 566, 2026-11-05 14:56:52 hora local, sesión `a76614d8`): `evaluated permission=mysql_mysql_query` = servidor `mysql` + herramienta `mysql_query`. En esa sesión no hay ninguna línea de `bash` ni de `edit`, y el agente no pidió permisos: usó solo el MCP. Contraste con la sesión del 3/11 (`4bf74bb7`, H14): ahí aparece `permission=bash` ejecutando un `mcp-client.js` propio con `call mysql_query`, o sea, el agente imitando al MCP por fuera.
- **H18 — Instalación de la skill creadora sin npx (6/11).** Por H9 (npx falló en este equipo) no se usa `npx skills add`. Se trae solo la carpeta `skills/skill-creator` del repositorio oficial `anthropics/skills` con `git clone --sparse` y se copia a la carpeta global de skills de OpenCode (`~/.config/opencode/skills/`), para no mezclar una herramienta en el repo del proyecto. Del `skill-creator` se usa el flujo de creación (entrevista → borrador de `SKILL.md`); su ciclo de evaluación automática (`scripts/run_eval.py`, visor de evals) está pensado para Claude Code y Python, y no se usa.
- **H19 — Revisión crítica de la skill generada (6/11).** La versión del agente (`7b3928b`) era sólida (formato, checklist de 10 bordes, citas obligatorias, solo lectura), pero tenía 5 problemas, cada uno contrastado con una fuente:
  1. Obligaba a revisar `RN-TU-01` a `RN-TU-10`, pero el README pone **RN-TU-10 fuera de alcance de v1** → habría marcado "SIN CUBRIR" en falso. Ajuste: RN-TU-10 = "No aplica" salvo que el change la active.
  2. En el borde 3 daba por hecho que un turno que **termina** después del cierre debe rechazarse, pero **RN-TU-03 solo habla de `hora_inicio`** → el agente casi inventa una regla. Ajuste: marcarlo como "decisión de dominio a justificar".
  3. "Un ATENDIDO no debería bloquear" sin cita → se reescribió citando RN-TU-01 (solo `RESERVADO` cuenta).
  4. **No contemplaba reglas nuevas que propone el propio change** (el C-13 propone RN-TU-11, por sillón): las habría tratado como inventadas. Ajuste: leer `proposal.md`/`design.md` y tratarlas como "regla propuesta por el change", exigiendo escenarios y actualización de la KB.
  5. Aunque su descripción menciona sillón/box, **el checklist no tenía ningún borde de sillón**. Ajuste: ítem 11 (mismo sillón con profesionales distintos, sillones distintos, contiguo, cancelado libera, turnos viejos sin sillón).
- **H20 — Un comodín de Windows pisó la skill (6/11, verificado en GitHub).** En `b677119`, el comando `U "SKILL*.md"` tomó el archivo más reciente que empezaba con "skill": **`skill-registry.md`**, porque Windows no distingue mayúsculas en los nombres. Resultado: `.opencode/skills/revisor-escenarios-turnos/SKILL.md` quedó con el contenido del registro (por eso el commit marcó 211 líneas cambiadas en ese archivo). Se detectó comparando lo subido con lo entregado. Corrección: copiar el `SKILL.md` excluyendo `skill-registry*`. La evidencia del 2.3 se mira con `git diff 7b3928b <commit de corrección> -- .opencode/skills/revisor-escenarios-turnos/SKILL.md`, que salta el commit roto.
- **H21 — El servicio de MySQL estaba detenido (6/11).** Antes del explore, `Get-Service *mysql*` mostró `MySQL2610` en `Stopped`: había quedado con arranque manual. Sin el servicio, el MCP no puede conectar. Solución (PowerShell como administrador): `Set-Service MySQL2610 -StartupType Automatic; Start-Service MySQL2610`. Va a la tabla de problemas del README.
- **H22 — Explore del C-13 verificado (6/11).** El agente usó `mysql_query` (3 llamadas en el log) y su resumen coincide con la base: no hay tabla `sillon`, no hay `sillon_id`, y los turnos 1 y 2 (profesionales distintos, 2026-11-03 10:00, `RESERVADO`) son el choque de sillón que hoy se acepta. Claude contrastó con el código las afirmaciones sobre concurrencia y errores: `TurnoService` usa TX `SERIALIZABLE`, un lock JVM por profesional (`synchronized`), `PESSIMISTIC_WRITE` sobre `profesional`, y `TurnoOcupadoException("Horario ocupado")` con sugeridos; todo es cierto. Punto a vigilar en el propose: el diseño agrega un segundo lock (sillón) y el comentario del código dice "sin anidamiento → sin deadlock"; tomar siempre los locks en el mismo orden (profesional → sillón) evita el deadlock, pero el `design.md` tiene que justificarlo. Aporte del agente que no estaba en el prompt: los `sugeridos` del 409 también tienen que filtrar por sillón. Nota para el video: los turnos de prueba se crearon para "mañana" del 2/11, así que ya están en el pasado; antes de grabar, volver a correr `scripts/dev-db-nativo.sql`.
- **H23 — Revisión del propose del C-13 (6/11).** Claude leyó los 4 artefactos desde GitHub. Respetan las 4 decisiones de Lucas y el `design.md` justifica el punto que vigilaba H22: los dos locks se toman siempre en orden profesional → sillón (en la JVM y en la base), así no se puede formar un ciclo de espera, y se actualiza el comentario del código. Detalles finos del agente: la columna queda nullable pero la obligatoriedad vive en el DTO; el índice `idx_turno_sillon_fecha` empieza por `sillon_id` para no generar otro índice automático (usa lo aprendido en H17). Huecos que Claude **no** corrigió a propósito, para ver si la skill los detecta: (1) no hay escenario de **concurrencia** en el spec (solo una tarea de test); (2) los escenarios ponen el estado previo dentro del WHEN, sin GIVEN explícito; (3) el `design.md` dice que reprogramar con un sillón distinto al original da 400, pero no hay escenario. `openspec validate` no se pudo correr desde el entorno de Claude (sin acceso a npm); lo corre Lucas.
