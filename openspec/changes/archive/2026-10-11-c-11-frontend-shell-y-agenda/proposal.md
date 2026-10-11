# Proposal — C-11-frontend-shell-y-agenda

## Why

Las APIs de turnos, pacientes, profesionales, usuarios y auth (C-03→C-10) están completas y archivadas, pero el sistema aún no es operable por sus usuarios reales: secretaria, odontólogo y paciente no tienen UI. Sin shell SSR + agenda visible no hay MVP usable ni se pueden validar los Flujos 1–6 end-to-end en navegador.

## What Changes

- Shell SSR Thymeleaf que extiende `login.html`/`home.html` existentes: layout con sidebar/header, `GET /` con redirect (no autenticado → `/login`; autenticado → dashboard por rol), menú por rol.
- Páginas SSR (sin SPA/React): `/login` mejorado (form + error genérico + `expired`), `/agenda` (vista día/semana, filtro profesional+fecha, lista cronológica, huecos libres), `/turnos/nuevo` (autocomplete paciente por DNI/apellido, profesional, especialidad filtrada por profesional, fecha, slots libres, validación inline), `/turnos/{id}` (detalle + botones Cancelar/Reprogramar/Atendido según rol y estado), `/pacientes` (buscador + alta inline Flujo 4), `/pacientes/{id}` (ficha + historial).
- Fragmentos Thymeleaf reutilizables: `AgendaDia`/`AgendaSemana`, `TurnoCard` (colores por estado), `HuecosLibres`, `PacienteAutocomplete`, `ProfesionalSelect`, `EstadoBadge`.
- Manejo UX de errores HTTP sin duplicar validación backend: 401→redirect `/login`, 403→"No autorizado", 409→"Horario ocupado" + sugeridos, 400→mensajes por campo.
- Responsive Bootstrap mobile-first (CDN o `static/` verificado en diseño) + RBAC solo oculta en UI (backend sigue mandando).
- Tests E2E/HTML: MockMvc + Thymeleaf como base (Playwright solo si hay navegador disponible).

## Capabilities

### New Capabilities

- `frontend-shell`: shell SSR + navegación por rol + páginas agenda/turnos/pacientes + fragmentos + manejo UX de errores HTTP sobre APIs existentes.

### Modified Capabilities

- Ninguna. Este change es presentacional: no cambia REQUIREMENTS de `turnos`, `pacientes`, `auth` ni otras capabilities (solo UX + manejo de errores sobre contratos existentes).

## Impact

- Código: nuevos `MvcController` (thin, model attributes, redirects por rol) + `templates/` + fragmentos; reutiliza `SecurityConfig` (CSRF cookie `XSRF-TOKEN`), `LoginController` (`/login`, `/`), controllers REST existentes. Sin cambios en `/api/*`.
- Sin migración de BD. Sin nuevos secrets/env vars (reutiliza `APP_*` existentes).
- Riesgo principal: divergencia UI vs RBAC backend — se mitiga ocultando solo en UI y testeando 403/paciente-ajeno a nivel MockMvc.
