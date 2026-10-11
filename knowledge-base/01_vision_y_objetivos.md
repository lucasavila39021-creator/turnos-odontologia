# Visión y Objetivos — Turnos Odontología

## Propósito del sistema
Centralizar y agilizar la gestión de turnos de una clínica odontológica universitaria en un calendario único confiable que elimine superposiciones y desorganización en la atención diaria.

La clínica hoy gestiona turnos de forma dispersa (WhatsApp, papel, planillas) lo que genera choques de agenda, baches ociosos y pérdida de tiempo de secretaría y profesionales. El sistema es el **único punto de verdad** para qué paciente, con qué profesional, en qué horario y por qué especialidad.

## Objetivos por actor

| Actor | Objetivo principal | Objetivos secundarios |
|-------|--------------------|-----------------------|
| **Paciente** | Reservar y cancelar su turno sin fricción y sin depender del teléfono | Ver sus próximos turnos, recibir confirmación, evitar esperas |
| **Secretaria** | Gestionar todos los turnos del día/semana sin superposiciones | Reasignar, cancelar, buscar huecos libres, dar de alta pacientes |
| **Odontólogo** | Consultar su agenda personal del día/semana de forma clara | Ver detalle del paciente y especialidad, anticipar carga |
| **Admin** | Configurar usuarios, roles y especialidades sin tocar código | Mantener catálogo de profesionales y permisos al día |

## Alcance v1.0 — MVP (P2:a)

- ABM de **Pacientes** (nombre, DNI, contacto)
- ABM de **Profesionales** (vinculados a Usuario + Especialidades)
- ABM de **Especialidades** (ej. Ortodoncia, Endodoncia, Cirugía)
- ABM de **Usuarios y Roles** (Paciente, Secretaria, Odontólogo, Admin)
- **Calendario** por día/semana y por profesional
- **Alta de turno** con validación de no-superposición
- **Cancelación** de turno (por paciente o secretaria)
- **Consulta de agenda** (odontólogo ve su día, secretaria ve todo)
- Autenticación y RBAC básico
- Validaciones de negocio (duración fija, horarios de atención)

## Fuera de alcance (v1 explícitamente NO incluye)

- Recordatorios automáticos (WhatsApp/email/push) — v2
- Historia clínica odontológica — v2
- Pagos, señas o facturación — v2
- Multi-sede / multi-clínica / multi-tenant — v2
- Métricas y reportes avanzados (no-show, ocupación) — v2
- Integración con obra social / SSO universitario — v2
- App móvil nativa — v2 (web responsive sí)

## Métricas de éxito (v1)

- 0 turnos superpuestos registrados (RN-TU-01 = 100% cumplimiento)
- Tiempo de secretaria para dar un turno < 1 min (vs. 3-5 min actual estimado)
- 100% de turnos consultables por odontólogo antes de la jornada
- Reducción de huecos por desorganización (medible en ocupación semanal)
- Adopción: >80% de turnos del mes cargados en sistema vs. fuera de sistema
