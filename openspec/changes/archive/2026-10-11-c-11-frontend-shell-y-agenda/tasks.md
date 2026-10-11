# Tasks — C-11-frontend-shell-y-agenda

## 1. Base layout + shell por rol

- [x] 1.1 Crear `layout.html` + fragmentos (`header`, `sidebar`, `EstadoBadge`, `TurnoCard`, `HuecosLibres`, `PacienteAutocomplete`, `ProfesionalSelect`) extendiendo `home.html`, con Bootstrap local en `static/` (CSP-safe) y `thymeleaf-extras-springsecurity` verificado en `pom.xml`; verificar con `mvn -q compile` y `GET /` renderiza layout.
- [x] 1.2 Implementar `ShellController` (`GET /`: anónimo→`/login`, autenticado→dashboard por rol) + menú por rol en sidebar; verificar con MockMvc: anónimo `GET /` → redirect `/login`, cada rol ve su menú y no ve links ajenos.
- [x] 1.3 Mejorar `login.html` (estilos, `?expired`, labels+a11y, error genérico) sin cambiar `loginProcessingUrl`; verificar con MockMvc: `GET /login` 200 con CSRF hidden, POST inválido muestra "Credenciales inválidas", `GET /login?expired` muestra "Sesión expirada".

## 2. Agenda día/semana

- [x] 2.1 Implementar `AgendaMvcController` (`GET /agenda`: día default hoy TZ `America/Argentina/Buenos_Aires`, semana 7 días, filtro profesional+fecha, orden `hora_inicio`, huecos libres vía servicios C-09); verificar con MockMvc: agenda día ordenada con paciente/especialidad/badge + huecos, semana cubre 7 días, día vacío muestra "Sin turnos" + libres.
- [x] 2.2 Tests de scoping de agenda (paciente solo suyos, odontólogo solo propia, secretaria todos, paciente-ajeno 403); verificar con MockMvc login por rol (`@WithMockUser` o sesión real) sobre `GET /agenda` y `GET /turnos/{id}` ajeno → 403/"No autorizado".

## 3. Turnos nuevo + detalle

- [x] 3.1 Implementar `TurnoMvcController` `GET|POST /turnos/nuevo` (autocomplete DNI/apellido con degradación sin JS, profesional, especialidad filtrada server-side RN-PR-05, fecha, slots libres, validación inline + repoblado tras error); verificar con MockMvc: form 200 con slots, POST válido → 302 detalle, POST conflicto → re-render "Horario ocupado" + sugeridos conservando datos, POST inválido → error junto al campo (nunca 500).
- [x] 3.2 Implementar `GET /turnos/{id}` + POSTs Cancelar/Reprogramar/Atendido delegando en `TurnoService` (mismos guards C-10: botones solo por rol+estado RESERVADO); verificar con MockMvc: Flujo 2 (cancelar RESERVADO→CANCELADO libera hueco), reprogramar ok (Flujo 5), ATENDIDO solo RESERVADO→ATENDIDO, transiciones inválidas re-renderizan 400, rol sin permiso → 403.

## 4. Pacientes buscador + ficha

- [x] 4.1 Implementar `PacienteMvcController` `GET /pacientes` (buscador DNI/apellido + alta inline Flujo 4 → redirect `/turnos/nuevo?paciente={id}`; DNI duplicado muestra error + link a ficha); verificar con MockMvc: búsqueda por apellido, alta inline encadena a nuevo turno, DNI duplicado 409-UX con link.
- [x] 4.2 Implementar `GET /pacientes/{id}` (ficha + historial turnos; PACIENTE solo propia); verificar con MockMvc: secretaria ve ficha+historial, paciente ve la suya y ajena → 403/"No autorizado".

## 5. Integración + responsive

- [x] 5.1 Verificación E2E HTML de punta a punta (Flujo 4 alta+reserva, Flujo 1 reserva, Flujo 2 cancelar, Flujo 5 reprogramar, Flujo 3 agenda día, login por rol + 401→`/login?expired`); verificar con suite MockMvc verde (`mvn test -Dtest=*Mvc*,*Html*`) o Playwright headless si hay navegador (documentar cuál se usó).
- [x] 5.2 Revisión responsive mobile-first 360px + a11y (labels, errores anunciados, sin scroll horizontal en `/agenda`) y manejo global (403→"No autorizado", 400 por campo, CSP `default-src 'self'` intacta); verificar con inspección + asserts de HTML/clases Bootstrap y header CSP en respuesta.
