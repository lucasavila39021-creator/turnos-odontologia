# Reglas de Negocio — Turnos Odontología

Cada regla tiene código `RN-{DOMINIO}-{NN}` para trazabilidad en `06` y `07`.

## Dominio: Autenticación y Autorización (RN-AU)

- **RN-AU-01**: Toda ruta salvo `/login` y `/` requiere usuario autenticado — evita acceso anónimo a agenda/datos de pacientes.
- **RN-AU-02**: La autorización se valida en backend por rol (RBAC), no solo ocultando UI — evita bypass por URL/API directa.
- **RN-AU-03**: Un Paciente solo puede ver/operar turnos donde `turno.paciente_id` corresponde a su ficha — aislamiento por paciente.
- **RN-AU-04**: Un Odontólogo solo puede consultar su propia agenda (`turno.profesional_id = su profesional_id`) — no ve agenda de colegas.
- **RN-AU-05**: Solo Admin puede ABM de usuarios/roles y especialidades — evita escalamiento de privilegios.
- **RN-AU-06**: Un usuario tiene un único rol en v1 — simplifica RBAC; multi-rol es v2.

## Dominio: Pacientes (RN-PA)

- **RN-PA-01**: DNI es único e irrepetible — evita duplicar ficha.
- **RN-PA-02**: Nombre, apellido y DNI son obligatorios al crear paciente — datos mínimos para identificar.
- **RN-PA-03**: Email y teléfono son opcionales pero si se cargan deben ser válidos (formato) — evita datos basura.
- **RN-PA-04**: Un paciente puede tener 0..N turnos; no se elimina paciente con turnos futuros RESERVADOS — evita huérfanos.

## Dominio: Profesionales y Especialidades (RN-PR)

- **RN-PR-01**: Profesional debe tener al menos una especialidad asignada — evita turnos sin especialidad.
- **RN-PR-02**: Matrícula es única si se carga — evita duplicados.
- **RN-PR-03**: Especialidad `nombre` es único — evita "Ortodoncia" duplicada con distinta capitalización (normalizar).
- **RN-PR-04**: No se puede desactivar profesional con turnos futuros RESERVADOS — evita agenda con profesional inactivo.
- **RN-PR-05**: Al crear turno, la especialidad debe pertenecer al profesional (`profesional_especialidad` existe) — coherencia catálogo.

## Dominio: Turnos (RN-TU) — núcleo del sistema

- **RN-TU-01 — No superposición**: No pueden existir dos turnos `RESERVADO` para el mismo profesional que se solapen en tiempo el mismo día. Validación en servicio + transacción DB; si hay carrera, gana el primero (constraint lógico, no solo índice). — *Justificación: es el problema raíz (superposiciones).*
- **RN-TU-02 — Duración fija**: Todo turno dura un bloque fijo (ej. 30 min, configurable global pero no por turno en v1). `hora_fin = hora_inicio + duración`. — *Simplifica agenda y evita huecos irregulares.*
- **RN-TU-03 — Horario de atención**: `hora_inicio` debe estar dentro del horario de atención de la clínica (ej. 08:00-18:00, configurable). Fuera de rango se rechaza.
- **RN-TU-04 — Fecha no pasada**: No se puede crear turno con `fecha < hoy` ni `fecha+hora < ahora` si es hoy — evita turnos retroactivos.
- **RN-TU-05 — Un paciente, un turno a la vez**: Un paciente no puede tener dos turnos RESERVADOS solapados (mismo día/hora con profesionales distintos) — evita doble reserva del paciente.
- **RN-TU-06 — Estados válidos**: Turno nace `RESERVADO`; solo puede pasar a `CANCELADO` o `ATENDIDO`. No hay transición `CANCELADO → RESERVADO` (se crea uno nuevo). — *Trazabilidad.*
- **RN-TU-07 — Cancelación**: Puede cancelar el paciente (solo sus turnos) o secretaria/admin (cualquiera). Odontólogo no cancela en v1. Cancelar requiere `motivo` opcional pero recomendado. No se puede cancelar turno ya `ATENDIDO` o `CANCELADO`.
- **RN-TU-08 — Reprogramar = cancelar + crear**: No hay "mover" atómico en v1; reprogramar es cancelar el anterior y crear uno nuevo (dos operaciones, auditables). — *Simplicidad v1.*
- **RN-TU-09 — Profesional y especialidad obligatorios**: Todo turno tiene `profesional_id` y `especialidad_id` no nulos — evita turnos huérfanos.
- **RN-TU-10 — Anticipación mínima (opcional v1)**: Si se configura, no se puede reservar con menos de X horas de anticipación (ej. 2hs). Si no se configura, no aplica.

## Dominio: Validaciones Globales (RN-GL)

- **RN-GL-01**: Todos los inputs se validan en backend con mensajes claros (Bean Validation / Django Validators) — no confiar solo en frontend.
- **RN-GL-02**: Fechas y horas se persisten en zona horaria de la clínica (America/Argentina/Buenos_Aires) — evita desfases.
- **RN-GL-03**: Borrado físico no existe en v1; "eliminar" es `CANCELADO` o `activo=false` — auditoría y recuperación.
