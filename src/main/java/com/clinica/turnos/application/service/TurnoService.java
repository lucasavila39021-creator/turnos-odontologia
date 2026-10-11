package com.clinica.turnos.application.service;

import com.clinica.turnos.application.dto.TurnoCreateRequest;
import com.clinica.turnos.application.dto.AgendaResponse;
import com.clinica.turnos.application.dto.TurnoListResponse;
import com.clinica.turnos.application.dto.TurnoQueryParams;
import com.clinica.turnos.application.dto.TurnoResponse;
import com.clinica.turnos.application.exception.TurnoForbiddenException;
import com.clinica.turnos.application.exception.TurnoInvalidoException;
import com.clinica.turnos.application.exception.TurnoOcupadoException;
import com.clinica.turnos.domain.model.Especialidad;
import com.clinica.turnos.domain.model.EstadoTurno;
import com.clinica.turnos.domain.model.Paciente;
import com.clinica.turnos.domain.model.Profesional;
import com.clinica.turnos.domain.model.Rol;
import com.clinica.turnos.domain.model.Turno;
import com.clinica.turnos.domain.repository.EspecialidadRepository;
import com.clinica.turnos.domain.repository.PacienteRepository;
import com.clinica.turnos.domain.repository.ProfesionalEspecialidadRepository;
import com.clinica.turnos.domain.repository.ProfesionalRepository;
import com.clinica.turnos.domain.repository.TurnoRepository;
import com.clinica.turnos.security.AuthzService;
import com.clinica.turnos.security.CustomUserDetails;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reserva de turnos (C-08, RN-TU-01..05/09, RN-PR-05).
 * TX SERIALIZABLE + lock de fila del profesional (D-02) + serializador JVM
 * por profesional: el segundo creador concurrente del mismo hueco espera a
 * que el primero confirme, re-lee el solape y recibe 409.
 * En MySQL los gap-locks (REPEATABLE READ) cierran la carrera entre nodos;
 * ante deadlock 1213 el cliente reintenta con backoff. H2 no da gap-locks
 * reales: el serializador JVM garantiza el 1-gana en un solo nodo.
 *
 * <p>Orden fijo D-05: ownership → 404 FKs → RN-PR-05 → hora_fin →
 * RN-TU-03/04 → solapes → save. Auth primero para no filtrar fichas ajenas.
 */
@Service
public class TurnoService {

    private static final Logger log = LoggerFactory.getLogger(TurnoService.class);

    /** Zona fija RN-GL-02: nunca systemDefault. */
    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final int MAX_RANGO_DIAS = 31;
    private static final int MAX_SIZE = 100;

    private final TurnoRepository turnoRepository;
    private final PacienteRepository pacienteRepository;
    private final ProfesionalRepository profesionalRepository;
    private final EspecialidadRepository especialidadRepository;
    private final ProfesionalEspecialidadRepository vinculoRepository;
    private final AuthzService authzService;
    private final TransactionTemplate serializableTx;

    /**
     * Serializador por profesional (un solo nodo): la TX arranca DESPUÉS de
     * adquirir el lock, así el segundo contendiente ve la fila confirmada
     * del primero y el solape da 409. Sin anidamiento → sin deadlock.
     */
    private final ConcurrentHashMap<Long, Object> locks = new ConcurrentHashMap<>();

    private final int duracionMin;
    private final LocalTime horarioDesde;
    private final LocalTime horarioHasta;

    public TurnoService(TurnoRepository turnoRepository,
                        PacienteRepository pacienteRepository,
                        ProfesionalRepository profesionalRepository,
                        EspecialidadRepository especialidadRepository,
                        ProfesionalEspecialidadRepository vinculoRepository,
                        AuthzService authzService,
                        PlatformTransactionManager transactionManager,
                        @Value("${app.turno-duracion-min:30}") int duracionMin,
                        @Value("${app.horario-desde:08:00}") String horarioDesde,
                        @Value("${app.horario-hasta:18:00}") String horarioHasta) {
        this.turnoRepository = turnoRepository;
        this.pacienteRepository = pacienteRepository;
        this.profesionalRepository = profesionalRepository;
        this.especialidadRepository = especialidadRepository;
        this.vinculoRepository = vinculoRepository;
        this.authzService = authzService;
        this.serializableTx = new TransactionTemplate(transactionManager);
        this.serializableTx.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
        this.duracionMin = duracionMin;
        this.horarioDesde = LocalTime.parse(horarioDesde);
        this.horarioHasta = LocalTime.parse(horarioHasta);
    }

