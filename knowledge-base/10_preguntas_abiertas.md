# Preguntas Abiertas — Turnos Odontología

## Inconsistencias detectadas

### IN-01 — Paciente sin cuenta vs. Paciente que reserva
**Documento A dice:** Paciente reserva/cancela (P3) — implica login.  
**Documento B dice:** Secretaria da de alta paciente sin usuario (SU-02).  
**Impacto:** Dos caminos para crear paciente; si no se unifica, hay duplicados (DNI con y sin usuario).  
**Resolución propuesta:** Permitir ambos, pero al crear Usuario Paciente validar DNI único contra Paciente existente y linkear `paciente.usuario_id` si ya existe. Priorizar: Alta.

### IN-02 — Duración fija global vs. especialidades con distinta duración
**Documento A dice:** RN-TU-02 duración fija global.  
**Documento B dice:** DD-03 reconoce que ortodoncia/cirugía podrían necesitar distinto tiempo.  
**Impacto:** Workaround de 2 bloques para 60 min es frágil (puede quedar 1 bloque huérfano).  
**Resolución propuesta:** Mantener fija en v1, registrar en v2: columna `especialidad.duracion_min` y `profesional_especialidad.duracion_min` opcional. Priorizar: Media.

## Preguntas abiertas (priorizadas)

| Prioridad | Pregunta | Bloquea | Decisor |
|-----------|----------|---------|---------|
| **Alta** | ¿Horario de atención es global o por profesional/día? (valida SU-03 y RN-TU-03) | Modelado de Turno y validación | Product Owner / Clínica |
| **Alta** | ¿Cuántas sedes/sillones hay y si v1 debe soportar más de una? (valida SU-01) | ERD (falta `sede_id` si es multi-sede) | Clínica |
| **Alta** | ¿Todo paciente debe tener cuenta o basta con ficha creada por secretaria? (valida SU-02) | Flujo de alta y RBAC | PO |
| **Media** | ¿Duración por especialidad en v1 o v2? (valida SU-04) | RN-TU-02 | PO |
| **Media** | ¿Anticipación mínima y política de cancelación (ej. hasta 24hs antes)? (RN-TU-10) | RN-TU-07 | Clínica |
| **Media** | ¿SSO universitario / LDAP en v1 o login propio alcanza? | Auth (DD-06) | Cátedra / IT |
| **Media** | ¿Requieren auditoría append-only o alcanza created_by/at? (SU-06) | Modelo de auditoría | Cátedra |
| **Baja** | ¿Recordatorios WhatsApp/email en v2 con qué proveedor? (Twilio, SendGrid, interno) | Integración v2 | PO |
| **Baja** | ¿Un usuario puede tener dos roles (ej. secretaria que también es paciente)? (DD-05) | RBAC multi-rol | PO |
| **Baja** | ¿App móvil en roadmap o web responsive alcanza? | Stack frontend | PO |
| **Baja** | ¿Métricas de ocupación/no-show en v1 o v2? | Reportes | Clínica |
