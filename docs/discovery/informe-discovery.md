# Informe de Discovery - Sistema de Gestión de Turnos Odontológicos

## Resumen Ejecutivo
Se investigó el mercado de sistemas de gestión de turnos para clínicas odontológicas en Argentina. El hallazgo principal indica que el mercado está polarizado: existen sistemas sobredimensionados y sistemas genéricos. Se recomienda un MVP enfocado estrictamente en la reserva de turnos con validación transaccional (ACID) para evitar solapamientos.

---

## A. Tabla Comparativa de 15 Sistemas

| Producto / Proveedor | País | Segmento | Reserva Online | Recordatorios | Fuente y Fecha |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **1. Dentalink** | Chile/Latam | Clínicas medianas | Comprobado | Comprobado | dentalink.net (Oct 2026) |
| **2. TuTurno** | Argentina | Genérico/Profesionales| Comprobado | Comprobado | tuturno.io (Oct 2026) |
| **3. Reservio** | Internacional | Genérico/Servicios | Comprobado | Comprobado | reservio.com (Oct 2026) |
| **4. Clinic Cloud** | España/Latam | Clínicas de salud | Comprobado | Comprobado | cliniccloud.com (Oct 2026) |
| **5. Docturno** | Argentina | Médicos particulares | Comprobado | Comprobado | docturno.com (Oct 2026) |
| **6. OdontoSoft** | Latam | Consultorios | No evidenciado | No evidenciado | Búsqueda general (Oct 2026) |
| **7. Calendly** | Internacional | Genérico | Comprobado | Comprobado | calendly.com (Oct 2026) |
| **8. Nubimed** | España | Clínicas de salud | Comprobado | Comprobado | nubimed.com (Oct 2026) |
| **9. Denteo** | Suiza | Clínicas dentales | Comprobado | No evidenciado | denteo.com (Oct 2026) |
| **10. AgendaPro** | Latam | Estética/Salud | Comprobado | Comprobado | agendapro.com (Oct 2026) |
| **11. MisTurnos** | Argentina | Genérico | Comprobado | No evidenciado | misturnos.com (Oct 2026) |
| **12. SimplyBook.me**| Internacional | Genérico | Comprobado | Comprobado | simplybook.me (Oct 2026) |
| **13. Clinica Nube** | Latam | Consultorios | Comprobado | No evidenciado | clinicanube.com (Oct 2026) |
| **14. 10to8** | Internacional | Genérico | Comprobado | Comprobado | 10to8.com (Oct 2026) |
| **15. TurnosWeb** | Argentina | Genérico/Salud | No evidenciado | No evidenciado | Búsqueda general (Oct 2026) |

---

## B. Matriz de Puntuación (0 a 5)

*Nota metodológica: Se evaluaron en detalle los 5 principales. Los "No evidenciados" reciben 0.*

| Sistema | Turnos (25%) | Clínica (20%) | Integraciones (15%) | Admin (15%) | Exp. Paciente (10%) | Seg. (10%) | Precio (5%) | **Total Ponderado** |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Dentalink** | 5 (1.25) | 5 (1.00) | 4 (0.60) | 5 (0.75) | 4 (0.40) | 5 (0.50) | 2 (0.10) | **4.60** |
| **TuTurno** | 5 (1.25) | 0 (0.00) | 3 (0.45) | 2 (0.30) | 5 (0.50) | 3 (0.30) | 4 (0.20) | **3.00** |
| **Clinic Cloud**| 4 (1.00) | 4 (0.80) | 3 (0.45) | 4 (0.60) | 3 (0.30) | 4 (0.40) | 3 (0.15) | **3.70** |
| **Docturno** | 4 (1.00) | 2 (0.40) | 3 (0.45) | 2 (0.30) | 4 (0.40) | 3 (0.30) | 4 (0.20) | **3.05** |
| **AgendaPro** | 4 (1.00) | 1 (0.20) | 4 (0.60) | 4 (0.60) | 4 (0.40) | 3 (0.30) | 3 (0.15) | **3.25** |

---

## C. Análisis Competitivo

1. **Estándar de mercado:** La reserva online 24/7 ya es un commodity.
2. **Diferenciadores reales:** Manejo de agendas múltiples por sillón/box.
3. **Vacíos del mercado argentino:** Las clínicas chicas no pueden pagar Dentalink, pero TuTurno no maneja matrículas ni lógica médica.
4. **Oportunidades:** Un sistema ultra-liviano, enfocado en evitar el solapamiento de turnos mediante validación transaccional (MVP propuesto).

---

## D. Recomendación Final

* **MVP Sugerido:** ABM de Pacientes, ABM de Odontólogos, Reserva de Turnos con bloqueo transaccional de solapamientos.
* **Para etapas posteriores:** Facturación y odontograma digital.

---

## Verificación de Fuentes (Lectura Manual)

1. **Dentalink:** Se verificó en planes que incluyen control de stock (excesivo para MVP).
2. **TuTurno:** El enfoque es 100% genérico (canchas deportivas).
3. **Reservio:** Configuración genérica sin validaciones clínicas.
4. **Medesk:** Artículo "Errores comunes agenda médica" confirma que el solapamiento es el problema crítico a resolver.
5. **Docturno:** Interfaz orientada al paciente, sin herramientas administrativas robustas.