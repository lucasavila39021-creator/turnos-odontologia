# Arquitectura Propuesta — Turnos Odontología

> **Decisión congelada C-01 (2026-10-10): Variante A — Java Spring Boot 17 / 3.x + Maven es la variante activa.** Variante B (Python Django) queda documentada solo como alternativa descartada (DD-01/DD-02). No mantener dos en paralelo.

## Patrones aplicados

| Patrón | Dónde se usa | Por qué |
|--------|--------------|---------|
| **Capas (Controller → Service → Repository)** | Todo el backend | Mantenibilidad P5:b, separación clara; service concentra RN-* |
| **Monolito modular** | Deploy único | Suficiente para escala small, menos ops que microservicios |
| **RBAC por rol** | Spring Security / Django Auth | Requisito P3 (4 roles) simple sin ABAC en v1 |
| **Validación en servicio + Bean Validation** | Turno, Paciente, Profesional | RN-TU-01/05 y RN-PA/PR no dependen solo de frontend |
| **Transacción ACID** | Creación de turno | Garantiza no-superposición sin locks distribuidos |
| **DTO / ViewModel** | API o Templates | No exponer entidades JPA directamente |
| **Soft-state (estado)** | Turno.RESERVADO/CANCELADO/ATENDIDO | Trazabilidad sin borrado físico |

## Estructura de directorios

### Variante A — Java Spring Boot

```
turnos-odontologia/
├── src/main/java/com/clinica/turnos/
│   ├── TurnosApplication.java
│   ├── config/          # SecurityConfig, WebConfig, TimeZoneConfig
│   ├── domain/
│   │   ├── model/       # Entidades JPA: Usuario, Paciente, Profesional, Especialidad, Turno
│   │   └── repository/  # JpaRepository interfaces
│   ├── application/
│   │   ├── service/     # PacienteService, ProfesionalService, TurnoService (RN-* acá)
│   │   └── dto/         # TurnoCreateRequest, TurnoResponse, etc.
│   ├── web/
│   │   ├── controller/  # AuthController, PacienteController, TurnoController
│   │   └── validator/   # Validadores custom (no-superposición)
│   └── security/        # UserDetailsService, RBAC
├── src/main/resources/
│   ├── application.properties
│   ├── db/migration/    # Flyway V1__init.sql
│   └── templates/       # Thymeleaf (si SSR) o static/ para SPA
├── src/test/java/...    # Unit + Integration (Testcontainers MySQL)
├── pom.xml
└── docker-compose.yml   # app + mysql (opcional dev)
```

### Variante B — Python Django

```
turnos-odontologia/
├── manage.py
├── turnos/              # project settings
│   ├── settings.py
│   ├── urls.py
│   └── wsgi.py
├── apps/
│   ├── usuarios/        # Usuario, RBAC, login
│   ├── pacientes/       # Paciente model/service/views
│   ├── catalogo/        # Profesional, Especialidad
│   └── agenda/          # Turno model/service/views (RN-TU acá)
├── templates/           # Django Templates + Bootstrap
├── static/
├── requirements.txt
└── docker-compose.yml   # web + mysql
```

## Seguridad — C-03 auth-login-rbac (sesión server-side)

