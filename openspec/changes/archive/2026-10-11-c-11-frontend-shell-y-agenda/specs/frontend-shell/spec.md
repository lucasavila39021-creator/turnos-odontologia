# Spec — frontend-shell (delta C-11)

## ADDED Requirements

### Requirement: Shell con menú por rol y redirect raíz

El sistema SHALL renderizar un layout SSR con sidebar/header y menú según rol (PACIENTE: Mis turnos/Nuevo turno; ODONTOLOGO: Mi agenda; SECRETARIA: Agenda/Pacientes/Turnos; ADMIN: + Usuarios/Especialidades/Profesionales). `GET /` SHALL redirigir a `/login` sin sesión y al dashboard del rol con sesión.

#### Scenario: Redirect raíz sin sesión

- **WHEN** un anónimo pide `GET /`
- **THEN** responde redirect a `/login`.

#### Scenario: Menú por rol

- **WHEN** un ODONTOLOGO autenticado renderiza el shell
- **THEN** ve "Mi agenda" y NO ve "Usuarios" ni "Pacientes".

### Requirement: Página de login mejorada

`GET /login` SHALL mostrar form username+password con token CSRF, error genérico "Credenciales inválidas" ante 401 y aviso "Sesión expirada" con `?expired`. Tras login exitoso SHALL redirigir según rol (PACIENTE→mis turnos, ODONTOLOGO→agenda, SECRETARIA/ADMIN→agenda).

#### Scenario: Error genérico de login

- **WHEN** el login falla
- **THEN** la página muestra "Credenciales inválidas" sin revelar si el usuario existe.

### Requirement: Página de agenda día/semana

`GET /agenda` SHALL mostrar vista día (default hoy) y semana (rango 7 días), filtro profesional+fecha, lista cronológica por `hora_inicio` con paciente/especialidad/estado, y huecos libres destacados. SHALL respetar scoping backend (paciente solo suyos, odontólogo solo propia).

#### Scenario: Agenda día ordenada

- **WHEN** una secretaria abre `/agenda?fecha=2026-10-12&profesional=1`
- **THEN** ve los turnos ordenados por hora con paciente, especialidad y badge de estado, más los huecos libres.

#### Scenario: Agenda vacía

- **WHEN** no hay turnos ese día
- **THEN** muestra estado vacío ("Sin turnos este día") y los huecos libres disponibles.

### Requirement: Formulario nuevo turno

`GET /turnos/nuevo` SHALL ofrecer autocomplete de paciente por DNI/apellido, select de profesional, especialidad filtrada por profesional (RN-PR-05), fecha y slots libres; SHALL validar inline (campos obligatorios, fecha no pasada, horario válido) y ante 409 mostrar "Horario ocupado" con horarios sugeridos sin perder los datos cargados.

#### Scenario: Conflicto con sugeridos

- **WHEN** el POST choca con un turno existente (409 con `sugeridos`)
- **THEN** el form re-renderiza con "Horario ocupado, probá 10:30" y conserva paciente/profesional/fecha.

### Requirement: Detalle de turno con acciones por rol/estado

`GET /turnos/{id}` SHALL mostrar detalle (paciente, profesional, especialidad, fecha/hora, estado, motivo) y botones solo según rol+estado: Cancelar (PACIENTE propios, SECRETARIA/ADMIN), Reprogramar (SECRETARIA/ADMIN, solo RESERVADO), Atendido (SECRETARIA/ODONTOLOGO propia agenda, solo RESERVADO). Turnos CANCELADO/ATENDIDO SHALL mostrar badge sin botones de transición.

#### Scenario: Botones ocultos por rol

- **WHEN** un PACIENTE abre el detalle de un turno ajeno o un ODONTOLOGO abre `/turnos/{id}` con acción Cancelar
- **THEN** no ve el botón (403 backend si fuerza la URL).

### Requirement: Página de pacientes con alta inline

`GET /pacientes` SHALL ofrecer buscador por DNI/apellido y alta inline sin salir del flujo (Flujo 4: alta → redirige a `/turnos/nuevo` con paciente preseleccionado). DNI duplicado SHALL mostrar error con link a la ficha existente. `GET /pacientes/{id}` SHALL mostrar ficha + historial de turnos; PACIENTE solo ve su propia ficha (ajena → 403/"No autorizado").

#### Scenario: Alta inline Flujo 4

- **WHEN** una secretaria da de alta un paciente desde `/pacientes`
- **THEN** puede continuar a `/turnos/nuevo` con ese paciente ya seleccionado.

### Requirement: Manejo UX de errores HTTP

Las páginas SHALL mapear errores sin exponer stacktraces: sesión ausente/expirada → redirect `/login`; 403 → "No autorizado"; 409 → "Horario ocupado" + sugeridos; 400 → mensaje junto al campo correspondiente. Ninguna página SHALL devolver 500 por input inválido.

#### Scenario: Sesión expirada en agenda

- **WHEN** la sesión expira y el usuario pide `/agenda`
- **THEN** es redirigido a `/login?expired`.

### Requirement: Responsive mobile-first y estados visuales

El shell SHALL ser responsive Bootstrap mobile-first (usable en tablet de mostrador) con `TurnoCard` por estado (RESERVADO azul, CANCELADO tachado gris, ATENDIDO verde) y `EstadoBadge` consistente. Los formularios SHALL asociar `label` a cada input y anunciar errores por campo.

#### Scenario: Agenda en viewport móvil

- **WHEN** se abre `/agenda` en 360px de ancho
- **THEN** la lista es legible sin scroll horizontal y los filtros permanecen accesibles.
