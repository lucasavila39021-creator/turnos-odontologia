# Turnos Odontología — Base de Conocimiento

Base de conocimiento generada en modo interactivo (P0-sys: web-app, P0-scale: small, P5: mantenibilidad). Stack orientado a entorno universitario: Java/Python + MySQL.

## Índice de Archivos

| Archivo | Contenido |
|---------|-----------|
| [01_vision_y_objetivos.md](01_vision_y_objetivos.md) | Propósito, objetivos por actor, alcance v1, fuera de alcance, métricas |
| [02_descripcion_general.md](02_descripcion_general.md) | Stack (Java/Python + MySQL), arquitectura monolito modular, API |
| [03_actores_y_roles.md](03_actores_y_roles.md) | 4 actores, matriz RBAC, rutas públicas |
| [04_modelo_de_datos.md](04_modelo_de_datos.md) | Dominios, ERD, 6 entidades + intermedia, índices, seed |
| [05_reglas_de_negocio.md](05_reglas_de_negocio.md) | RN-AU/PR/PA/TU/GL con códigos trazables |
| [06_funcionalidades.md](06_funcionalidades.md) | 4 épicas, 9 US con CAs y reglas |
| [07_flujos_principales.md](07_flujos_principales.md) | 6 flujos extremo a extremo con casos de error |
| [08_arquitectura_propuesta.md](08_arquitectura_propuesta.md) | Patrones, estructura directorios Java/Python, seguridad, env vars |
| [09_decisiones_y_supuestos.md](09_decisiones_y_supuestos.md) | 7 decisiones (DD-01..07) + 6 supuestos (SU-01..06) |
| [10_preguntas_abiertas.md](10_preguntas_abiertas.md) | 2 inconsistencias + 11 preguntas priorizadas |

## Quick Start para Desarrolladores

1. **Entender el dominio** → [01](01_vision_y_objetivos.md), [03](03_actores_y_roles.md)
2. **Entender los datos** → [04](04_modelo_de_datos.md)
3. **Entender las reglas** → [05](05_reglas_de_negocio.md)
4. **Entender la arquitectura** → [02](02_descripcion_general.md), [08](08_arquitectura_propuesta.md)
5. **Implementar** → [07](07_flujos_principales.md), [06](06_funcionalidades.md) — en orden US-001 → US-034
6. **Antes de codificar** → [10](10_preguntas_abiertas.md) — resolver Alta prioridad

## Resumen Ejecutivo

Sistema web para una clínica odontológica universitaria que centraliza la agenda en un calendario único sin superposiciones. MVP v1: ABM pacientes/profesionales/especialidades + alta/cancelación/consulta de turnos con RBAC de 4 roles. Stack Java/Python + MySQL, monolito modular, prioridad mantenibilidad para evolucionar a recordatorios e historia clínica en v2.

## Cómo validar la KB

- Recorrer `10_preguntas_abiertas.md` con la clínica/cátedra y cerrar las 3 Altas antes del sprint 1.
- Revisar `05` y `07` con secretaria/odontólogo para confirmar RN-TU-01/03/05 y flujos de reserva/cancelación.
- Elegir variante de stack (Java o Python) y congelar `08` antes de scaffoldear código.
