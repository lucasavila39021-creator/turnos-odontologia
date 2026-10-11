# Tasks — C-09 turnos-consulta-agenda

## 1. Repository queries paginadas (solo lectura)

- [x] 1.1 Agregar queries derivadas paginadas a `TurnoRepository` (por profesional+fecha, paciente+fecha, fecha, rangos `Between`, con/sin `estado`, `OrderByFechaAscHoraInicioAsc` con `Pageable`) y verificar con test H2 que cada una usa índice y ordena por `hora_inicio ASC`.
- [x] 1.2 Agregar `AuthzService.isPropioProfesional(profesionalId)` espejo de `isPropioPaciente` (via `profesionalRepository.findByUsuarioId`) y verificar con test unitario que ficha ajena/inexistente → false.

## 2. Servicio listar + detalle con scoping por rol

- [x] 2.1 Implementar `TurnoService.listar(params, page, size)` `@Transactional(readOnly=true)`: resuelve ficha propia por rol, aplica scoping (PACIENTE fuerza `paciente_id` e ignora `profesional`; ODONTOLOGO fuerza `profesional_id`, otro → `TurnoForbiddenException`; SECRETARIA/ADMIN sin filtro), `fecha` default hoy en `America/Argentina/Buenos_Aires`, cap rango 31 días, mapeo batch sin N+1, y verificar con tests H2 de scoping x3 roles (paciente solo suyos, odontólogo solo propia + 403 otra, secretaria todos).
- [x] 2.2 Implementar `TurnoService.detalle(id)` `readOnly`: `findById` → 404 inexistente; scoping post-lectura (paciente ajeno → 404, odontólogo ajeno → 403) y verificar con tests H2 (detalle ok anidado, 404 inexistente, 404 paciente-ajeno, 403 odontólogo-ajeno).

## 3. Agenda + huecos libres

- [x] 3.1 Implementar `HuecosCalculator` puro (slots `HORARIO_DESDE→HASTA` step `DURACION_MIN`, resta solapados `RESERVADO` con semántica de bordes C-08, `CANCELADO`/`ATENDIDO` libres) y verificar con tests unitarios (excluye RESERVADO, incluye CANCELADO como libre, adyacente no solapa).
- [x] 3.2 Implementar `TurnoService.agenda(profesionalId, fecha)` `readOnly`: 404 profesional inexistente, scoping por rol (D-02), ocupados via query RESERVADO existente + `libres` del calculator, y verificar con test H2 de agenda con 1 RESERVADO + 1 CANCELADO (libres excluye solo el RESERVADO).

## 4. Controller + DTOs + errores

- [x] 4.1 Crear DTOs `TurnoListResponse` (envelope `content/page/size/totalElements` sobre `Page.map(TurnoResponse::from)`), `AgendaResponse`/`HuecosResponse`, `TurnoQueryParams`, con constructor injection y `@Valid`, y verificar compilación + serialización JSON de un ejemplo.
- [x] 4.2 Extender `TurnoController` con `GET /api/turnos`, `GET /api/turnos/{id}`, `GET /api/profesionales/{id}/agenda?fecha=` (`@PreAuthorize("isAuthenticated()")` → 401) + extender `GlobalExceptionHandler` (`DateTimeParseException`/`estado` inválido → 400 `fieldErrors.fecha`) y verificar con MockMvc H2: vista semana 7 días, fecha inválida 400, lista vacía `content:[]`, 401 sin sesión.

## 5. Integración y cierre

- [x] 5.1 Test E2E H2 del Flujo 3 por rol (secretaria día+semana, odontólogo propia, paciente suyos) con seed de 3 turnos y verificar orden cronológico + paciente/profesional/especialidad/estado presentes + TZ `America/Argentina/Buenos_Aires` en fecha default.
- [x] 5.2 Pasar `openspec validate --change c-09-turnos-consulta-agenda --strict` y `git status/diff` limpio de alcance (solo archivos del change + código planeado, sin commit) y verificar que no hay migración nueva ni secrets.
