# Funcionalidades — Turnos Odontología

Organizadas por épica y luego por historia de usuario (US-NNN). Cada US referencia reglas RN.

## Épica 1: Autenticación y Usuarios

### US-001 — Login
**Como** usuario (cualquier rol)  
**Quiero** iniciar sesión con usuario y contraseña  
**Para** acceder a mi agenda/funciones según mi rol

**Criterios de aceptación:**
- [ ] CA-1: Formulario con username + password, mensaje de error genérico si falla
- [ ] CA-2: Sesión creada, redirección según rol (paciente→mis turnos, odontólogo→mi agenda)
- [ ] CA-3: Ruta protegida redirige a login si no autenticado (RN-AU-01)
**Reglas relacionadas:** RN-AU-01, RN-AU-02

### US-002 — ABM Usuarios (Admin)
**Como** Admin  
**Quiero** crear/editar/desactivar usuarios y asignar rol  
**Para** mantener el plantel y permisos al día

**Criterios:**
- [ ] CA-1: Alta con username único, password hasheado, rol único (RN-AU-06)
- [ ] CA-2: Edición de rol y activo/inactivo
- [ ] CA-3: No se puede auto-desactivar el último Admin
**Reglas:** RN-AU-05, RN-AU-06

## Épica 2: Pacientes

### US-010 — Alta de paciente
**Como** Secretaria (o Admin)  
**Quiero** dar de alta un paciente con DNI, nombre, contacto  
**Para** poder asignarle turnos

**Criterios:**
- [ ] CA-1: DNI único, validación RN-PA-01/02
- [ ] CA-2: Mensaje claro si DNI ya existe (link a ficha existente)
**Reglas:** RN-PA-01, RN-PA-02, RN-PA-03

### US-011 — Consulta y edición de paciente
**Como** Secretaria  
**Quiero** buscar paciente por DNI/apellido y editar su ficha  
**Para** corregir datos o ver su historial de turnos

**Criterios:**
- [ ] CA-1: Buscador por DNI o apellido (índice)
- [ ] CA-2: Paciente ve solo su propia ficha (RN-AU-03)
**Reglas:** RN-PA-04, RN-AU-03

## Épica 3: Catálogo — Profesionales y Especialidades

### US-020 — ABM Especialidades (Admin)
**Como** Admin  
**Quiero** crear/editar/desactivar especialidades  
**Para** mantener el catálogo (Ortodoncia, etc.)

**Criterios:**
- [ ] CA-1: Nombre único (RN-PR-03), descripción opcional
- [ ] CA-2: No desactivar si hay profesionales activos con esa especialidad sin alternativa
**Reglas:** RN-PR-03

### US-021 — ABM Profesionales (Admin)
**Como** Admin  
**Quiero** dar de alta profesionales con matrícula y especialidades  
**Para** que tengan agenda asignable

**Criterios:**
- [ ] CA-1: Al menos una especialidad (RN-PR-01), matrícula única si se carga
- [ ] CA-2: Vinculación opcional a Usuario con rol ODONTOLOGO
**Reglas:** RN-PR-01, RN-PR-02, RN-PR-04

## Épica 4: Agenda y Turnos — núcleo

### US-030 — Ver agenda
**Como** Secretaria / Odontólogo / Paciente  
**Quiero** ver la agenda por día/semana (y por profesional si soy secretaria)  
**Para** saber qué huecos hay y qué turnos existen

**Criterios:**
- [ ] CA-1: Vista día y semana, filtro por profesional y fecha
- [ ] CA-2: Odontólogo ve solo su agenda (RN-AU-04), Paciente solo sus turnos (RN-AU-03)
- [ ] CA-3: Turnos muestran paciente, profesional, especialidad, hora, estado
**Reglas:** RN-AU-03, RN-AU-04

### US-031 — Reservar turno
**Como** Paciente (propio) o Secretaria (para cualquier paciente)  
**Quiero** reservar un turno eligiendo paciente, profesional, especialidad, fecha y hora  
**Para** asegurar atención sin superposición

**Criterios:**
- [ ] CA-1: Validación de no-superposición por profesional (RN-TU-01) y por paciente (RN-TU-05) — mensaje claro si choca
- [ ] CA-2: Validación de horario de atención (RN-TU-03), fecha no pasada (RN-TU-04), especialidad pertenece a profesional (RN-PR-05)
- [ ] CA-3: Duración fija aplicada automáticamente (RN-TU-02)
- [ ] CA-4: Turno se crea en estado RESERVADO
**Reglas:** RN-TU-01, RN-TU-02, RN-TU-03, RN-TU-04, RN-TU-05, RN-PR-05

### US-032 — Cancelar turno
**Como** Paciente (propio) o Secretaria/Admin  
**Quiero** cancelar un turno RESERVADO  
**Para** liberar el hueco

**Criterios:**
- [ ] CA-1: Solo estado RESERVADO es cancelable (RN-TU-06/07)
- [ ] CA-2: Paciente solo cancela los suyos; secretaria cualquiera (RN-AU-03)
- [ ] CA-3: Pide motivo opcional, cambia a CANCELADO, libera hueco
**Reglas:** RN-TU-06, RN-TU-07

### US-033 — Reprogramar turno (Secretaria)
**Como** Secretaria  
**Quiero** reprogramar un turno a otra fecha/hora  
**Para** acomodar pedido del paciente sin perder trazabilidad

**Criterios:**
- [ ] CA-1: Implementado como cancelar + crear (RN-TU-08), ambos auditados
- [ ] CA-2: Valida no-superposición en el nuevo horario
**Reglas:** RN-TU-08, RN-TU-01

### US-034 — Marcar como atendido (Secretaria/Odontólogo)
**Como** Secretaria u Odontólogo  
**Quiero** marcar un turno RESERVADO como ATENDIDO  
**Para** cerrar la atención del día

**Criterios:**
- [ ] CA-1: Solo RESERVADO → ATENDIDO, no desde CANCELADO
- [ ] CA-2: Visible en historial
**Reglas:** RN-TU-06
