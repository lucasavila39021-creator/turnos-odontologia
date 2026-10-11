# Tasks: C-08 Turnos Reserva

## 1. Migración y dominio

- [x] 1.1 Crear `V6__turno.sql` (tabla `turno`, FKs NOT NULL, 3 índices, CHECK `hora_fin > hora_inicio`, ENUM default RESERVADO) y verificar con `flyway info` + arranque en H2/MySQL dev sin errores.
- [x] 1.2 Crear entidad `Turno` + `TurnoRepository` (solape profesional/paciente `hora_inicio < :fin AND hora_fin > :inicio AND estado='RESERVADO'`, `existsFuturoReservado`) y verificar con test persistencia H2 (save + find + solape true/false).
- [x] 1.3 Crear DTOs `TurnoCreateRequest` (`@Valid`: ids `@NotNull`, `fecha @NotNull`, `hora_inicio @NotNull`) / `TurnoResponse.from()` con anidados y verificar con test validación (campo ausente → 400).

## 2. Servicio y reglas RN-TU/RN-PR-05

- [x] 2.1 Implementar `TurnoService.crear` en TX serializable (orden D-05: ownership→404→RN-PR-05→hora_fin→RN-TU-03/04→solapes→save, `sugeridos` máx 3) y verificar con tests servicio H2 (201 calcula hora_fin, cada RN da su 400/404/409).
- [x] 2.2 Crear excepciones `TurnoOcupadoException(sugeridos)`→409, `TurnoInvalidoException`→400, `TurnoForbiddenException`→403 + mapeos en `GlobalExceptionHandler` (envelope problem-details con `sugeridos`) y verificar con test MockMvc del envelope.
- [x] 2.3 Implementar `AuthzService.isPropioPaciente(pacienteId)` (`paciente.usuario_id == current_user.id`) y verificar con test (PACIENTE propio ok, ajeno 403, sin link 403).

## 3. Controller y RBAC

- [x] 3.1 Crear `TurnoController POST /api/turnos` (`@PreAuthorize` PACIENTE/SECRETARIA/ADMIN, `@Valid`, 201 `TurnoResponse`) y verificar con MockMvc (reserva ok 201, solape profesional 409, solape paciente 409, fuera horario 400, pasada 400, especialidad ajena 400, paciente-ajeno 403, ODONTOLOGO crear 403).
- [x] 3.2 Test concurrencia hilos H2 (2 creates simultáneos mismo hueco → solo 1 gana, otro 409) con nota MySQL gap-locks y verificar con `mvn test -Dtest=TurnoConcurrenciaTest` verde.

## 4. Handoff C-07 y cierre

- [x] 4.1 Reemplazar guard defensivo por query real en `ProfesionalService.tieneTurnosFuturosReservados` (elimina TODO(C-08), habilita test `@Disabled("C-08")`) y verificar con test integración (desactivar con turno futuro → 400 ficha intacta; sin futuros → 200).
- [x] 4.2 Validación final con `mvn test` verde + `openspec validate --change c-08-turnos-reserva` sin errores.