- **Autenticación — sesión server-side (D-01):** `POST /api/auth/login` JSON y `POST /login` form validan `PasswordEncoder.matches` BCrypt(12) case-insensitive (`findByUsernameIgnoreCase`), 401 genérico `{"error":"Credenciales inválidas"}` para credenciales inválidas o `activo=false` (no enumeración). En éxito crea `HttpSession` + `JSESSIONID` con `HttpOnly=true`, `Secure=${APP_COOKIE_SECURE:false}`, `SameSite=Lax`, `Path=/`, `timeout=30m`, `maximumSessions=1`. `GET /api/auth/me` → `MeResponse`; `POST /api/auth/logout` → 204 invalida sesión y borra `JSESSIONID`/`XSRF-TOKEN`. Decisión sesión server-side congela SSR Thymeleaf; JWT queda como alternativa documentada futura.
- **Autorización RBAC (D-07):** `@EnableMethodSecurity` + `@PreAuthorize("hasRole('ADMIN')")` o `@authz.isAdmin()` / `@authz.isOwner(#id)` via `AuthzService` (`@Component("authz")`). `anyRequest().authenticated()` deny-by-default, `permitAll` solo `GET /`, `/login`, `/css/**`, `/js/**`, `/error`, `GET /api/health`, `GET /actuator/health`, `POST /login`, `POST /api/auth/login`. Tests `TestSecuredController` verifican 403 bypass.
- **Validación de input (D-04/D-08):** `LoginRequest(@NotBlank @Size)` con `400 fieldErrors` RFC7807, `LoginResponse`/`MeResponse.from()` nunca exponen `passwordHash`, `@ControllerAdvice` mapea `BadCredentials/Disabled →401`, `AccessDenied →403`, `RateLimitExceeded →429`.
- **CSRF + headers (D-02):** `CookieCsrfTokenRepository.withHttpOnlyFalse()` + `CsrfTokenRequestAttributeHandler` (SpaCsrfTokenRequestHandler) — cookie `XSRF-TOKEN` no-HttpOnly, header `X-XSRF-TOKEN` requerido para POST con sesión; `ignoringRequestMatchers` para `/api/auth/login` y `/login` iniciales sin sesión; `headers` con `Content-Security-Policy default-src 'self'`, `frameOptions sameOrigin`, `referrerPolicy NO_REFERRER`, `HSTS includeSubDomains preload`. `CorsConfigurationSource` `allowedOrigins=[http://localhost:3000,http://localhost:8080]` nunca `*`, `allowCredentials=true` en `SecurityFilterChain`.
- **Rate limiting (D-06):** `LoginRateLimitFilter` (`OncePerRequestFilter` antes de `UsernamePasswordAuthenticationFilter`) + `RateLimitService` Bucket4j `5/60s` por clave `IP+username` normalizado lower, `X-Forwarded-For` first + `remoteAddr` fallback con `ForwardedHeaderFilter` (`server.forward-headers-strategy=native`). Solo fallos 401/302-error consumen bucket; al exceder `429 Too Many Requests` con `Retry-After` y body problem-details, header `X-RateLimit-Remaining:0`. In-memory por instancia (P0 single-instance), evolución a Redis futura.
- **Redirección por rol (D-05):** `RoleRedirectAuthenticationSuccessHandler` — JSON `200 LoginResponse` sin redirect; form `PACIENTE→/mis-turnos`, `ODONTOLOGO/SECRETARIA/ADMIN→/agenda`; `AuthenticationFailureHandler` JSON 401 genérico vs `302 /login?error`.
- **Passwords & secrets:** `BCryptPasswordEncoder(12)` strength 12 verificado vía `grep BCryptPasswordEncoder`, nunca `log.info` con password/token, secrets via env `DB_PASSWORD`, `APP_SECRET`, `APP_COOKIE_SECURE`.
- **Zona horaria:** `America/Argentina/Buenos_Aires` fija en `application.properties` / `settings.TIME_ZONE`.

## Variables de entorno

| Variable | Descripción | Ejemplo | Sensible |
|----------|-------------|---------|----------|
| `DB_HOST` | Host MySQL | `localhost` | No |
| `DB_PORT` | Puerto MySQL | `3306` | No |
| `DB_NAME` | Nombre BD | `turnos_db` | No |
| `DB_USER` | Usuario BD | `turnos_user` | No |
| `DB_PASSWORD` | Password BD | `s3cr3t` | Sí |
| `APP_SECRET` | Clave de sesión/JWT | `random-32-chars` | Sí |
| `APP_TIMEZONE` | Zona horaria | `America/Argentina/Buenos_Aires` | No |
| `APP_TURNO_DURACION_MIN` | Duración fija por turno | `30` | No |
| `APP_HORARIO_DESDE` | Apertura clínica | `08:00` | No |
| `APP_HORARIO_HASTA` | Cierre clínica | `18:00` | No |
| `PORT` | Puerto app | `8080` / `8000` | No |

## Decisiones de infra (P0-scale:a)

- Sin caché distribuido, sin colas, sin balanceador en v1 — 1 instancia + 1 MySQL alcanza para <50 usuarios y 40 turnos/día.
- Backups: `mysqldump` diario (o volumen Docker con snapshot) — documentar en `README` de deploy.
- Migrations: Flyway (Java) o Django Migrations — versionadas, no `ddl-auto: update` en prod.