    public TurnoResponse crear(TurnoCreateRequest req) {
        // 1. Ownership antes de validar negocio (no filtrar existencia ajena).
        if (authzService.hasRole("PACIENTE")
                && !authzService.isPropioPaciente(req.pacienteId())) {
            throw new TurnoForbiddenException();
        }
        Object lock = locks.computeIfAbsent(req.profesionalId(), k -> new Object());
        synchronized (lock) {
            return serializableTx.execute(status -> doCrear(req));
        }
    }

    private TurnoResponse doCrear(TurnoCreateRequest req) {

        // 2. FKs existen → 404 (dentro de la misma TX). El profesional se lee
        // con lock de escritura: serializa reservas del mismo hueco (el
        // segundo espera y luego ve el solape → 409).
        Paciente paciente = pacienteRepository.findById(req.pacienteId())
                .orElseThrow(() -> new NoSuchElementException(
                        "Paciente no encontrado: " + req.pacienteId()));
        Profesional profesional = profesionalRepository.findByIdForUpdate(req.profesionalId())
                .orElseThrow(() -> new NoSuchElementException(
                        "Profesional no encontrado: " + req.profesionalId()));
        Especialidad especialidad = especialidadRepository.findById(req.especialidadId())
                .orElseThrow(() -> new NoSuchElementException(
                        "Especialidad no encontrada: " + req.especialidadId()));
        if (!profesional.isActivo()) {
            throw new TurnoInvalidoException("profesional_id", "el profesional no está activo");
        }
        if (!especialidad.isActiva()) {
            throw new TurnoInvalidoException("especialidad_id", "la especialidad no está activa");
        }

        // 3. RN-PR-05: la especialidad pertenece al profesional.
        if (!vinculoRepository.existsByProfesionalIdAndEspecialidadId(
                profesional.getId(), especialidad.getId())) {
            throw new TurnoInvalidoException(
                    "especialidad_id", "la especialidad no pertenece al profesional");
        }

        // 4. hora_fin calculada en servicio (RN-TU-02), nunca del cliente.
        LocalTime horaFin = req.horaInicio().plusMinutes(duracionMin);

        // 5. RN-TU-03: dentro de APP_HORARIO_DESDE/HASTA.
        if (req.horaInicio().isBefore(horarioDesde) || horaFin.isAfter(horarioHasta)) {
            throw new TurnoInvalidoException(
                    "hora_inicio", "fuera del horario de atención");
        }

        // 6. RN-TU-04: no en el pasado (TZ America/Argentina/Buenos_Aires vía JVM).
        LocalDateTime ahora = LocalDateTime.now();
        if (req.fecha().isBefore(ahora.toLocalDate())
                || (req.fecha().isEqual(ahora.toLocalDate())
                    && !req.horaInicio().isAfter(ahora.toLocalTime()))) {
            throw new TurnoInvalidoException("fecha", "no se puede reservar en el pasado");
        }

        // 7. Solapes RN-TU-01 (profesional) y RN-TU-05 (paciente) → 409.
        try {
            if (turnoRepository.existsSolapeProfesional(
                    profesional.getId(), req.fecha(), req.horaInicio(), horaFin,
                    EstadoTurno.RESERVADO)) {
                throw new TurnoOcupadoException(calcularSugeridos(
                        profesional.getId(), req.fecha(), req.horaInicio()));
            }
            if (turnoRepository.existsSolapePaciente(
                    paciente.getId(), req.fecha(), req.horaInicio(), horaFin,
                    EstadoTurno.RESERVADO)) {
                throw new TurnoOcupadoException(calcularSugeridos(
                        profesional.getId(), req.fecha(), req.horaInicio()));
            }
        } catch (ConcurrencyFailureException ex) {
            // Carrera perdida bajo SERIALIZABLE (MySQL 1213): re-leer y dar 409.
            log.info("turno carrera perdida prof={} fecha={}", profesional.getId(), req.fecha());
            throw new TurnoOcupadoException(calcularSugeridos(
                    profesional.getId(), req.fecha(), req.horaInicio()));
        }

        Turno entity = new Turno(
                paciente.getId(), profesional.getId(), especialidad.getId(),
                req.fecha(), req.horaInicio(), horaFin,
                EstadoTurno.RESERVADO, currentUserId());
        Turno saved;
        try {
            saved = turnoRepository.saveAndFlush(entity);
        } catch (ConcurrencyFailureException ex) {
            log.info("turno commit en carrera prof={} fecha={}", profesional.getId(), req.fecha());
            throw new TurnoOcupadoException(calcularSugeridos(
                    profesional.getId(), req.fecha(), req.horaInicio()));
        }
        log.info("op=crear-turno id={} prof={} fecha={} inicio={}",
                saved.getId(), profesional.getId(), req.fecha(), req.horaInicio());
        return TurnoResponse.from(saved, paciente, profesional, especialidad);
    }

