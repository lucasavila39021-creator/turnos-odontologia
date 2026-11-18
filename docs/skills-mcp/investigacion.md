# Ficha de investigación — Skill propia y MCP

**Materia:** Metodología I · **Proyecto:** turnos-odontologia · **Integrante:** Lucas Ávila · **Fecha:** 2026-11-02 (actualizada 2026-11-06)

## 1. Necesidad detectada y change elegido

Todos los changes del roadmap original (C-01 a C-12) están archivados, por lo que se agregó a `CHANGES.md` el **C-13 `turnos-por-sillon`**. Surge de una pregunta abierta de prioridad Alta de la knowledge-base (`10_preguntas_abiertas.md`: "¿Cuántas sedes/sillones hay?"). Hoy un turno se valida por profesional (RN-TU-01) y por paciente (RN-TU-05), pero no por el recurso físico: dos profesionales distintos pueden reservar el mismo sillón a la misma hora.

- **Tarea repetible (skill):** cada change de turnos exige revisar que los escenarios cubran las reglas RN-TU y los casos de borde (turnos contiguos, cancelados que liberan el horario, límites del horario de atención). Hoy esa revisión es manual y depende de la memoria de quien la hace.
- **Necesidad de acceso (MCP):** para proponer el C-13 hay que conocer el esquema **real** de la tabla `turno` (columnas, índices, constraints), no el que describe la documentación.

## 2. Skill creadora y diseño de la skill propia

| Dato | Valor |
|---|---|
| Skill creadora | `skill-creator` (Anthropic) |
| Fuente oficial | https://github.com/anthropics/skills/tree/main/skills/skill-creator |
| Instalación | `npx skills add anthropics/skills@skill-creator`; plan B: copiar la carpeta `skills/skill-creator` del repositorio oficial a `.opencode/skills/` (en este equipo `npx` falló con el MCP) |
| Compatibilidad con OpenCode | OpenCode lee skills en formato `SKILL.md` (frontmatter `name` y `description`) desde `.opencode/skills/`, `.claude/skills/` y `.agents/skills/`, a nivel proyecto o global. |

**Skill propia: `revisor-escenarios-turnos`**

- **Propósito:** auditar el spec de un change de turnos contra las reglas de negocio de la knowledge-base y detectar escenarios faltantes o ambiguos.
- **Cuándo se activa:** al terminar `propose` de un change que toque turnos, antes de aprobar el spec.
- **Entradas:** ruta del `spec.md` del change y `knowledge-base/05_reglas_de_negocio.md`.
- **Procedimiento:** (1) listar los escenarios y clasificarlos en feliz, error de negocio o borde; (2) verificar el formato dado/cuando/entonces; (3) cruzar cada regla RN-TU que toca el change con al menos un escenario; (4) recorrer un checklist de bordes del dominio; (5) proponer los escenarios faltantes.
- **Resultado esperado:** tabla "regla → escenario que la cubre" y lista de escenarios faltantes, cada uno con su regla de origen.
- **Criterios de revisión:** no inventa reglas que no estén en la KB (las marca como "decisión de dominio a justificar"); cada hallazgo cita una regla o un caso del checklist; el spec final tiene como mínimo un escenario feliz, uno de error y uno de borde.
- **Ubicación:** `.opencode/skills/revisor-escenarios-turnos/SKILL.md`, versionada en el repositorio para que cualquier integrante la tenga al clonar.

## 3. Comparación de servidores MCP y elección

| Criterio | MySQL (benborla) — **elegido** | Context7 (Upstash) |
|---|---|---|
| Propósito | Consultar esquema y datos de una base MySQL | Documentación actualizada de librerías |
| Herramientas | `mysql_query`; recurso `mysql://tables` | `resolve-library-id`, `query-docs` |
| Tipo | Local (`npx -y @benborla29/mcp-server-mysql`) | Remoto (`https://mcp.context7.com/mcp`) |
| Requisitos | Node.js 20+, MySQL 5.7+ (el proyecto usa 8.0 en Docker; en este equipo, MySQL nativo) | Ninguno local |
| Credenciales | Usuario y contraseña de la base, por variables de entorno | API key opcional (solo amplía el límite de uso) |
| Permisos | Solo lectura por defecto; escritura opt-in con `ALLOW_INSERT/UPDATE/DELETE_OPERATION` | Solo consulta |
| Costo | Gratis, licencia MIT | Gratis |

