# Índice de capturas

| Archivo | Qué muestra | Qué prueba | Paso |
|---|---|---|---|
| `Captura.PNG` | README oficial de `mcp-server-mysql`: herramienta `mysql_query` y recurso `mysql://tables` | Nombre real de la herramienta, verificado a mano en la fuente (H3) | 1.4 |
| `Captura1.PNG` | README oficial de `mcp-server-mysql`: variable `MYSQL_PASS` y flags `ALLOW_*_OPERATION` | La variable es `MYSQL_PASS` (no `MYSQL_PASSWORD`, como dijo Gemini) y la escritura está desactivada por defecto (H1) | 1.4 |
| `Captura2.PNG` | README oficial de `mcp-server-mysql`: instalación rápida | Confirma `MYSQL_PASS` en otro ejemplo de la misma fuente (H1) | 1.4 |
| `Captura4.PNG` | README oficial de Context7: herramientas `resolve-library-id` y `query-docs` | Nombres reales de las herramientas del MCP comparado (H3) | 1.4 |
| `Captura5.PNG` | OpenCode lista las skills disponibles, entre ellas `skill-creator` | La skill creadora quedó instalada y el agente la ve | 2.1 |
| `Captura6.PNG` | Fin de la generación de la skill: reglas de la auditoría y "solo se creó ese archivo" | La skill se generó con `skill-creator` respetando las restricciones del prompt | 2.2 |
| `Captura7.PNG` | Sesión nueva: sin nombrarla, el agente describe `revisor-escenarios-turnos`, qué entrega y qué necesita | El agente encuentra la skill por las instrucciones del proyecto | 2.7 |
| `candado2-delete-denegado.png` | Workbench con el usuario `agente_mcp`: `SELECT` funciona, `DELETE` da *Error 1142* | El usuario del MCP es de solo lectura (H7) | E.6 |
| `confirmacion de evidencias.txt` | Nota del 2/11 sobre la verificación de los README | Respaldo en texto de las capturas de 1.4 | 1.4 |
| `gfdgsd.txt` | Comando usado para extraer líneas del log de OpenCode | Cómo se obtuvo el registro del MCP | 3.2 |

El uso real del MCP está en [`../log-mcp-uso.txt`](../log-mcp-uso.txt), y el de la skill en los informes [`../c13-revision-skill.md`](../c13-revision-skill.md) y [`../c13-revision-skill-2.md`](../c13-revision-skill-2.md).