    /**
     * Hasta 3 huecos libres próximos (D-04): step de duración desde la hora
     * pedida hasta HASTA, excluyendo solapados. Sin libres → lista vacía.
     */
    private List<String> calcularSugeridos(Long profesionalId, LocalDate fecha, LocalTime desde) {
        List<String> sugeridos = new ArrayList<>(3);
        LocalDate hoy = LocalDate.now();
        LocalTime ahora = LocalTime.now();
        LocalTime candidato = desde;
        while (sugeridos.size() < 3 && !candidato.plusMinutes(duracionMin).isAfter(horarioHasta)) {
            LocalTime fin = candidato.plusMinutes(duracionMin);
            boolean futuro = fecha.isAfter(hoy)
                    || (fecha.isEqual(hoy) && candidato.isAfter(ahora));
            if (futuro && !turnoRepository.existsSolapeProfesional(
                    profesionalId, fecha, candidato, fin, EstadoTurno.RESERVADO)) {
                sugeridos.add(candidato.toString());
            }
            candidato = candidato.plusMinutes(duracionMin);
        }
        return sugeridos;
    }

    /**
     * Cancelar turno (C-10 D-01/D-02/D-04, RN-TU-06/07).
     * Orden: 404 inexistente → ownership 403 → estado 400.
     * Solo RESERVADO → CANCELADO; persiste motivo (trim o null) y updated_at
     * vía JPA; created_by inmutable. CANCELADO libera hueco (solapes solo
     * miran RESERVADO, sin cambio en repository).
     */
    @Transactional
    public TurnoResponse cancelar(Long id, String motivo) {
        Turno turno = turnoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Turno no encontrado: " + id));
        if (authzService.hasRole("PACIENTE")
                && !authzService.isPropioPaciente(turno.getPacienteId())) {
            throw new TurnoForbiddenException("No tiene permiso para cancelar este turno");
        }
        if (authzService.hasRole("ODONTOLOGO")) {
            throw new TurnoForbiddenException("No tiene permiso para cancelar este turno");
        }
        if (turno.getEstado() != EstadoTurno.RESERVADO) {
            throw new TurnoInvalidoException("estado", "No se puede cancelar el turno en estado "
                    + turno.getEstado());
        }
        String limpio = motivo != null && !motivo.isBlank() ? motivo.trim() : null;
        turno.setEstado(EstadoTurno.CANCELADO);
        turno.setMotivoCancelacion(limpio);
        Turno saved = turnoRepository.saveAndFlush(turno);
        log.info("op=cancelar id={} estado={}", saved.getId(), saved.getEstado());
        return mapearTurnos(List.of(saved)).get(0);
    }

