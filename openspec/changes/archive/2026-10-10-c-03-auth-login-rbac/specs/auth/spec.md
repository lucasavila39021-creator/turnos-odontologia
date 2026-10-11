# Spec Delta — auth

## Purpose

Provee autenticación completa y RBAC por rol para que solo usuarios verificados con sesión válida accedan a recursos según su rol, mitigando acceso anónimo, enumeración y brute force, y sirviendo como base para que C-04..C-12 hagan cumplir RN-AU-01..04 en backend.

## ADDED Requirements

### Requirement: Login con validación bcrypt y error genérico 401
El sistema SHALL validar `POST /api/auth/login` (JSON) y `POST /login` (form) con `username`+`password` contra `Usuario.password_hash` vía `PasswordEncoder.matches` (BCrypt 12), colación case-insensitive por `utf8mb4_0900_ai_ci`, y responder 200 con `LoginResponse {id, username, rol, email}` y `Set-Cookie JSESSIONID` en éxito, o 401 genérico `{"error":"Credenciales inválidas"}` sin revelar si usuario existe o está inactivo.

#### Scenario: Login exitoso retorna 200 y cookie de sesión
- **WHEN** un cliente envía `POST /api/auth/login` con `{"username":"admin","password":"admin123"}` válido y `activo=true`
- **THEN** el sistema retorna 200 con `LoginResponse` (`id`, `username`, `rol`, `email`), `Set-Cookie: JSESSIONID=...; HttpOnly; Path=/; SameSite=Lax` y crea sesión server-side; `password_hash` nunca se expone en body ni logs

#### Scenario: Password incorrecto retorna 401 genérico sin revelar existencia
- **WHEN** se envía `POST /api/auth/login` con `username` existente pero `password` incorrecto
- **THEN** el sistema retorna 401 con body `{"error":"Credenciales inválidas"}` (mismo mensaje y status que para usuario inexistente) y no crea sesión

#### Scenario: Usuario inactivo retorna 401 genérico igual que credenciales inválidas
- **WHEN** existe `Usuario(username="inact", activo=false)` y se envía `POST /api/auth/login` con su password correcto
- **THEN** el sistema retorna 401 `{"error":"Credenciales inválidas"}` (no distingue inactivo de inválido) y no crea sesión; error se mapea desde `DisabledException`

#### Scenario: Login case-insensitive por collation
- **WHEN** existe `usuario(username="Admin")` y se envía `POST /api/auth/login` con `username="admin"` (distinta capitalización) y password correcto
- **THEN** el sistema autentica exitosamente (200) porque `usuario.username` colaciona `utf8mb4_0900_ai_ci` y `findByUsernameIgnoreCase` es case-insensitive

### Requirement: Sesión server-side con atributos HttpOnly Secure SameSite=Lax y endpoints me/logout
El sistema SHALL usar sesión server-side (`HttpSession` + `JSESSIONID`) con `HttpOnly`, `Secure` (true en prod, configurable en dev), `SameSite=Lax`, `Path=/`, exponer `GET /api/auth/me` → `MeResponse {id, username, rol, email}` si autenticado o 401 si no, y `POST /api/auth/logout` que invalida sesión/`SecurityContext` y limpia `JSESSIONID`/`XSRF-TOKEN` con 204.

#### Scenario: Me retorna datos del usuario autenticado
- **WHEN** un cliente autenticado (cookie `JSESSIONID` válida) hace `GET /api/auth/me` con `Accept: application/json`
- **THEN** el sistema retorna 200 con `MeResponse` (`id`, `username`, `rol`, `email` del `CustomUserDetails`) y nunca incluye `passwordHash`

#### Scenario: Me sin sesión retorna 401 JSON o 302 a login según Accept
- **WHEN** un cliente sin sesión hace `GET /api/auth/me` sin cookie
- **THEN** si `Accept: application/json` retorna 401 con body problem-details, si `Accept: text/html` retorna 302 `Location: /login`

#### Scenario: Logout invalida sesión y me posterior es 401
- **WHEN** cliente autenticado hace `POST /api/auth/logout` (con `X-XSRF-TOKEN` si CSRF activo)
- **THEN** el sistema invalida `HttpSession`, limpia `SecurityContext`, borra cookies y retorna 204; un `GET /api/auth/me` posterior con la cookie vieja retorna 401/302

#### Scenario: Cookie JSESSIONID tiene atributos seguros
- **WHEN** login exitoso retorna `Set-Cookie`
- **THEN** el header contiene `HttpOnly` y `SameSite=Lax` y en perfil `prod` `Secure`; en `dev` `Secure` puede ser false según `APP_COOKIE_SECURE`

### Requirement: Rate limiting 5 intentos / 60s por IP+username con 429 y Retry-After
El sistema SHALL limitar `POST /api/auth/login` y `POST /login` a 5 intentos fallidos por 60s por clave `IP + username` normalizado (case-insensitive, `X-Forwarded-For` first + `remoteAddr` fallback con `ForwardedHeaderFilter` + `server.forward-headers-strategy=native`), retornando 429 con `Retry-After` y body problem-details cuando se excede, sin contar éxitos.

#### Scenario: Sexto intento fallido dentro de ventana retorna 429
- **WHEN** se hacen 5 `POST /api/auth/login` fallidos (401) con misma `IP` y `username="admin"` en <60s y luego un sexto idéntico
- **THEN** el sexto retorna 429 con header `Retry-After: 60` (o segundos restantes) y body `{"title":"Too Many Requests","status":429,"detail":"Demasiados intentos. Reintentá en 60s."}` y no llega a validar bcrypt

