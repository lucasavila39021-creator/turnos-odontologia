# Design — C-03 auth-login-rbac

## Context

C-01 dejó `SecurityConfig` mínimo (`csrf.disable()`, `cors.disable()`, `formLogin` básico, `permitAll` para `/`, `/login`, `GET /api/health`, `BCryptPasswordEncoder(12)`) y `application.properties` con `server.forward-headers-strategy=native`, `problemdetails.enabled=true`, `TimeZoneConfig` fija. C-02 añadió `Usuario` (`username UNIQUE`, `email UNIQUE nullable`, `rol ENUM`, `activo`, `password_hash`) + `UsuarioRepository` + `DevDataInitializer admin/admin123`. `openspec list` vacío implica GATE 2 superado. Stack congelado en `08`: **Java Spring Boot 3.2.5 / Java 17 / MySQL 8.0 / Flyway / Maven** — no hay variante Python en paralelo. `CHANGES.md` scope exige `POST /api/auth/login` (JSON) o `POST /login` (form) con validación bcrypt, rate limit 5/60s por IP+username, 401 genérico, sesión server-side o JWT `HttpOnly Secure SameSite=Lax` (elegir una y documentar en `08`), `GET /api/auth/me`, `POST /api/auth/logout` (+ refresh si JWT), middleware AuthRequired salvo públicas, `PermissionContext/@PreAuthorize` con RN-AU-02/03/04, redirección por rol y DTOs `LoginRequest/LoginResponse/MeResponse`. Governance **CRITICO** — auth cruza 11 controllers futuros, concentra riesgos de enumeración, brute force, bypass RBAC y manejo de cookies. Ver `proposal.md` para motivación y `knowledge-base/03 §RBAC`, `05 RN-AU-01..06`, `07 Flujo 6` para comportamiento esperado.

## Goals / Non-Goals

**Goals:**
- Implementar login validado contra `Usuario` con `PasswordEncoder.matches` (BCrypt 12), 401 genérico para credenciales inválidas o `activo=false`, sesión server-side `JSESSIONID` con atributos `HttpOnly`/`Secure`/`SameSite=Lax`, y respuesta `LoginResponse`/`MeResponse` sin exponer hash.
- Incorporar `GET /api/auth/me` y `POST /api/auth/logout` (invalidación), y `AuthRequired` real vía `SecurityFilterChain` con lista cerrada de `permitAll` y `anyRequest().authenticated()`, compatible con `Accept: application/json` → 401 JSON vs form → 302 a `/login`.
- Añadir rate limiting 5/60s por `IP + username` (Bucket4j/caffeine in-memory) con `ForwardedHeaderFilter`, 429 `Retry-After` + problem-details.
- Hacer cumplir RBAC en backend con `@EnableMethodSecurity` + `@PreAuthorize`/`AuthzService` (`RN-AU-02/03/04`), deny-by-default, y probarlo con `TestSecuredController` + `MockMvc`.
- Cumplir `springboot-security` compact rules: constructor injection, DTO `@Valid`, `ResponseEntity`, `@ControllerAdvice` RFC7807, `OncePerRequestFilter` para rate limit, headers CSP/`frameOptions`/`referrerPolicy`, secrets via env.
- Entregar tests de integración con Testcontainers MySQL + `spring-security-test` que cubran login ok/401/429, `me`, `logout`, rutas protegidas 401/302, RBAC 403 y colación case-insensitive de `username`.

**Non-Goals:**
- JWT access+refresh con `HttpOnly` cookie y `/api/auth/refresh` con rotación — se documenta como alternativa descartada (ver D-01); no se entrega en este change salvo que el equipo exija JWT.
- ABM usuarios/roles (C-04), Paciente/Profesional/Turno (C-05..C-08), frontend agenda/shell (C-11) ni tablas nuevas — se usa `usuario` existente.
- `spring-session` distribuido/Redis, Remember-Me, OAuth2/LDAP/SSO, 2FA, auditoría append-only — fuera de v1; rate limit permanece in-memory (P0 escala single-instance).
- Cambios en `platform-foundation` o `user-accounts` specs — este change añade `auth` sin redefinirlas.

## Decisions

