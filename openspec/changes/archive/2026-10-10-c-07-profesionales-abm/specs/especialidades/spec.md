# Spec Delta — especialidades (C-07)

## MODIFIED Requirements

### Requirement: Profesionales por especialidad — contrato congelado hasta C-07

El sistema SHALL exponer `GET /api/especialidades/{id}/profesionales?page=&size=` que retorna página de profesionales vinculados ordenados por `apellido, nombre` (200; 404 si la especialidad no existe); desde C-07 lista los profesionales reales con `activo` cualquiera (filtro opcional `?activo=`); paginación, orden y RBAC iguales al contrato v1.

#### Scenario: Lista real de vinculados

- **WHEN** un rol autenticado llama `GET /api/especialidades/{id}/profesionales` existiendo 2 profesionales vinculados (uno activo, uno inactivo)
- **THEN** responde 200 con ambos en `content` ordenados por `apellido, nombre`.

#### Scenario: Lista vacía sin vinculados

- **WHEN** un rol autenticado llama `GET /api/especialidades/{id}/profesionales` con especialidad existente sin profesionales
- **THEN** responde 200 con `content:[]` y metadatos de paginación.

#### Scenario: Lista vacía pre-C-07

- **WHEN** un rol autenticado llama `GET /api/especialidades/{id}/profesionales` con especialidad existente
- **THEN** responde 200 con `content:[]` y metadatos de paginación.

#### Scenario: Id inexistente devuelve 404

- **WHEN** se llama `GET /api/especialidades/999999/profesionales`
- **THEN** responde 404.