**Elección:** MySQL. Aporta directamente al C-13 porque permite comprobar sobre la base real que `turno` no tiene `sillon_id` y revisar los índices existentes antes de diseñar la migración. Context7 queda como alternativa si no hubiera una base disponible.

## 4. Configuración aplicada y límites

Entorno real: OpenCode **1.19.5 (V1)**, en Windows 10 Enterprise LTSC 1809. Docker Desktop no instala en esa versión de Windows, así que la base corre en MySQL nativo, cargada con `scripts/dev-db-nativo.sql` (migraciones V2–V6 del proyecto y datos ficticios).

1. Servidor instalado de forma fija (`npm install -g @benborla29/mcp-server-mysql`), porque `npx` dejó la descarga a medias.
2. Usuario `agente_mcp` con permiso **solo `SELECT` y `SHOW VIEW`** sobre `turnos_db`.
3. `opencode.json` (raíz del repo), formato V1: clave `mcp` → `mysql`, `type: local`, `command: ["node", "<ruta>/dist/index.js"]`, `environment` con `MYSQL_PASS: "{env:MYSQL_PASS_AGENTE}"`. La contraseña vive solo en una variable del sistema.
4. Comprobación: `opencode mcp list` → `✓ mysql connected`; uso real de `mysql_query` con `SHOW CREATE TABLE turno`, registrado en el log de OpenCode.

**Límites (tres candados):** servidor sin flags de escritura; usuario MySQL de solo lectura (un `DELETE` devuelve *Error 1142*); y permisos de OpenCode `bash` y `edit` en `ask`, agregados después de ver que el agente, sin MCP, intentó resolver el pedido con scripts propios.

## 5. Archivos de la fundación a actualizar

`.opencode/skills/revisor-escenarios-turnos/SKILL.md` (nuevo) · `.atl/skill-registry.md` (registrar la skill) · `AGENTS.md` y `CLAUDE.md` (uso de la skill y del MCP, con el mismo texto) · `opencode.json` (nuevo, sin secretos) · `README.md` (sección MCP) · `knowledge-base/05_reglas_de_negocio.md` (RN-TU-11, propuesta en el change) · `knowledge-base/10_preguntas_abiertas.md` · `CHANGES.md` (estado del C-13).

## 6. Verificado y pendiente

- Verificado: OpenCode V1 resuelve `{env:VAR}` dentro de `environment` (la contraseña no está en el repo).
- Verificado: la configuración de V2 (`"mcp": {"servers": …}`) no aplica a la versión instalada.
- Pendiente: compatibilidad real de `skill-creator` con OpenCode (sus scripts de evaluación están pensados para Claude Code); se comprueba al instalarla.

## 7. Fuentes (consultadas el 2026-11-02)

- OpenCode — MCP servers: https://opencode.ai/docs/en/mcp-servers
- OpenCode — Skills: https://opencode.ai/docs/skills
- Repositorio de mcp-server-mysql (README verificado personalmente): https://github.com/benborla/mcp-server-mysql
- Repositorio de Context7 (README verificado personalmente): https://github.com/upstash/context7
- skill-creator, repositorio oficial: https://github.com/anthropics/skills/tree/main/skills/skill-creator
- Ficha de mcp-server-mysql: https://mcp.so/servers/mcp-server-mysql
- OpenCode V2 — MCP servers (para contrastar la sintaxis): https://opencode.ai/v2/docs/mcp-servers
- Docker Desktop — requisitos de Windows (mensaje del instalador 4.97.0, 2026-11-02)