### D-01 — Sesión server-side vs JWT `HttpOnly Secure SameSite` (elección crítica)
- **Decisión:** **Sesión server-side** con `JSESSIONID` (`HttpOnly`, `Secure` en prod, `SameSite=Lax` vía `server.servlet.session.cookie.*` o `ResponseCookie` si se customiza) + `HttpSession` gestionada por Spring Security. Se conserva `formLogin` para SSR y se expone `POST /api/auth/login` JSON que autentica vía `AuthenticationManager` y crea sesión. `GET /api/auth/me` lee `SecurityContext`. `POST /api/auth/logout` invalida sesión + `SecurityContextLogoutHandler`. Se documenta en `08 §Seguridad` que JWT queda como alternativa SPA si el equipo migra: entonces se añadiría `JwtService` + `OncePerRequestFilter` Bearer + `POST /api/auth/refresh`.
- **Rationale:** `SecurityConfig` actual ya es `formLogin` + sesión implícita; C-01/C-02 no tienen infraestructura JWT (`JwtEncoder`, `TokenStore`). Sesión satisface SSR (Thymeleaf) de `08` y evita complejidad de refresh-token rotation/blacklist en v1; COOKIE attrs cumplen `springboot-security` rule `httpOnly, Secure, SameSite=Strict/Lax`. P0-scale:a es 1 instancia, sin necesidad de stateless.
- **Alternativa descartada:** JWT stateless Bearer + refresh en cookie `HttpOnly Secure SameSite=Lax`. Pros: horizontal scaling. Contras: requiere `JwtService`, blacklist para logout, rotación y `security.oauth2.resourceserver`; aumenta superficie y no está en `pom` hoy. Se deja registrado en `Open Questions` como evolución si `spring-session`/`JWT` se necesita.
- **Trade-off:** Sesión in-memory no escala a N instancias; mitigado con `spring-session-jdbc` o Redis en v2 si hace falta (ver Risks).

### D-02 — Endurecimiento de `SecurityFilterChain`
- **Decisión:** Reemplazar `csrf.disable()`/`cors.disable()`/`anyRequest().authenticated()` laxo por:
  ```java
  http.csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                         .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
      .headers(h -> h.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"))
                     .frameOptions(f -> f.sameOrigin())
                     .referrerPolicy(r -> r.policy(ReferrerPolicy.NO_REFERRER))
                     .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).preload(true)))
      .cors(c -> c.configurationSource(corsSource())) // nunca "*" en prod
      .authorizeHttpRequests(a -> a
         .requestMatchers(HttpMethod.GET, "/", "/login", "/css/**", "/js/**", "/error").permitAll()
         .requestMatchers(HttpMethod.POST, "/login", "/api/auth/login").permitAll()
         .requestMatchers(HttpMethod.GET, "/api/health", "/actuator/health").permitAll()
         .anyRequest().authenticated())
      .formLogin(f -> f.loginPage("/login").loginProcessingUrl("/login")
                       .successHandler(roleRedirectHandler).failureHandler(jsonOrRedirectFailure))
      .logout(l -> l.logoutUrl("/api/auth/logout").logoutSuccessHandler((req,res,auth)->res.setStatus(204))
                   .deleteCookies("JSESSIONID","XSRF-TOKEN").invalidateHttpSession(true))
      .sessionManagement(s -> s.sessionCreationPolicy(IF_REQUIRED).maximumSessions(1).expiredUrl("/login?expired"))
      .exceptionHandling(e -> e.authenticationEntryPoint(jsonOrRedirectEntryPoint)
                               .accessDeniedHandler(jsonOrRedirectAccessDenied))
      .addFilterBefore(loginRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
      .addFilterAfter(new ForwardedHeaderFilter(), SecurityContextHolderFilter.class); // o bean
  ```
  CSRF permanece activo para SSR (`CookieCsrfTokenRepository` → cookie `XSRF-TOKEN` no-HttpOnly para que JS lea header `X-XSRF-TOKEN`; form Thymeleaf incluye `th:csrf`). Para `POST /api/auth/login` JSON se exige header `X-XSRF-TOKEN` si `Content-Type: application/json` y sesión existe; login inicial (sin sesión) está `permitAll` y no necesita token. `application.properties` ya tiene `server.forward-headers-strategy=native`; se añade bean `ForwardedHeaderFilter` si se quiere clave IP detrás de proxy (`X-Forwarded-For`).
