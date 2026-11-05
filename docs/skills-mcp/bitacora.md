# Bitácora — TP Skills + MCP (Metodología I)

> Archivo anti-olvido. Va en `docs/skills-mcp/bitacora.md`. Se lee al empezar cada sesión
> y se actualiza al terminar. Un commit por paso que funciona (sin firma de IA) + `git push`.
> No reemplaza a AGENTS.md / CLAUDE.md: esas son reglas del agente, esto es el avance.

## Reglas fijas de este TP
- No regenerar la fundación ni tocar `.active-orchestrator-state.json` para "maquillar" etapas.
- El change nuevo queda **preparado**, NO implementado, verificado ni archivado.
- El MCP se documenta como **integración** (README / AGENTS.md), nunca en `.atl/skill-registry.md`.
- Datos ficticios siempre. Cero tokens/claves en repo, capturas o video.
- Todo dato de una herramienta (versión, ruta, comando) se contrasta con su doc oficial: URL + fecha.

## Estado actual
- Actualizado: 2026-11-05 14:50
- Fase: 3 — contraseña sincronizada; conexión del agente a MySQL comprobada
- Último commit confirmado: `feat: integrar servidor MCP de MySQL en solo lectura para OpenCode` (3/11). Pendiente de commit: bloque `permission` de `opencode.json` y esta bitácora
- Próximo paso concreto: commitear `opencode.json` + bitácora; después, **3.2**: abrir OpenCode, ver `mysql` conectado en el panel lateral y pedirle `SHOW CREATE TABLE turno` usando SOLO el MCP
- Bloqueos / dudas para el profe: fecha de clase y de entrega (desconocidas → modo simulacro)

## Entornos
| Máquina | Sistema | OpenCode | Node | Base de datos | Proveedor de modelo |
|---|---|---|---|---|---|
| Notebook Lenovo | Linux Mint | 1.19.5 (V1) | instalado | Docker (MySQL 8.0) | OpenCode Zen |
| Escritorio | Windows 10 Enterprise LTSC 1809 | 1.19.5 (V1) — NO actualizar a 2.0 | v26.13.0 / npm 11.20.0 | MySQL Server 26.10.0 nativo + Workbench 8.0 | OpenCode, modelo gratuito Big Pickle (sin API key) |

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
- [ ] E.10 Instalar el CLI `openspec` (lo exigen las skills OPSX del repo; antes de la fase 4)
- Nota: OpenCode se usa desde la terminal de VS Code; la consola vieja de Windows no dibuja los íconos (`?`) ni pega con Ctrl+V

## Fase 1 — Ficha de investigación → `docs/skills-mcp/investigacion.md` (máx 2 páginas)
- [x] 1.1–1.7 Borrador hecho
- [ ] 1.8 Actualizar el borrador: versión V1 confirmada, MySQL nativo en lugar de Docker, `{env:}` verificado
- [ ] 1.9 Subir la ficha al repo

## Fase 2 — Skill propia
- [ ] 2.1 Instalar la skill creadora y comprobar que el agente la ve
- [ ] 2.2 Generar la skill propia con ella (guardar el prompt usado)
- [ ] 2.3 Revisar y adaptar lo generado al dominio (anotar qué se cambió)
- [ ] 2.4 Guardarla en `.opencode/skills/revisor-escenarios-turnos/SKILL.md`
- [ ] 2.5 Registrar en `.atl/skill-registry.md`
- [ ] 2.6 Actualizar `AGENTS.md` y `CLAUDE.md` con el mismo texto
- [ ] 2.7 Sesión nueva del agente: comprobar que la encuentra sin ayuda

## Fase 3 — MCP
- [x] 3.1 Conexión comprobada (= E.8)
- [ ] 3.2 Ejecutar 1 herramienta real (`SHOW CREATE TABLE turno`) y guardar pedido + resultado
- [x] 3.3 `opencode.json` de ejemplo sin secretos
- [x] 3.3b Tercer candado: permisos `bash` y `edit` en `ask` dentro de `opencode.json` (por H14) — 3/11, confirmado por Lucas; falta verlo en un commit
- [ ] 3.4 Sección MCP en README (requisitos, pasos, variables, cómo probar, problema y solución)
- [ ] 3.5 AGENTS.md / CLAUDE.md: propósito del MCP, tools, cuándo, límites

## Fase 4 — Preparar el change C-13 (SDD)
- [ ] 4.1 Explore (usando el MCP) → guardar resumen
- [ ] 4.2 Propose: proposal.md, design.md, specs/, tasks.md
- [ ] 4.3 Spec con ≥3 escenarios dado/cuando/entonces: feliz, error de negocio, borde
- [ ] 4.4 Pasar la skill propia sobre el spec → corregir con Ajustar
- [ ] 4.5 tasks.md indica cómo se testea cada escenario
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
| 2026-11-05 | Retomamos; paso 18: `ALTER USER` + `setx` con la misma clave → `CONEXION OK [ { n: 4 } ]`. Se detectó que la bitácora descargada quedó como `bitacora (8).md` sin reemplazar la del repo (las versiones con H9b–H15 nunca se habían subido) | pendiente | Commit; prueba real del MCP (3.2) |
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
