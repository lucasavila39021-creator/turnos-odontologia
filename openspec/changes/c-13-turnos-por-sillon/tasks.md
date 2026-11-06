# Tasks: C-13 Turnos por Sillón

## 1. Modelo, migración y DTOs

- [ ] 1.1 Crear `V7__sillon.sql` (tabla `sillon` con `uk_sillon_nombre` e `idx_sillon_activo`; `turno.sillon_id BIGINT UNSIGNED NULL`; FK `fk_turno_sillon` `ON DELETE RESTRICT ON UPDATE CASCADE`; índice `idx_turno_sillon_fecha(sillon_id, fecha, hora_inicio)`) y verificar con `flyway info` verde + `SHOW CREATE TABLE turno` vía `mysql_query` (columna, FK e índice presentes, sin índice automático extra para `sillon_id`).
- [ ] 1.2 Crear entidad `Sillon` (`nombre` único, `activo`, timestamps) + `SillonRepository` (`findByIdForUpdate` con `PESSIMISTIC_WRITE`, `existsByNombreIgnoreCase`) y agregar `Turno.sillonId` nullable + `@Index(name = "idx_turno_sillon_fecha", ...)`; verificar con test de persistencia H2 (guardar sillón, asociarlo a un turno, leerlo y comprobar que un turno sin sillón persiste con `NULL`).
- [ ] 1.3 Crear `SillonResponse(id, nombre)`; agregar `sillonId @NotNull` a `TurnoCreateRequest`, `sillonId` opcional a `TurnoReprogramarRequest` y `SillonInfo sillon` nullable a `TurnoResponse.from()`; verificar con tests de validación (`TurnoCreateRequest` sin `sillonId` → violación; con `sillonId` → válido) y compilación de los constructores existentes.

## 2. Servicio y regla RN-TU-11

- [ ] 2.1 Agregar `TurnoRepository.existsSolapeSillon(sillonId, fecha, inicio, fin, estado)` con la misma query de bordes que profesional/paciente y verificar con test de repositorio: solape verdadero, contiguo falso, `CANCELADO` falso y `sillon_id NULL` no matchea (H2 y, si Docker está disponible, Testcontainers MySQL).
- [ ] 2.2 Implementar en `TurnoService.doCrear` la carga/validación del sillón (inexistente → 404, inactivo → 400) y RN-TU-11 después de RN-TU-01/05; extender `calcularSugeridos` para filtrar por profesional **y** sillón; verificar con `TurnoServiceSillonTest` (H2) los escenarios: mismo sillón/profesionales distintos → 409, sillones distintos → 201, contiguo → 201, `CANCELADO` libera → 201, legacy `NULL` no bloquea → 201, sugeridos sin choque de sillón.
- [ ] 2.3 Implementar en `reprogramar`/`doReprogramar` el sillón efectivo (conservar el del original; otro distinto → 400; original legacy `NULL` exige `sillon_id` → 400), validar RN-TU-11 en destino y mantener rollback total; verificar con tests: conserva sillón, legacy sin sillón → 400 con original RESERVADO, destino con sillón ocupado → 409 con original intacto.
- [ ] 2.4 Agregar `locksSillon` y `SillonRepository.findByIdForUpdate` en el orden fijo profesional → sillón (JVM y DB), actualizar el comentario de concurrencia de `TurnoService` y verificar con `TurnoSillonConcurrenciaTest` (H2): dos altas simultáneas, profesionales distintos, mismo sillón y horario → 1 creada + 1 `TurnoOcupadoException`; nota explícita de gap-locks MySQL.
- [ ] 2.5 Extender `mapearTurnos` para resolver sillones en batch (sin N+1) y verificar con test de detalle/lista: turno con sillón devuelve `sillon{id,nombre}` y turno legacy devuelve `sillon:null` sin excepción.

## 3. API, SSR y tests existentes

- [ ] 3.1 Ajustar `TurnoController POST /api/turnos` para propagar `sillonId` y verificar con MockMvc (patrón `TurnoControllerH2Test`): alta sin `sillon_id` → 400 `fieldErrors.sillon_id`, sillón inexistente → 404, sillón inactivo → 400, solape de sillón → 409 con `sugeridos`, alta válida → 201 con `sillon.id`, y reprogramar con/sin sillón según el original.
- [ ] 3.2 Actualizar `TurnoForm` (+`sillonId`), `TurnoMvcController` (parseo obligatorio, paso a `TurnoCreateRequest`, carga de sillones activos en `cargarApoyos`) y `turno-form.html` (select de sillón + `fieldErrors['sillonId']`); verificar con test MockMvc del alta SSR: sin sillón → 400 re-renderizado, con sillón → 302 al detalle, y el HTML contiene el select.
- [ ] 3.3 Actualizar los tests existentes que construyen `TurnoCreateRequest`/`TurnoResponse` o formularios (`TurnoControllerH2Test`, `TurnoConsultaH2Test`, `TurnoFlujo3E2EH2Test`, `FlujosE2EH2Test`, `TurnoHorarioBordeTest`, `TurnoTransicionesIT`, `TurnoServiceConsultaTest`) para incluir sillón y verificar que la suite compila y sigue verde.