- **Rationale:** `springboot-security` rule: CSRF enabled for session/SSR, disabled+STATELESS only for pure Bearer APIs. `headers` CSP/frameOptions/referrer per spec. CORS en `SecurityFilterChain` nivel, nunca `*`.
- **Alternativa:** Mantener `csrf.disable()` y confiar en SameSite — rechazada (no cumple hardness CRITICO).

### D-03 — `CustomUserDetailsService` + `CustomUserDetails`
- **Decisión:** `CustomUserDetailsService implements UserDetailsService` (constructor injection `UsuarioRepository`) con `loadUserByUsername(String username)` case-insensitive (`lower(username)` o colación DB `utf8mb4_0900_ai_ci`): `findByUsernameIgnoreCase` o `findByUsername(username.toLowerCase())` según repo; mapea `Usuario` → `CustomUserDetails implements UserDetails` con `username`, `password`=`passwordHash`, `authorities=[ROLE_<rol>]`, `enabled=activo`, `accountNonLocked=true`. Si `!activo` lanza `DisabledException` (mapeada a 401 genérico). Password nunca logueado. `AuthenticationManager` expone `DaoAuthenticationProvider` con `PasswordEncoder` BCrypt(12).
- **Rationale:** C-02 ya garantiza `BCryptPasswordEncoder(12)` bean; `UserDetailsService` es el único punto donde `activo=false` se traduce a `DisabledException` → 401 (spec req). `springboot-patterns` constructor injection.
- **Alternativas:** `JdbcUserDetailsManager` — descartado (duplica `Usuario`).

### D-04 — `AuthController` / `LoginController` y DTOs
- **Decisión:**
  ```
  record LoginRequest(@NotBlank @Size(max=50) String username, @NotBlank @Size(max=100) String password) {}
  record LoginResponse(Long id, String username, String email, Rol rol) { static LoginResponse from(Usuario u) }
  record MeResponse(Long id, String username, String email, Rol rol) { static MeResponse from(Usuario u) }
  ```
  `AuthController(@RestController @RequestMapping("/api/auth"))` con `POST /login` (`@Valid @RequestBody LoginRequest`, `HttpServletRequest`, `HttpServletResponse`): autentica vía `AuthenticationManager.authenticate(new UsernamePasswordAuthenticationToken(...))`, en éxito `SecurityContextHolder.getContext().setAuthentication(auth)`, `request.getSession(true)`, set cookie `JSESSIONID` automáticamente, return `200 LoginResponse.from(usuario)`. En fallo: `BadCredentialsException` o `DisabledException` → `throw` → `@ControllerAdvice` → 401 genérico. `GET /me` → si `authentication instanceof CustomUserDetails` return `MeResponse`, sino 401. `POST /logout` → `SecurityContextLogoutHandler`. `LoginController(@Controller)` sirve `GET /login` → `login.html` (Thymeleaf) con `th:action="@{/login}"`, `th:if="${param.error}"` genérico, `th:csrf`.
- **Rationale:** Hard rule DTOs con `@Valid` y `from()` mapper; `ResponseEntity` con status correcto; nunca exponer `passwordHash`. Dual `POST /api/auth/login` JSON + `POST /login` form cubre SSR y API sin duplicar lógica (ambos delegan a `AuthenticationManager`).
- **Trade-off:** Dos endpoints login — documentación clara evita confusión; tests cubren ambos.

### D-05 — `RoleRedirectAuthenticationSuccessHandler`
- **Decisión:** `Component RoleRedirectSuccessHandler implements AuthenticationSuccessHandler` que lee `authentication.getAuthorities()` → `Rol`: `PACIENTE → /mis-turnos`, `ODONTOLOGO → /agenda`, `SECRETARIA/ADMIN → /agenda` (US-001 CA-2). Para `Accept: application/json` retorna `200 LoginResponse` JSON; para `Accept: text/html` hace `sendRedirect`. `AuthenticationFailureHandler` similar: JSON → 401 `{"error":"Credenciales inválidas"}`; form → redirect `/login?error`.
- **Rationale:** `CHANGES.md` exige redirección por rol; failure genérico evita enumeración de usuarios.