    /**
     * Marcar atendido (C-10 D-01/D-04, US-034).
     * Orden: 404 → ownership 403 → estado 400. Solo RESERVADO → ATENDIDO.
     */
    @Transactional
    public TurnoResponse atendido(Long id) {
        Turno turno = turnoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Turno no encontrado: " + id));
        if (authzService.hasRole("PACIENTE")) {
            throw new TurnoForbiddenException("No tiene permiso para marcar atendido este turno");
        }
        if (authzService.hasRole("ODONTOLOGO")
                && !authzService.isPropioProfesional(turno.getProfesionalId())) {
            throw new TurnoForbiddenException("No tiene permiso para marcar atendido este turno");
        }
        if (turno.getEstado() != EstadoTurno.RESERVADO) {
            throw new TurnoInvalidoException("estado", "No se puede marcar como atendido el turno en estado "
                    + turno.getEstado());
        }
        turno.setEstado(EstadoTurno.ATENDIDO);
        Turno saved = turnoRepository.saveAndFlush(turno);
        log.info("op=atendido id={} estado={}", saved.getId(), saved.getEstado());
        return mapearTurnos(List.of(saved)).get(0);
    }

    /**
     * Reprogramar (C-10 D-03, RN-TU-08, Flujo 5): cancelar original + crear
     * nuevo en una sola TX SERIALIZABLE. Orden D-03: 404 → RBAC (controller,
     * SECRETARIA/ADMIN) → RN-TU-03/04 destino → cancelar-en-TX + validar
     * solapes + crear. Ante 409 rollback total: original intacto RESERVADO.
     * Reutiliza existsSolape*, calcularSugeridos y locks de C-08.
     */
    public TurnoResponse reprogramar(Long id, LocalDate nuevaFecha, LocalTime nuevaHora) {
        Turno original = turnoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Turno no encontrado: " + id));
        Object lock = locks.computeIfAbsent(original.getProfesionalId(), k -> new Object());
        synchronized (lock) {
            return serializableTx.execute(status -> doReprogramar(id, nuevaFecha, nuevaHora));
        }
    }