## 4. Seed y knowledge-base

- [ ] 4.1 Actualizar `DevDataInitializer` (crear `Sillón 1` y `Sillón 2` activos idempotentemente y asignar los turnos seed a `Sillón 1`) y `scripts/dev-db-nativo.sql` (crear sillones, asignar ambos turnos de las 10:00 al mismo sillón, dejar el CANCELADO de las 11:00 con `sillon_id NULL`); verificar ejecutando el script y consultando con `mysql_query` que hay 2 sillones, el choque de las 10:00 comparte sillón y el turno legacy tiene `NULL`.
- [ ] 4.2 Agregar **RN-TU-11 — No superposición por sillón** a `knowledge-base/05_reglas_de_negocio.md` con el texto exacto del proposal y verificar que la regla queda junto a RN-TU-01/05 con su justificación.

## 5. Integración final

- [ ] 5.1 Correr `mvn test` completo y `openspec validate c-13-turnos-por-sillon`; verificar suite verde y change válido, con los escenarios del spec cubiertos por los tests listados abajo.

## Cobertura de escenarios del spec

| Escenario | Dónde se prueba | Tipo |
|-----------|-----------------|------|
| Alta sin sillón devuelve 400 | `TurnoControllerH2Test` | MockMvc + H2 |
| Alta SSR sin sillón devuelve 400 | `TurnoMvcControllerTest` | MockMvc + H2 |
| Sillón inexistente devuelve 404 | `TurnoControllerH2Test` | MockMvc + H2 |
| Sillón inactivo devuelve 400 | `TurnoControllerH2Test` | MockMvc + H2 |
| Turno legacy sin sillón sigue siendo legible | `TurnoConsultaH2Test` + `TurnoControllerH2Test` | MockMvc + H2 |
| Alta cerca de medianoche no corre la fecha | `TurnoHorarioBordeTest` | Servicio H2 (TZ) |
| Mismo sillón y horario con profesionales distintos devuelve 409 | `TurnoServiceSillonTest` + `TurnoSillonIT` | Servicio H2 + Testcontainers MySQL |
| Mismo horario en sillones distintos se crea | `TurnoServiceSillonTest` + `TurnoSillonIT` | Servicio H2 + Testcontainers MySQL |
| Turno contiguo en el mismo sillón se crea | `TurnoServiceSillonTest` | Servicio H2 |
| Turno CANCELADO libera el sillón | `TurnoServiceSillonTest` | Servicio H2 |
| Turno ATENDIDO no bloquea el sillón | `TurnoServiceSillonTest` | Servicio H2 |
| Turnos legacy sin sillón no participan | `TurnoServiceSillonTest` + `TurnoSillonIT` | Servicio H2 + Testcontainers MySQL |
| Mismo horario en otro día no solapa | `TurnoServiceSillonTest` + `TurnoSillonIT` | Servicio H2 + Testcontainers MySQL |
| Sugeridos filtran por profesional y sillón | `TurnoServiceSillonTest` | Servicio H2 |
| Dos altas simultáneas al mismo sillón: una se crea y la otra devuelve 409 | `TurnoSillonConcurrenciaTest` | Servicio H2 |
| Reprogramar conserva el sillón | `TurnoControllerH2Test` + `TurnoServiceSillonTest` | MockMvc + H2 |
| Reprogramar legacy exige sillón | `TurnoControllerH2Test` | MockMvc + H2 |
| Reprogramar con sillón distinto devuelve 400 | `TurnoControllerH2Test` + `TurnoServiceSillonTest` | MockMvc + H2 |
| Reprogramar con sillón inexistente devuelve 404 | `TurnoControllerH2Test` | MockMvc + H2 |
| Reprogramar con sillón inactivo devuelve 400 | `TurnoControllerH2Test` | MockMvc + H2 |
| Reprogramar a sillón ocupado devuelve 409 con original intacto | `TurnoServiceSillonTest` + `TurnoControllerH2Test` | Servicio H2 + MockMvc |

## Workflow follow-up

- Correr la skill `revisor-escenarios-turnos` sobre este spec antes de aprobar el change (paso siguiente al propose; no se ejecuta ahora).
- Marcar C-13 como preparado en `CHANGES.md` y archivar el change recién después de implementar y validar.