### D-06 — Rate limiting 5/60s por `IP + username`
- **Decisión:** `LoginRateLimitFilter extends OncePerRequestFilter` (ordenado antes de `UsernamePasswordAuthenticationFilter`) + `RateLimitService` que mantiene `ConcurrentHashMap<String, Bucket>` (Bucket4j) o Caffeine `Cache` con expiración 60s. Clave = `normalizedUsername.toLowerCase() + "|" + clientIp` donde `clientIp = X-Forwarded-For (first) ?? request.getRemoteAddr()` (con `ForwardedHeaderFilter`). Solo cuenta intentos `POST /api/auth/login` y `POST /login` con 401 previos (fallo); éxitos no incrementan pero sí limpian bucket. Al exceder, responde `429 Too Many Requests` con `Retry-After: 60` y body problem-details `{"type":"about:blank","title":"Too Many Requests","status":429,"detail":"Demasiados intentos. Reintentá en 60s."}` y header `X-RateLimit-Remaining: 0`. Implementación Bucket4j: `Bucket.builder().addLimit(Bandwidth.classic(5, Refill.greedy(5, Duration.ofSeconds(60)))).build()` + `tryConsume(1)` por request. In-memory por instancia es suficiente (P0 single-instance); para cluster se migraría a Redis (`Bucket4j + redis` o `Bucket4j + JCache`).
- **Rationale:** `springboot-security` compact rule rate limit con Bucket4j 429 + retry hint; key por IP+username evita que atacante rote usernames o IPs. Ventana deslizante 60s cumple spec.
- **Alternativas:** `Bucket4j` distribuido Redis desde v1 — overkill; `Guava RateLimiter` — no soporta clave compuesta fácilmente.

### D-07 — RBAC `@PreAuthorize` + `AuthzService` (PermissionContext)
- **Decisión:** `@EnableMethodSecurity` ya activo. Crear `AuthzService` (`@Service`) con métodos `boolean isAdmin()`, `boolean isPacienteOwner(Long pacienteId)` (compara `CustomUserDetails.id` vs `paciente.usuario_id`), `boolean isOdontologoOwner(Long profesionalId)` (compara vs `profesional.usuario_id`). Uso: `@PreAuthorize("hasRole('ADMIN')")` en endpoints admin; `@PreAuthorize("@authz.isPacienteOwner(#id) or hasAnyRole('SECRETARIA','ADMIN')")` en paciente/turno futuros; C-03 prueba con `TestSecuredController` (`@GetMapping("/api/test/admin-only") @PreAuthorize("hasRole('ADMIN')")` etc.) para validar 403 sin esperar C-04. Seguridad deny-by-default: `anyRequest().authenticated()` + method security.
- **Rationale:** RN-AU-02 (validar en backend, no solo UI), RN-AU-03/04 (aislamiento). `springboot-security` rule `@PreAuthorize("hasRole('ADMIN')")` o `@authz.isOwner(#id)`.
- **Trade-off:** `AuthzService` requiere join a `Paciente`/`Profesional` en C-05/C-07; en C-03 se implementa esqueleto con `Usuario` y se extiende luego sin cambiar API.

### D-08 — Manejo de errores RFC7807 y `ProblemDetail`
- **Decisión:** `GlobalExceptionHandler extends ResponseEntityExceptionHandler` + `@ControllerAdvice` con handlers: `BadCredentialsException`/`DisabledException` → `401` `ProblemDetail` con `title="Unauthorized"` `detail="Credenciales inválidas"` (mismo mensaje para no enumerar si usuario existe o inactivo); `AccessDeniedException` → `403`; `RateLimitExceededException` → `429` con `Retry-After`; `MethodArgumentNotValidException` → `400` con `fieldErrors`; `AuthenticationException` en `AuthenticationEntryPoint` → 401 JSON si `Accept: application/json` o 302 a `/login` si `text/html`. `spring.mvc.problemdetails.enabled=true` ya en `application.properties` produce `application/problem+json`.
- **Rationale:** `springboot-patterns` central `@ControllerAdvice` `ApiError` RFC7807; evita filtrar info sensible.