#### Scenario: Rate limit es por IP+username no solo IP
- **WHEN** se excede 5 fallos para `IP=1.1.1.1 + username=admin` y se hace `POST /api/auth/login` con `IP=1.1.1.1 + username=other` distinto
- **THEN** el request para `other` no es 429 (clave distinta) y se valida normal 401/200

#### Scenario: Login exitoso no incrementa contador pero fallos posteriores sí
- **WHEN** se hace login exitoso (200) intercalado entre fallos para misma clave
- **THEN** el exitoso no cuenta para el límite; solo fallos 401 cuentan hasta 5/60s

### Requirement: Middleware AuthRequired con rutas públicas y protección 401/302
El sistema SHALL exigir autenticación en toda ruta salvo `GET /`, `GET /login`, `POST /login`, `POST /api/auth/login`, `GET /api/health`, `GET /actuator/health`, `/css/**`, `/js/**`, `/error`, retornando 401 JSON si `Accept: application/json` o 302 a `/login` si `text/html` cuando no autenticado (RN-AU-01).

#### Scenario: Ruta protegida sin sesión retorna 401 JSON para API
- **WHEN** cliente sin sesión hace `GET /api/auth/me` o `GET /api/usuarios` futuro con `Accept: application/json`
- **THEN** el sistema retorna 401 (no 200 ni 403) con problem-details y no expone datos

#### Scenario: Ruta protegida sin sesión redirige a login para navegador
- **WHEN** cliente sin sesión hace `GET /agenda` o `GET /mis-turnos` con `Accept: text/html`
- **THEN** el sistema retorna 302 `Location: /login` (o `/login?expired` si sesión expirada)

#### Scenario: Rutas públicas no requieren auth
- **WHEN** cliente sin sesión hace `GET /api/health` o `GET /login` o `GET /` o `GET /css/main.css`
- **THEN** el sistema retorna 200 (o 302 para `/` según implementación) sin exigir autenticación

### Requirement: RBAC por rol en backend con @PreAuthorize / PermissionContext (RN-AU-02/03/04)
El sistema SHALL hacer cumplir autorización por rol en backend con `@EnableMethodSecurity` + `@PreAuthorize("hasRole('...')")` o `@authz.isOwner(#id)` (deny-by-default), verificando RN-AU-02 (no solo UI), RN-AU-03 (Paciente solo propios) y RN-AU-04 (Odontólogo solo su agenda), retornando 403 `Forbidden` en bypass por URL/API directa.

#### Scenario: Endpoint admin-only con rol PACIENTE retorna 403
- **WHEN** usuario autenticado con `rol=PACIENTE` hace `GET /api/test/admin-only` (placeholder de C-03) o futuro `GET /api/usuarios`
- **THEN** el sistema retorna 403 con problem-details (no 200 ni 401) aunque la URL sea conocida

#### Scenario: Endpoint secretaria-only con rol ODONTOLOGO retorna 403
- **WHEN** usuario `rol=ODONTOLOGO` intenta `POST /api/usuarios` o `POST /api/especialidades`
- **THEN** el sistema retorna 403; el check ocurre en `SecurityFilterChain`/`@PreAuthorize` antes de lógica de negocio

#### Scenario: Paciente no puede ver recurso de otro paciente (RN-AU-03)
- **WHEN** `PACIENTE_A` autenticado pide `GET /api/pacientes/{id_B}` donde `paciente_B.usuario_id != A.id`
- **THEN** el sistema retorna 403 (o 404 si se oculta existencia) pero nunca 200 con datos de B; este change prueba el mecanismo con `TestSecuredController` y deja semántica lista para C-05

### Requirement: Redirección por rol tras login exitoso (US-001 CA-2)
El sistema SHALL redirigir tras `POST /login` form exitoso según `rol`: `PACIENTE → /mis-turnos`, `ODONTOLOGO → /agenda`, `SECRETARIA/ADMIN → /agenda`; para `POST /api/auth/login` JSON retorna 200 con `LoginResponse` y no redirige, soportando `Accept` negotiation.

#### Scenario: Login form de PACIENTE redirige a /mis-turnos
- **WHEN** `PACIENTE` hace `POST /login` form con credenciales válidas y `Accept: text/html`
- **THEN** el sistema crea sesión y responde 302 `Location: /mis-turnos`

#### Scenario: Login JSON de ODONTOLOGO retorna 200 con rol sin redirect
- **WHEN** `ODONTOLOGO` hace `POST /api/auth/login` con `Accept: application/json` y credenciales válidas
- **THEN** el sistema retorna 200 `LoginResponse{rol=ODONTOLOGO}` sin `Location` header; cliente decide navegación

### Requirement: Validación de LoginRequest y manejo RFC7807 con mensajes por campo
El sistema SHALL validar `LoginRequest` con Bean Validation (`@NotBlank` `username`, `@NotBlank` `password`, `@Size(max=50)` username) y responder 400 con problem-details `fieldErrors` cuando falten campos, nunca 500; y mapear `BadCredentialsException`/`DisabledException` → 401 genérico, `AccessDeniedException` → 403, `RateLimitExceeded` → 429.

#### Scenario: Login con username blank retorna 400 con fieldErrors
- **WHEN** se envía `POST /api/auth/login` con `{"username":"","password":"x"}`
- **THEN** el sistema retorna 400 con `application/problem+json` y `fieldErrors.username` indica `must not be blank`

#### Scenario: Error 401 y 403 usan problem-details sin filtrar interno
- **WHEN** ocurre `BadCredentialsException` o `AccessDeniedException`
- **THEN** el body es `{"type":"about:blank","title":"...","status":401|403,"detail":"Credenciales inválidas"|"Forbidden"}` sin stacktrace ni `passwordHash`
