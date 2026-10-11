# Design — C-11-frontend-shell-y-agenda

## Context

Ver `proposal.md` (Why). Estado actual: variante SSR Thymeleaf congelada (C-01); existen `templates/login.html`, `templates/home.html`, `LoginController` (`GET /login`, `GET /`), `SecurityConfig` con sesión server-side + CSRF cookie `XSRF-TOKEN` (`CookieCsrfTokenRepository.withHttpOnlyFalse()`, `ignoringRequestMatchers` solo para `/login` inicial), y APIs REST `/api/turnos|pacientes|profesionales|especialidades|auth|usuarios` con RBAC backend + scoping por rol en servicios (C-08/09/10). No existe `static/` (verificado: ausente) ni fragmentos Thymeleaf. Los MVC controllers nuevos deben ser thin y delegar en los mismos `ApplicationService` que usan los REST controllers.

## Goals / Non-Goals

- Goals: shell + 6 páginas SSR que cubren Flujos 1–6 visibles; fragmentos reutilizables; manejo UX de 401/403/409/400; menú por rol; responsive mobile-first; verificación MockMvc HTML.
- Non-Goals: NO SPA/React/Zustand (variante congelada); NO nuevas APIs ni cambios de contrato REST; NO migración BD; NO duplicar validaciones de dominio en frontend (solo UX/inline); NO recordar/HC/pagos (fuera de v1).

## Decisions

### D-01 — SSR Thymeleaf + MvcControllers thin (no SPA)

Nuevos `@Controller` (p. ej. `AgendaMvcController`, `TurnoMvcController`, `PacienteMvcController`, `ShellController` para `GET /`) que llaman a los mismos services (`TurnoService`, `PacienteService`, `ProfesionalService`) y devuelven vistas con model attributes; forms POST → validación → redirect/ re-render con errores. Alternativa SPA/React descartada: C-01 congeló SSR + sesión server-side; SPA exigiría JWT/CORS y duplicaría RBAC.

### D-02 — Extender `login.html`/`home.html` + layout de fragmentos

`login.html` se mejora (estilos, `expired`, a11y) sin cambiar `loginProcessingUrl`; `home.html` pasa a ser layout base (`layout.html` + `fragments/header|sidebar|badges|forms`) que las páginas extienden. Alternativa reescribir desde cero descartada: preserva comportamiento C-03 verificado (rate-limit, redirect por rol).

### D-03 — Menú por rol solo oculta; backend manda

`sec:authorize` (thymeleaf-extras-springsecurity) o `model.rol` para ocultar links/botones; cada POST/GET MVC reusa `@PreAuthorize` + scoping de servicio, así URL directa forzada → 403/redirect. Alternativa filtro solo-UI descartada: violaría RN-AU-02.

### D-04 — CSRF nativo Thymeleaf + Bootstrap local

Forms con `${_csrf.parameterName/token}` (patrón ya usado en `login.html`); `SecurityConfig` sin cambios (permitir `GET /agenda|/turnos/**|/pacientes/**` autenticados, `static/**` público). Bootstrap vía WebJars o `static/css` local (crear `static/` ausente) en vez de CDN externo: respeta `Content-Security-Policy default-src 'self'`. Alternativa CDN descartada: rompería CSP y tests offline.

### D-05 — Slots/huecos y autocomplete vía servicios existentes

`/agenda` y `/turnos/nuevo` consumen `TurnoService.listar` + cálculo de huecos (lógica C-09) en el MVC controller; autocomplete paciente = `GET /api/pacientes?dni|apellido` con fetch + degradación a `<select>`/input sin JS. Especialidad filtrada por profesional se resuelve server-side al seleccionar profesional (re-render) + mejora JS opcional. Alternativa endpoints nuevos descartada: duplica contratos.

### D-06 — Tests MockMvc HTML primero, Playwright condicional

`@SpringBootTest + MockMvc + Thymeleaf` con H2/perfil test: asserts de HTML (menú por rol, orden cronológico, badges, errores 409/400 re-render, 403 paciente-ajeno, redirect `/login?expired`). Playwright solo si hay navegador en el entorno de apply; si no, harness `webapp-testing` + MockMvc basta. Alternativa solo-Playwright descartada: frágil sin navegador y viola hard rule (tests DB real/H2, no mocks de servicio).

## Risks / Trade-offs

- [Divergencia UI vs RBAC] → Mitigación: botones condicionados + tests de URL directa por rol (403) en cada página.
- [CSP bloquea inline-JS/CSS] → Mitigación: JS/CSS en archivos `static/`, sin `onclick` inline; verificar header en tests.
- [Re-render pierde datos del form tras 409/400] → Mitigación: repoblar model con DTO + `BindingResult`/`fieldErrors`; test que postea conflicto y aserta valores conservados.
- [Alcance: 6 páginas en un change] → Mitigación: fragmentos compartidos primero (tasks ordenan layout→agenda→turnos→pacientes); C-12 hace polish.

## Migration Plan

Sin migración. Deploy: solo archivos nuevos/modificados (`templates/`, `static/`, MVC controllers); rollback = revert del commit. `GET /` cambia comportamiento solo para autenticados (redirect a dashboard por rol); anónimos siguen a `/login`.

## Open Questions

- Ninguna que bloquee specs o tasks. En apply verificar: versión exacta de Bootstrap disponible (WebJars vs vendorizado) y presencia de `thymeleaf-extras-springsecurity` en `pom.xml` (si falta, agregarlo como única dependencia nueva).