### D-09 — Propiedades y cookies
- **Decisión:** Añadir a `application.properties`:
  ```
  server.servlet.session.cookie.http-only=true
  server.servlet.session.cookie.secure=${APP_COOKIE_SECURE:true} # true en prod, false en dev http
  server.servlet.session.cookie.same-site=lax
  server.servlet.session.timeout=30m
  app.login-rate-limit.max-attempts=5
  app.login-rate-limit.window-seconds=60
  ```
  `APP_SECRET` ya existe; no se hardcodea. `TimeZoneConfig` sin cambio. Documentar en `08` elección sesión y atributos cookie.
- **Rationale:** `springboot-security` cookies `httpOnly, Secure, SameSite=Strict/Lax`; `APP_SECRET` via env. `APP_TURNO_*` sin uso en este change.

### D-10 — Estrategia de testing (DB real)
- **Decisión:** Tests `src/test/java/com/clinica/turnos/web/AuthControllerTest.java` y `SecurityIntegrationTest.java` con `@SpringBootTest` + `@AutoConfigureMockMvc` + `@Testcontainers @Container MySQLContainer("mysql:8.0")` + `@DynamicPropertySource` (datasource url). Usan `MockMvc` + `spring-security-test` (`formLogin`, `user()`). Tests: `login_ok_returns200AndMeOk`, `login_wrongPassword_401Generic`, `login_inactiveUser_401Generic`, `me_withoutSession_401JsonAnd302Form`, `adminOnlyEndpoint_asPaciente_403`, `rateLimit_after5Fails_429`, `logout_invalidatesSession_me401`, `login_caseInsensitiveUsername_ok`, `csrf_postWithoutToken_403ForSessionEndpointsButPermitAllLogin` según config. Cubren RN-AU-01, RN-TU bypass. No mocks de DB (hard rule).
- **Rationale:** Hard rule `NUNCA testear DB con mocks → usar Testcontainers MySQL`; unit puro solo para `AuthzService` lógica sin IO.

## Risks / Trade-offs

- **CRITICO — Enumeración de usuarios** → Mensaje distinto para "usuario no existe" vs "password mal" vs "inactivo" revela existencia. Mitigado: siempre `401 "Credenciales inválidas"` genérico, mismo delay (~50ms jitter) para no filtrar por timing; logs server sí distinguen con `log.debug` sin exponer a cliente.
- **Brute force distribuido** → In-memory bucket por instancia no frena ataque distribuido desde botnet con IPs rotadas. Mitigado: clave `IP+username` + `username`-only bucket secundario global (5/60s por username global) como defensa adicional; en prod detrás de WAF/Cloudflare; evolución a Redis Bucket si escala.
- **Sesión en memoria no escala** → Single-instance ok (P2:a/P0), pero 2 instancias perderían sesión. Mitigado: `spring-session-jdbc` con tabla `SPRING_SESSION` o Redis; decisión diferida a C-12 hardening; `application.properties` ya prevé `spring.session.store-type`.
- **CSRF complexity** → `CookieCsrfTokenRepository` exige header `X-XSRF-TOKEN` en `POST /api/*` con sesión; clientes JSON deben leer cookie. Mitigado: documentar en `README` y proveer `CsrfToken` en `GET /api/auth/me` o `GET /login`; login inicial `permitAll` no necesita CSRF.
- **Forwarded IP spoofing** → Atacante puede falsificar `X-Forwarded-For` si no hay proxy confiable. Mitigado: `server.forward-headers-strategy=native` solo si `ForwardedHeaderFilter` detrás de `TrustedProxy` (ngnix/ALB); en dev `getRemoteAddr()` es verdad; se loggea `X-Forwarded-For` para auditoría.
- **BCrypt(12) perf en tests** → 250ms por encode puede ralentizar suite. Mitigado: reutilizar hash precomputado `$2a$12$...` para `admin` y `TestUsers`; solo `DevDataInitializer` hace `encode` una vez al arranque.
- **Cookie Secure en dev http** → `Secure=true` rompe login en `http://localhost`. Mitigado: `${APP_COOKIE_SECURE:false}` en `dev` profile, `true` en `prod`; documentado en `.env.example`.
- **RBAC deny-by-default rigidez** → `anyRequest().authenticated()` puede bloquear `css/js` si no están en `permitAll`. Mitigado: lista explícita `/css/**`, `/js/**`, `/images/**`, `/error` en `permitAll` y test de 200 para esos paths sin sesión.

