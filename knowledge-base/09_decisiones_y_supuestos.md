# Decisiones y Supuestos — Turnos Odontología

## Decisiones documentadas

### DD-01 — Stack Java o Python + MySQL (orientación universitaria)
**Decisión:** Documentar dos variantes equivalentes (Java Spring Boot o Python Django/FastAPI) + MySQL 8.0, monolito modular.  
**Contexto:** P4 exige entorno universitario con preferencia Java/Python + relacional; P5 prioriza mantenibilidad.  
**Alternativas consideradas:** Node.js + Postgres, PHP Laravel, stack serverless.  
**Justificación:** Java y Python son los más enseñados en universidades hispanas; MySQL es el relacional más disponible en labs; monolito reduce ops y facilita corrección/evaluación.  
**Trade-offs aceptados:** No se optimiza para escalabilidad masiva (no hace falta en P0-scale:a); se resigna reactividad full (WebSockets) en v1.

### DD-02 — MySQL como BD relacional
**Decisión:** MySQL 8.0 con InnoDB, FKs y migrations versionadas.  
**Contexto:** Requisito P4 (relacional) y necesidad de transacciones para no-superposición.  
**Alternativas:** Postgres, SQLite, H2 en memoria.  
**Justificación:** Cumple ACID, disponible en cualquier hosting universitario, ORM maduros en ambos stacks.  
**Trade-offs:** Menos tipos avanzados que Postgres (JSONB, rangos), irrelevante en v1.

### DD-03 — Duración de turno fija global
**Decisión:** Todo turno dura un bloque fijo configurable globalmente (ej. 30 min), no por turno ni por especialidad en v1 (RN-TU-02).  
**Contexto:** Simplificar agenda y validación de huecos.  
**Alternativas:** Duración por especialidad o por profesional.  
**Justificación:** Reduce complejidad de UI y de RN-TU-01; se puede extender a duración por especialidad en v2 sin migrar datos (agregar columna).  
**Trade-offs:** Si una especialidad necesita 60 min, ocupa 2 bloques contiguos en v1 (workaround).

### DD-04 — Reprogramar = cancelar + crear
**Decisión:** No hay operación atómica "mover"; reprogramar son dos operaciones auditadas (RN-TU-08).  
**Contexto:** Evitar transacción compleja y mantener trazabilidad.  
**Alternativas:** UPDATE de fecha/hora in-place.  
**Justificación:** Más simple de implementar y auditar; si falla el segundo paso, el primero queda CANCELADO con motivo "reprogramado" y se ve en historial.  
**Trade-offs:** Dos writes en vez de uno; requiere manejo de error que revierta o informe.

### DD-05 — RBAC de rol único, sin ABAC en v1
**Decisión:** Un usuario, un rol; permisos por rol (RN-AU-06).  
**Contexto:** 4 roles bien diferenciados (P3), sin necesidad de permisos finos por recurso.  
**Alternativas:** ABAC, multi-rol, permisos por recurso.  
**Justificación:** Suficiente para v1, fácil de enseñar y testear; multi-rol se agrega en v2 si aparece caso real.  
**Trade-offs:** Si una secretaria también es paciente, necesita dos cuentas en v1.

### DD-06 — Sin integraciones externas en v1
**Decisión:** v1 sin WhatsApp/email, sin SSO, sin pagos.  
**Contexto:** P2:a (solo agenda) y P5:b (mantenibilidad).  
**Alternativas:** Integrar recordatorios desde día 1.  
**Justificación:** Cada integración multiplica configuración, secrets y fallos; postergar permite validar el core (no-superposición) antes de acoplar servicios.  
**Trade-offs:** No-show no se ataca en v1; se mitiga en v2.

### DD-07 — SSR vs. SPA: dejar abierto
**Decisión:** Documentar ambas (Thymeleaf/Django Templates SSR o React SPA + REST) como equivalentes; elegir una al implementar.  
**Contexto:** P0-sys:a (web app) no especifica SPA.  
**Justificación:** SSR es más simple para v1 universitario (menos tooling); SPA es mejor si el equipo quiere separar frontend. No forzar.  
**Trade-offs:** SSR recarga página; SPA requiere API y auth JWT.

---

## Supuestos inferidos

### SU-01 — Sede única, 2-5 odontólogos
**Supuesto:** 1 sede, plantel chico, ~20-40 turnos/día.  
**Origen:** P0-scale:a + P1 (clínica singular) + P2:a.  
**Riesgo si es falso:** Si son 10 sedes, falta `sede_id` y multi-tenant.  
**Cómo validar:** Preguntar "¿cuántos sillones/sedes tienen hoy?" antes de modelar Turno.

### SU-02 — Paciente puede existir sin cuenta
**Supuesto:** Secretaria puede crear Paciente sin Usuario asociado.  
**Origen:** P3 (Secretaria gestiona, Paciente reserva) — no todo paciente se auto-registra.  
**Riesgo si es falso:** Si todo paciente debe auto-registrarse, sobra flujo de alta por secretaria.  
**Cómo validar:** Confirmar si la clínica acepta turnos telefónicos/presenciales sin registro previo.

### SU-03 — Horario de atención único global
**Supuesto:** Un único rango (ej. 08:00-18:00) para toda la clínica en v1, no por profesional ni por día.  
**Origen:** Simplificación v1 (RN-TU-03).  
**Riesgo si es falso:** Si cada odontólogo tiene horario distinto, RN-TU-03 debe ser por profesional.  
**Cómo validar:** Pedir grilla horaria real de 1 semana.

### SU-04 — Duración fija no varía por especialidad en v1
**Supuesto:** Ver DD-03.  
**Origen:** P2:a + mantenibilidad.  
**Riesgo si es falso:** Ver trade-off DD-03.  
**Cómo validar:** Preguntar "¿ortodoncia y cirugía duran lo mismo?".

### SU-05 — Zona horaria fija Argentina
**Supuesto:** Toda fecha/hora en `America/Argentina/Buenos_Aires`.  
**Origen:** Clínica universitaria local.  
**Riesgo si es falso:** Si hay pacientes/profesionales en otras zonas, hay desfase.  
**Cómo validar:** Confirmar sede.

### SU-06 — No hay auditoría regulatoria estricta en v1
**Supuesto:** Trazabilidad mínima (created_by/at) alcanza; no se exige audit trail append-only ni firma digital.  
**Origen:** Ausencia de mención de compliance en P4.  
**Riesgo si es falso:** Si la universidad exige log inmutable, falta tabla de auditoría.  
**Cómo validar:** Preguntar a cátedra/coordinación si hay requisito de auditoría.
