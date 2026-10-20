# Informe de Discovery - MVP Gestión de Turnos Odontológicos

## 1. El Problema
En las clínicas odontológicas pequeñas, la gestión manual de agendas (WhatsApp, papel o Excel) provoca errores humanos severos, destacando el solapamiento de turnos (darle dos pacientes al mismo médico a la misma hora) y la pérdida de tiempo administrativo.

## 2. Público Objetivo
Consultorios odontológicos independientes y clínicas de tamaño reducido en Argentina.

## 3. La Solución (MVP)
Desarrollo de una API REST robusta (backend) especializada en la gestión estricta de citas, asegurando validación de negocio y base de datos contra el solapamiento de horarios por profesional.

## 4. Análisis de Competidores y Fuentes (Verificación Manual)
- **Fuente 1: Dentalink** (Sitio web oficial, revisado Oct 2026). *Análisis:* Software sobredimensionado. Incluye odontogramas, facturación y sueldos. Excede las necesidades de un MVP ágil.
- **Fuente 2: TuTurno** (Sitio web oficial, revisado Oct 2026). *Análisis:* Excelente frontend pero genérico (sirve para peluquerías o canchas). Carece de especificidad médica.
- **Fuente 3: Reservio** (Sitio web oficial, revisado Oct 2026). *Análisis:* Similar a TuTurno, orientado a servicios generales sin validaciones clínicas fuertes.
- **Fuente 4: Artículo Medesk** (https://www.medesk.net/es/blog/errores-comunes-agenda-medica/, revisado Oct 2026). *Análisis:* Confirma que el error más grave a resolver en consultorios es el solapamiento de horarios.
- **Fuente 5: Clínica Nube** (Revisión general, Oct 2026). *Análisis:* Enfoque excesivo en marketing, descuidando la simplicidad de la agenda.

## 5. In-Scope (Alcance del MVP)
- Gestión de Odontólogos (con validación de matrícula).
- Gestión de Pacientes.
- Reserva de Turnos con validación estricta de no-solapamiento.

## 6. Out-of-Scope (Fuera del Alcance)
- Interfaz gráfica avanzada (Frontend).
- Módulos de facturación, cobros o control de stock.
- Odontogramas digitales.