## Migration Plan

1. **Pre-condición:** C-02 archivado, `V2__usuario` aplicada, `DevDataInitializer` con `admin/admin123` BCrypt(12) en `dev`, `mvn verify -DskipTests` verde, `docker compose up -d mysql` healthy.
2. **Rama:** `c-03-auth-login-rbac` desde `main`; `openspec new change c-03-auth-login-rbac` ya ejecutado.
3. **Implementar en orden:** `CustomUserDetails`/`CustomUserDetailsService` → `RateLimitService`/`LoginRateLimitFilter` + dependencia Bucket4j → `AuthzService` → `RoleRedirectSuccessHandler` + `SecurityJsonHandlers` (EntryPoint/AccessDenied/Failure) → `LoginRequest/Response/MeResponse` DTOs → `AuthController` + `LoginController` + `login.html` (si no existe) → endurecer `SecurityConfig` (CSRF, headers, cors, sessionManagement, authorizeHttpRequests, filtros) → `GlobalExceptionHandler` 429/401/403 → `application.properties` cookies + rate-limit props → `TestSecuredController` (solo test profile) si se usa para probar RBAC.
4. **Verificación local:** `mvn test -Dtest=AuthControllerTest,SecurityIntegrationTest` verde con Testcontainers; `curl -v -c cookies.txt -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}' http://localhost:8080/api/auth/login` → 200 + `Set-Cookie: JSESSIONID=...; HttpOnly; SameSite=Lax` + body `LoginResponse`; `curl -b cookies.txt http://localhost:8080/api/auth/me` → 200 `MeResponse`; `curl -X POST -b cookies.txt http://localhost:8080/api/auth/logout` → 204; posterior `me` → 401; `for i in 1..6; do curl -s -o /dev/null -w "%{http_code}\n" -H "Content-Type: application/json" -d '{"username":"admin","password":"wrong"}' http://localhost:8080/api/auth/login; done` → 5×401, 6×429 con `Retry-After`; `curl -H "Accept: text/html" http://localhost:8080/api/auth/me` sin cookie → 302 `/login`; `curl -H "Accept: application/json" http://localhost:8080/api/auth/me` sin cookie → 401 JSON.
5. **CI:** `backend-test` con `mysql:8.0` service y Testcontainers debe pasar; `lint` sin cambios.
6. **Rollback:** Forward-only, sin migración DB. Si `SecurityConfig` bloquea legítimos, revertir a `permitAll` previo y desplegar `V{next}` de fix; nunca editar `.openspec.yaml` tras `archive`. Sesión in-memory no requiere reparación de datos.
7. **Deploy:** `docker compose up --build` + `SPRING_PROFILES_ACTIVE=dev` → `admin` disponible; `APP_COOKIE_SECURE=false` en dev; `APP_SECRET` via `.env`.
8. **Archivado:** `openspec archive c-03-auth-login-rbac` copia `specs/auth/spec.md` a `openspec/specs/auth/spec.md` y marca C-03 `[x]` en `CHANGES.md`; actualizar `08 §Seguridad` con elección sesión y atributos cookie.

## Open Questions

- ¿Sesión server-side es definitiva o se migrará a JWT `HttpOnly` + refresh en SPA? C-03 elige sesión; si PO/clínica exige stateless o `spring-session` distribuido, el change se adapta a D-01 alternativa JWT (añadir `JwtService`, `POST /api/auth/refresh`, rotación) sin cambiar specs (solo design `08`). Confirmar antes de merge y congelar en `08`.
- ¿`APP_COOKIE_SECURE` debe forzarse a `true` en prod via `Authz` o `WebConfig`? Se propone `server.servlet.session.cookie.secure=true` en `prod` profile y `false` en `dev`; verificar con infra (`TLS termination`).
- ¿Rate limit global por `username` además de `IP+username`? Se propone añadir bucket secundario 10/60s por username global para mitigar botnet; si PO ve falso-positivo para clínica detrás de NAT, se ajusta a 20/60s.
- ¿CSRF token debe exponerse en `GET /api/auth/csrf` para SPA JSON? Si frontend es SPA pura, se añade endpoint; si SSR Thymeleaf basta, no. Decisión se toma en C-11 y se documenta.