    private TurnoResponse doReprogramar(Long id, LocalDate nuevaFecha, LocalTime nuevaHora) {
        Turno original = turnoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Turno no encontrado: " + id));
        if (original.getEstado() != EstadoTurno.RESERVADO) {
            throw new TurnoInvalidoException("estado", "No se puede reprogramar el turno en estado "
                    + original.getEstado());
        }
        // Lock de fila del profesional: serializa carreras en destino (C-08 D-02).
        profesionalRepository.findByIdForUpdate(original.getProfesionalId())
                .orElseThrow(() -> new NoSuchElementException(
                        "Profesional no encontrado: " + original.getProfesionalId()));

        // RN-TU-03: destino dentro de horario; RN-TU-04: no en el pasado.
        LocalTime horaFin = nuevaHora.plusMinutes(duracionMin);
        if (nuevaHora.isBefore(horarioDesde) || horaFin.isAfter(horarioHasta)) {
            throw new TurnoInvalidoException(
                    "nueva_hora_inicio", "fuera del horario de atención");
        }
        LocalDateTime ahora = LocalDateTime.now();
        if (nuevaFecha.isBefore(ahora.toLocalDate())
                || (nuevaFecha.isEqual(ahora.toLocalDate())
                    && !nuevaHora.isAfter(ahora.toLocalTime()))) {
            throw new TurnoInvalidoException("nueva_fecha", "no se puede reprogramar en el pasado");
        }

        // Cancelar-primero-en-memoria + flush: el original deja de bloquear
        // (queries solo miran RESERVADO) y el mismo-hueco da 200 con dos filas.
        // Si la validación/creación falla, la TX hace rollback → intacto.
        original.setEstado(EstadoTurno.CANCELADO);
        original.setMotivoCancelacion("reprogramado");
        turnoRepository.saveAndFlush(original);

        try {
            if (turnoRepository.existsSolapeProfesional(
                    original.getProfesionalId(), nuevaFecha, nuevaHora, horaFin,
                    EstadoTurno.RESERVADO)) {
                throw new TurnoOcupadoException(calcularSugeridos(
                        original.getProfesionalId(), nuevaFecha, nuevaHora));
            }
            if (turnoRepository.existsSolapePaciente(
                    original.getPacienteId(), nuevaFecha, nuevaHora, horaFin,
                    EstadoTurno.RESERVADO)) {
                throw new TurnoOcupadoException(calcularSugeridos(
                        original.getProfesionalId(), nuevaFecha, nuevaHora));
            }
        } catch (ConcurrencyFailureException ex) {
            log.info("reprogramar carrera perdida orig={}", id);
            throw new TurnoOcupadoException(calcularSugeridos(
                    original.getProfesionalId(), nuevaFecha, nuevaHora));
        }

        Turno nuevo = new Turno(
                original.getPacienteId(), original.getProfesionalId(),
                original.getEspecialidadId(), nuevaFecha, nuevaHora, horaFin,
                EstadoTurno.RESERVADO, currentUserId());
        Turno saved;
        try {
            saved = turnoRepository.saveAndFlush(nuevo);
        } catch (ConcurrencyFailureException ex) {
            log.info("reprogramar commit en carrera orig={}", id);
            throw new TurnoOcupadoException(calcularSugeridos(
                    original.getProfesionalId(), nuevaFecha, nuevaHora));
        }
        Paciente paciente = pacienteRepository.findById(saved.getPacienteId())
                .orElseThrow(() -> new NoSuchElementException(
                        "Paciente no encontrado: " + saved.getPacienteId()));
        Profesional profesional = profesionalRepository.findById(saved.getProfesionalId())
                .orElseThrow(() -> new NoSuchElementException(
                        "Profesional no encontrado: " + saved.getProfesionalId()));
        Especialidad especialidad = especialidadRepository.findById(saved.getEspecialidadId())
                .orElseThrow(() -> new NoSuchElementException(
                        "Especialidad no encontrada: " + saved.getEspecialidadId()));
        log.info("op=reprogramar id={} nuevo={} fecha={} inicio={}",
                id, saved.getId(), nuevaFecha, nuevaHora);
        return TurnoResponse.from(saved, paciente, profesional, especialidad);
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails cud) {
            return cud.getId();
        }
        return null;
    }

    private Rol currentRol() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails cud) {
            return cud.getRol();
        }
        return null;
    }

    /**
     * Lista paginada con scoping por rol (C-09 D-01/D-02), readOnly.
     * PACIENTE fuerza su ficha e ignora {@code profesional} (200 filtrado);
     * ODONTOLOGO fuerza su ficha (otro → 403); SECRETARIA/ADMIN sin filtro.
     * Sin fecha/desde/hasta → hoy en America/Argentina/Buenos_Aires.
     * Rango acotado a 31 días → 400 (evita full scan).
     */
    @Transactional(readOnly = true)
    public TurnoListResponse listar(TurnoQueryParams params, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_SIZE));
        Rol rol = currentRol();
        Long pacienteForzado = null;
        Long profesionalForzado = null;
        if (rol == Rol.PACIENTE) {
            pacienteForzado = pacienteRepository.findByUsuarioId(currentUserId())
                    .map(Paciente::getId).orElse(null);
            if (pacienteForzado == null) {
                return TurnoListResponse.vacia(pageable.getPageNumber(), pageable.getPageSize());
            }
        } else if (rol == Rol.ODONTOLOGO) {
            profesionalForzado = profesionalRepository.findByUsuarioId(currentUserId())
                    .map(Profesional::getId).orElse(null);
            if (profesionalForzado == null) {
                throw new TurnoForbiddenException("No tiene agenda propia asignada");
            }
            if (params.profesionalId() != null
                    && !params.profesionalId().equals(profesionalForzado)) {
                throw new TurnoForbiddenException("No tiene permiso para ver otra agenda");
            }
        }
        Long profEfectivo = profesionalForzado != null
                ? profesionalForzado : params.profesionalId();

        Page<Turno> pagina;
        if (params.desde() != null || params.hasta() != null) {
            if (params.desde() == null || params.hasta() == null) {
                throw new TurnoInvalidoException("fecha", "informar desde y hasta para el rango");
            }
            if (params.hasta().isBefore(params.desde())) {
                throw new TurnoInvalidoException("fecha", "hasta anterior a desde");
            }
            if (ChronoUnit.DAYS.between(params.desde(), params.hasta()) > MAX_RANGO_DIAS) {
                throw new TurnoInvalidoException("fecha", "rango máximo de 31 días");
            }
            pagina = queryRango(pacienteForzado, profEfectivo,
                    params.desde(), params.hasta(), params.estado(), pageable);
        } else {
            LocalDate fecha = params.fecha() != null ? params.fecha() : LocalDate.now(ZONA);
            pagina = queryDia(pacienteForzado, profEfectivo, fecha, params.estado(), pageable);
        }
        List<TurnoResponse> dtos = mapearTurnos(pagina.getContent());
        return TurnoListResponse.from(new PageImpl<>(dtos, pageable, pagina.getTotalElements()));
    }

    private Page<Turno> queryDia(Long pacienteId, Long profesionalId, LocalDate fecha,
                                 EstadoTurno estado, Pageable pageable) {
        if (pacienteId != null) {
            return estado != null
                    ? turnoRepository.findByPacienteIdAndFechaAndEstadoOrderByFechaAscHoraInicioAsc(
                            pacienteId, fecha, estado, pageable)
                    : turnoRepository.findByPacienteIdAndFechaOrderByFechaAscHoraInicioAsc(
                            pacienteId, fecha, pageable);
        }
        if (profesionalId != null) {
            return estado != null
                    ? turnoRepository.findByProfesionalIdAndFechaAndEstadoOrderByFechaAscHoraInicioAsc(
                            profesionalId, fecha, estado, pageable)
                    : turnoRepository.findByProfesionalIdAndFechaOrderByFechaAscHoraInicioAsc(
                            profesionalId, fecha, pageable);
        }
        return estado != null
                ? turnoRepository.findByFechaAndEstadoOrderByFechaAscHoraInicioAsc(
                        fecha, estado, pageable)
                : turnoRepository.findByFechaOrderByFechaAscHoraInicioAsc(fecha, pageable);
    }

    private Page<Turno> queryRango(Long pacienteId, Long profesionalId,
                                   LocalDate desde, LocalDate hasta,
                                   EstadoTurno estado, Pageable pageable) {
        if (pacienteId != null) {
            return estado != null
                    ? turnoRepository.findByPacienteIdAndFechaBetweenAndEstadoOrderByFechaAscHoraInicioAsc(
                            pacienteId, desde, hasta, estado, pageable)
                    : turnoRepository.findByPacienteIdAndFechaBetweenOrderByFechaAscHoraInicioAsc(
                            pacienteId, desde, hasta, pageable);
        }
        if (profesionalId != null) {
            return estado != null
                    ? turnoRepository.findByProfesionalIdAndFechaBetweenAndEstadoOrderByFechaAscHoraInicioAsc(
                            profesionalId, desde, hasta, estado, pageable)
                    : turnoRepository.findByProfesionalIdAndFechaBetweenOrderByFechaAscHoraInicioAsc(
                            profesionalId, desde, hasta, pageable);
        }
        return estado != null
                ? turnoRepository.findByFechaBetweenAndEstadoOrderByFechaAscHoraInicioAsc(
                        desde, hasta, estado, pageable)
                : turnoRepository.findByFechaBetweenOrderByFechaAscHoraInicioAsc(
                        desde, hasta, pageable);
    }

    /**
     * Detalle con scoping post-lectura (C-09 D-03): inexistente → 404;
     * paciente ajeno → 404 (no revela existencia); odontólogo ajeno → 403.
     */
    @Transactional(readOnly = true)
    public TurnoResponse detalle(Long id) {
        Turno turno = turnoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Turno no encontrado: " + id));
        Rol rol = currentRol();
        if (rol == Rol.PACIENTE) {
            Long ficha = pacienteRepository.findByUsuarioId(currentUserId())
                    .map(Paciente::getId).orElse(null);
            if (ficha == null || !ficha.equals(turno.getPacienteId())) {
                throw new NoSuchElementException("Turno no encontrado: " + id);
            }
        } else if (rol == Rol.ODONTOLOGO) {
            Long ficha = profesionalRepository.findByUsuarioId(currentUserId())
                    .map(Profesional::getId).orElse(null);
            if (ficha == null || !ficha.equals(turno.getProfesionalId())) {
                throw new TurnoForbiddenException("No tiene permiso para ver este turno");
            }
        }
        return mapearTurnos(List.of(turno)).get(0);
    }

    /**
     * Agenda diaria (C-09 D-04), readOnly: 404 profesional inexistente;
     * ODONTOLOGO solo la propia (otra → 403). Ocupados = RESERVADO vía
     * query existente; libres vía HuecosCalculator puro.
     */
    @Transactional(readOnly = true)
    public AgendaResponse agenda(Long profesionalId, LocalDate fecha) {
        profesionalRepository.findById(profesionalId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Profesional no encontrado: " + profesionalId));
        if (currentRol() == Rol.ODONTOLOGO
                && !authzService.isPropioProfesional(profesionalId)) {
            throw new TurnoForbiddenException("No tiene permiso para ver esta agenda");
        }
        LocalDate dia = fecha != null ? fecha : LocalDate.now(ZONA);
        List<Turno> ocupados = turnoRepository
                .findByProfesionalIdAndFechaAndEstadoOrderByHoraInicioAsc(
                        profesionalId, dia, EstadoTurno.RESERVADO);
        List<TurnoResponse> ocupadosDto = mapearTurnos(ocupados);
        List<HuecosCalculator.Ocupado> intervalos = ocupados.stream()
                .map(t -> new HuecosCalculator.Ocupado(t.getHoraInicio(), t.getHoraFin()))
                .toList();
        List<LocalTime> libres = HuecosCalculator.libres(
                horarioDesde, horarioHasta, duracionMin, intervalos);
        return new AgendaResponse(dia, ocupadosDto, libres);
    }

    /**
     * Historial de la ficha del paciente (C-11): todos los estados, más
     * recientes primero. Solo lectura; el scoping lo aplica el MVC
     * controller con el mismo @PreAuthorize que GET /api/pacientes/{id}
     * (staff o dueño de la ficha).
     */
    @Transactional(readOnly = true)
    public List<TurnoResponse> historialPorPaciente(Long pacienteId) {
        List<Turno> turnos = turnoRepository.findByPacienteIdOrderByFechaDescHoraInicioDesc(pacienteId);
        return mapearTurnos(turnos);
    }

    /**
     * Mapeo batch sin N+1: una query por entidad referenciada por página.
     */
    private List<TurnoResponse> mapearTurnos(List<Turno> turnos) {
        if (turnos.isEmpty()) {
            return List.of();
        }
        Map<Long, Paciente> pacientes = pacienteRepository.findAllById(turnos.stream()
                        .map(Turno::getPacienteId).collect(Collectors.toSet())).stream()
                        .collect(Collectors.toMap(Paciente::getId, Function.identity()));
        Map<Long, Profesional> profesionales = profesionalRepository.findAllById(turnos.stream()
                        .map(Turno::getProfesionalId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Profesional::getId, Function.identity()));
        Map<Long, Especialidad> especialidades = especialidadRepository.findAllById(turnos.stream()
                        .map(Turno::getEspecialidadId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Especialidad::getId, Function.identity()));
        return turnos.stream()
                .map(t -> TurnoResponse.from(
                        t,
                        requireRef(pacientes.get(t.getPacienteId()), "Paciente", t.getPacienteId()),
                        requireRef(profesionales.get(t.getProfesionalId()),
                                "Profesional", t.getProfesionalId()),
                        requireRef(especialidades.get(t.getEspecialidadId()),
                                "Especialidad", t.getEspecialidadId())))
                .toList();
    }

    private static <T> T requireRef(T ref, String tipo, Long id) {
        if (ref == null) {
            throw new NoSuchElementException(tipo + " no encontrado: " + id);
        }
        return ref;
    }
}
