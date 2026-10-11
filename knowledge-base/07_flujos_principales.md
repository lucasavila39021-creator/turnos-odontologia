# Flujos Principales — Turnos Odontología

Cada flujo extremo a extremo con disparador, actor, pasos y casos de error.

## Flujo 1: Reserva de turno (Paciente o Secretaria)

**Disparador:** Paciente quiere atenderse / Secretaria recibe pedido por mostrador/teléfono  
**Actor:** Paciente (propio) o Secretaria (para paciente)

**Pasos:**
1. Actor se autentica (`POST /login`) — RN-AU-01
2. Actor navega a "Nuevo turno" → elige Profesional y Especialidad (validado RN-PR-05)
3. Elige Fecha y Hora (frontend muestra huecos libres consultando `GET /api/turnos?profesional=&fecha=`)
4. Sistema valida en backend:
   - Horario dentro de atención (RN-TU-03)
   - Fecha no pasada (RN-TU-04)
   - No superposición profesional (RN-TU-01)
   - No superposición paciente (RN-TU-05)
   - Duración fija calculada (RN-TU-02)
5. Si pasa validación → `INSERT turno RESERVADO` en transacción, `created_by = current_user`
6. Respuesta 201 con detalle del turno; UI muestra confirmación y turno en agenda

**Diagrama de secuencia:**
```
Paciente/Secretaria → Frontend → API (POST /turnos) → Service (valida RN-*) → MySQL (INSERT en TX)
                                    ← 201 turno / 409 conflicto por superposición
Frontend ← respuesta → muestra confirmación o mensaje "Horario ocupado, probá 10:30"
```

**Casos de error:**
- Horario ocupado (RN-TU-01/05) → 409 Conflict + huecos sugeridos
- Fuera de horario (RN-TU-03) → 400 + rango válido
- Especialidad no pertenece a profesional (RN-PR-05) → 400
- Fecha pasada (RN-TU-04) → 400

## Flujo 2: Cancelación de turno

**Disparador:** Paciente no puede asistir / Secretaria libera hueco  
**Actor:** Paciente (propio) o Secretaria/Admin

**Pasos:**
1. Actor ve "Mis turnos" o "Agenda del día" y elige turno RESERVADO
2. Click "Cancelar" → modal pide motivo (opcional)
3. `PATCH /turnos/{id}/cancelar` con motivo
4. Backend valida: estado es RESERVADO (RN-TU-06), actor tiene permiso (RN-AU-03 / secretaria), no es ATENDIDO (RN-TU-07)
5. `UPDATE turno SET estado=CANCELADO, motivo_cancelacion=...` — hueco queda libre
6. UI actualiza agenda; turno aparece tachado en historial

**Casos de error:**
- Ya CANCELADO/ATENDIDO → 400 "No se puede cancelar"
- Paciente intenta cancelar turno ajeno → 403

## Flujo 3: Consulta de agenda del día (Odontólogo y Secretaria)

**Disparador:** Inicio de jornada, odontólogo quiere ver a quién atiende  
**Actor:** Odontólogo (propia) / Secretaria (todas)

**Pasos:**
1. Login → redirección a "Agenda"
2. `GET /turnos?profesional={id}&fecha=hoy&estado=RESERVADO,ATENDIDO` (odontólogo: `profesional` forzado a su id)
3. Backend filtra por rol (RN-AU-03/04) y ordena por `hora_inicio`
4. UI muestra lista cronológica: 09:00 Paciente A (Ortodoncia), 09:30 Paciente B...
5. Click en turno → detalle paciente (nombre, DNI, teléfono)

**Casos de error:**
- Sin turnos → mensaje "No tenés turnos hoy"
- Fecha inválida → 400

## Flujo 4: Alta de paciente + primer turno (Secretaria)

**Disparador:** Paciente nuevo sin ficha  
**Actor:** Secretaria

**Pasos:**
1. Secretaria busca por DNI → no existe
2. "Nuevo paciente" → carga DNI, nombre, apellido, contacto (RN-PA-02/03)
3. `POST /pacientes` → 201
4. Sin salir, "Asignar turno" → Flujo 1 con ese paciente + profesional/especialidad/fecha/hora
5. Todo en 2 pasos, sin re-ingresar datos

**Casos de error:**
- DNI duplicado (RN-PA-01) → 409 + link a ficha existente
- Datos obligatorios faltantes → 400 con detalle por campo

## Flujo 5: Reprogramación (Secretaria)

**Disparador:** Paciente pide mover turno  
**Actor:** Secretaria

**Pasos:**
1. Secretaria abre turno RESERVADO → "Reprogramar"
2. Elige nueva fecha/hora
3. Sistema ejecuta `CANCELAR original (motivo: reprogramado)` + `CREAR nuevo` (RN-TU-08) — dos operaciones auditadas
4. Valida no-superposición en nuevo horario (RN-TU-01/05)
5. Si ok → original pasa a CANCELADO, nuevo queda RESERVADO; si falla → se revierte y se informa

**Casos de error:**
- Nuevo horario ocupado → 409, se mantiene el original RESERVADO
- Original ya no es RESERVADO → 400

## Flujo 6: Login y RBAC

**Disparador:** Usuario entra a `/` sin sesión  
**Actor:** Cualquiera

**Pasos:**
1. `GET /` → middleware detecta sin sesión → redirect `/login`
2. Usuario envía credenciales → `POST /login` → valida hash, crea sesión/JWT, set cookie
3. `GET /me` → devuelve rol; frontend muestra menú según rol
4. Cada request posterior valida sesión y rol en backend (RN-AU-02) — no basta ocultar botón

**Casos de error:**
- Credenciales inválidas → 401 genérico (no revela si usuario existe)
- Sesión expirada → 401 → redirect login
