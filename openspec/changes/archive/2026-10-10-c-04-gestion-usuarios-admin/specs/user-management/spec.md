# Spec Delta — user-management

## Purpose

Permite al rol ADMIN gestionar el plantel de usuarios (alta, edición, desactivación y asignación de rol) sobre la entidad Usuario existente, garantizando unicidad, RBAC estricto y protección del último administrador activo.

## ADDED Requirements

### Requirement: Listar usuarios paginado solo ADMIN
El sistema SHALL exponer `GET /api/usuarios?page=&size=&sort=` que retorna `Page<UsuarioResponse>` (default `size=20`, `sort=username,asc`) solo a `rol=ADMIN`; sin `password_hash` en el body.

#### Scenario: ADMIN lista usuarios paginado
- **WHEN** un ADMIN autenticado hace `GET /api/usuarios?page=0&size=20` con `Accept: application/json`
- **THEN** el sistema retorna 200 con `content[]` de `UsuarioResponse`, `totalElements`, `totalPages` y ningún elemento contiene `password_hash`

#### Scenario: Listado vacío retorna página vacía 200
- **WHEN** un ADMIN pide `GET /api/usuarios` y no hay usuarios además de sí mismo o la página excede el total
- **THEN** el sistema retorna 200 con `content=[]` y `totalElements` acorde, sin error

### Requirement: Alta de usuario con unicidad y password hasheado
El sistema SHALL exponer `POST /api/usuarios` solo ADMIN que crea `Usuario` con `username` único obligatorio, `password` min 8 chars hasheado BCrypt(12), `rol` enum obligatorio y `email` opcional único; éxito → 201 `UsuarioResponse`.

#### Scenario: Alta válida retorna 201
- **WHEN** un ADMIN envía `POST /api/usuarios` con `{"username":"odonto1","password":"segura123","rol":"ODONTOLOGO","email":"o1@clinica.local"}`
- **THEN** el sistema persiste `Usuario` con `password_hash` `$2a$12$...`, retorna 201 con `UsuarioResponse` sin hash y `created_at/updated_at` no nulos

#### Scenario: Username duplicado retorna 409
- **WHEN** existe `usuario(username="odonto1")` y un ADMIN envía `POST /api/usuarios` con el mismo `username` y otro email
- **THEN** el sistema retorna 409 problem-details con `fieldErrors.username` indicando duplicado y no persiste fila

#### Scenario: Email duplicado retorna 409
- **WHEN** existe `usuario(email="dup@clinica.local")` y un ADMIN envía alta con el mismo email no nulo
- **THEN** el sistema retorna 409 con `fieldErrors.email` y no persiste fila

#### Scenario: Password corto o rol inválido retorna 400
- **WHEN** un ADMIN envía `POST /api/usuarios` con `password="corta"` o `rol="INVALID"` o sin `rol`
- **THEN** el sistema retorna 400 con `fieldErrors` por campo y no persiste fila

### Requirement: Edición parcial de email rol y activo
El sistema SHALL exponer `PATCH /api/usuarios/{id}` solo ADMIN que actualiza `email`, `rol` y/o `activo` (username inmutable, password fuera de alcance v1); valida unicidad de email y enum de rol; éxito → 200 `UsuarioResponse` con `updated_at` posterior.

#### Scenario: Cambio de rol válido retorna 200
- **WHEN** un ADMIN envía `PATCH /api/usuarios/{id}` con `{"rol":"SECRETARIA"}` sobre usuario no-último-admin
- **THEN** el sistema actualiza `rol`, avanza `updated_at`, retorna 200 y registra log de cambio de rol

#### Scenario: Username en PATCH se ignora o rechaza sin mutar
- **WHEN** un ADMIN envía `PATCH /api/usuarios/{id}` con `{"username":"otro"}`
- **THEN** el sistema no modifica `username` (lo ignora o responde 400) y el resto de campos válidos se aplican o todo se rechaza sin cambio parcial silencioso

#### Scenario: Email duplicado en edición retorna 409
- **WHEN** un ADMIN edita `usuario A` con `email` igual al de `usuario B` existente no nulo
- **THEN** el sistema retorna 409 con `fieldErrors.email` y no modifica la fila

### Requirement: Desactivar usuario y protección último ADMIN
El sistema SHALL exponer `PATCH /api/usuarios/{id}/desactivar` solo ADMIN (idempotente, pone `activo=false`) y SHALL rechazar con 400 toda operación que deje cero admins activos (desactivar o degradar rol del último `ADMIN` con `activo=true`).

#### Scenario: Desactivar usuario común retorna 200
- **WHEN** un ADMIN envía `PATCH /api/usuarios/{id}/desactivar` sobre usuario no-admin activo
- **THEN** el sistema pone `activo=false`, retorna 200 y ese usuario recibe 401 en futuros logins

#### Scenario: Desactivar último ADMIN activo retorna 400
- **WHEN** existe un solo `Usuario(rol=ADMIN, activo=true)` y un ADMIN pide desactivarlo o cambiarle el rol
- **THEN** el sistema retorna 400 problem-details (`detail` indica último administrador), no modifica la fila y el conteo de admins activos sigue en 1

#### Scenario: Degradar rol del último ADMIN retorna 400
- **WHEN** existe un solo admin activo y se envía `PATCH /api/usuarios/{id}` con `{"rol":"SECRETARIA"}`
- **THEN** el sistema retorna 400 sin cambiar `rol` ni `activo`

### Requirement: RBAC ADMIN-only con 403 para el resto
El sistema SHALL exigir `hasRole('ADMIN')` en los 4 endpoints (`GET`, `POST`, `PATCH`, `PATCH /desactivar`) y retornar 403 problem-details a `PACIENTE/ODONTOLOGO/SECRETARIA` autenticados y 401/302 a no autenticados según `Accept`.

#### Scenario: PACIENTE pide listar usuarios recibe 403
- **WHEN** un usuario `rol=PACIENTE` autenticado hace `GET /api/usuarios` con `Accept: application/json`
- **THEN** el sistema retorna 403 sin exponer datos y sin ejecutar lógica de negocio

#### Scenario: SECRETARIA intenta alta recibe 403
- **WHEN** un usuario `rol=SECRETARIA` autenticado hace `POST /api/usuarios` válido
- **THEN** el sistema retorna 403 y no persiste fila

#### Scenario: Sin sesión pide endpoint admin recibe 401 o 302
- **WHEN** un cliente sin sesión hace `GET /api/usuarios` con `Accept: application/json` o navegador a vista admin
- **THEN** el sistema retorna 401 problem-details para API o 302 a `/login` para HTML

### Requirement: Auditoría y no exposición de hash
El sistema SHALL persistir `created_at/updated_at` en alta y edición, emitir log SLF4J de cada cambio de rol (`usuarioId`, `actor`, `rolAnterior`, `rolNuevo`) sin PII sensible, y SHALL nunca incluir `password_hash` en responses, problem-details ni logs.

#### Scenario: Alta y edición actualizan timestamps
- **WHEN** se crea un usuario y luego se edita su `email`
- **THEN** `created_at` permanece igual al de creación y `updated_at` de edición es posterior

#### Scenario: Response nunca expone hash
- **WHEN** se inspecciona cualquier `200/201` de `/api/usuarios` o log de cambio de rol
- **THEN** el body/log contiene `id, username, email, rol, activo, createdAt, updatedAt` y no contiene `password_hash` ni `password`
