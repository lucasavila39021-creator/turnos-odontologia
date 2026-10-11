package com.clinica.turnos.config;

import com.clinica.turnos.domain.model.Especialidad;
import com.clinica.turnos.domain.model.EstadoTurno;
import com.clinica.turnos.domain.model.Paciente;
import com.clinica.turnos.domain.model.Profesional;
import com.clinica.turnos.domain.model.ProfesionalEspecialidad;
import com.clinica.turnos.domain.model.Rol;
import com.clinica.turnos.domain.model.Turno;
import com.clinica.turnos.domain.model.Usuario;
import com.clinica.turnos.domain.repository.EspecialidadRepository;
import com.clinica.turnos.domain.repository.PacienteRepository;
import com.clinica.turnos.domain.repository.ProfesionalEspecialidadRepository;
import com.clinica.turnos.domain.repository.ProfesionalRepository;
import com.clinica.turnos.domain.repository.TurnoRepository;
import com.clinica.turnos.domain.repository.UsuarioRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seed dev-only — solo perfil dev (C-12 D-01).
 * Crea 5 especialidades + 4 usuarios bcrypt + 1 profesional linkeado a
 * odontólogo con 2 especialidades + 1 paciente linkeado a paciente + 3 turnos
 * ejemplo (RESERVADO / CANCELADO / ATENDIDO). Idempotente con existsBy.
 * NUNCA corre en prod (@Profile dev, sin V7 con datos).
 */
@Component
@Profile("dev")
public class DevDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataInitializer.class);
    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    private final UsuarioRepository usuarioRepository;
    private final EspecialidadRepository especialidadRepository;
    private final ProfesionalRepository profesionalRepository;
    private final PacienteRepository pacienteRepository;
    private final ProfesionalEspecialidadRepository vinculoRepository;
    private final TurnoRepository turnoRepository;
    private final PasswordEncoder passwordEncoder;

    public DevDataInitializer(UsuarioRepository usuarioRepository,
                              EspecialidadRepository especialidadRepository,
                              ProfesionalRepository profesionalRepository,
                              PacienteRepository pacienteRepository,
                              ProfesionalEspecialidadRepository vinculoRepository,
                              TurnoRepository turnoRepository,
                              PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.especialidadRepository = especialidadRepository;
        this.profesionalRepository = profesionalRepository;
        this.pacienteRepository = pacienteRepository;
        this.vinculoRepository = vinculoRepository;
        this.turnoRepository = turnoRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedUsuarios();
        seedEspecialidades();
        Long odoUserId = usuarioRepository.findByUsername("odontologo")
                .map(Usuario::getId).orElse(null);
        Long pacUserId = usuarioRepository.findByUsername("paciente")
                .map(Usuario::getId).orElse(null);
        Long profId = seedProfesional(odoUserId);
        Long pacId = seedPaciente(pacUserId);
        seedVinculos(profId);
        seedTurnos(profId, pacId);
    }

    private void seedUsuarios() {
        crearUsuario("admin", "admin123", "admin@clinica.local", Rol.ADMIN);
        crearUsuario("secretaria", "secre1234", "secretaria@clinica.local", Rol.SECRETARIA);
        crearUsuario("odontologo", "odonto1234", "odontologo@clinica.local", Rol.ODONTOLOGO);
        crearUsuario("paciente", "paciente123", "paciente@clinica.local", Rol.PACIENTE);
    }

    private void crearUsuario(String username, String rawPassword, String email, Rol rol) {
        if (!usuarioRepository.existsByUsername(username)) {
            usuarioRepository.save(new Usuario(username,
                    passwordEncoder.encode(rawPassword), email, rol, true));
            log.info("seed usuario {} created", username);
        } else {
            log.info("seed usuario {} already exists", username);
        }
    }

    private void seedEspecialidades() {
        crearEspecialidad("Ortodoncia", "Aparatos y alineación dental");
        crearEspecialidad("Endodoncia", "Tratamiento de conducto");
        crearEspecialidad("Periodoncia", "Encías y soporte dental");
        crearEspecialidad("Implantología", "Implantes dentales");
        crearEspecialidad("Odontopediatría", "Odontología infantil");
    }

    private void crearEspecialidad(String nombre, String descripcion) {
        if (!especialidadRepository.existsByNombreIgnoreCase(nombre)) {
            especialidadRepository.save(new Especialidad(nombre, descripcion, true));
            log.info("seed especialidad {} created", nombre);
        } else {
            log.info("seed especialidad {} already exists", nombre);
        }
    }

    private Long seedProfesional(Long odoUserId) {
        Profesional prof;
        if (!profesionalRepository.existsByMatricula("M-SEED-01")) {
            prof = profesionalRepository.save(new Profesional("Ana", "Seed",
                    "M-SEED-01", null, "ana.seed@clinica.local", odoUserId, true));
            log.info("seed profesional M-SEED-01 created");
        } else {
            prof = profesionalRepository.findByMatricula("M-SEED-01").orElseThrow();
            if (odoUserId != null && !odoUserId.equals(prof.getUsuarioId())) {
                prof.setUsuarioId(odoUserId);
                prof = profesionalRepository.save(prof);
            }
            log.info("seed profesional M-SEED-01 already exists");
        }
        return prof.getId();
    }

    private Long seedPaciente(Long pacUserId) {
        Paciente pac;
        if (!pacienteRepository.existsByDni("99999999")) {
            pac = pacienteRepository.save(new Paciente("Juan", "Seed", "99999999",
                    null, "juan.seed@clinica.local", null, pacUserId));
            log.info("seed paciente 99999999 created");
        } else {
            pac = pacienteRepository.findByDni("99999999").orElseThrow();
            if (pacUserId != null && !pacUserId.equals(pac.getUsuarioId())) {
                pac.setUsuarioId(pacUserId);
                pac = pacienteRepository.save(pac);
            }
            log.info("seed paciente 99999999 already exists");
        }
        return pac.getId();
    }

    private void seedVinculos(Long profId) {
        vincular(profId, "Ortodoncia");
        vincular(profId, "Endodoncia");
    }

    private void vincular(Long profId, String especialidad) {
        Especialidad esp = especialidadRepository.findAll().stream()
                .filter(e -> e.getNombre().equalsIgnoreCase(especialidad))
                .findFirst().orElse(null);
        if (esp == null || profId == null) {
            return;
        }
        if (!vinculoRepository.existsByProfesionalIdAndEspecialidadId(profId, esp.getId())) {
            Profesional prof = profesionalRepository.findById(profId).orElseThrow();
            vinculoRepository.save(new ProfesionalEspecialidad(prof, esp));
            log.info("seed vinculo profesional {} especialidad {} created", profId, especialidad);
        } else {
            log.info("seed vinculo profesional {} especialidad {} already exists",
                    profId, especialidad);
        }
    }

    private void seedTurnos(Long profId, Long pacId) {
        if (profId == null || pacId == null) {
            return;
        }
        Especialidad esp = especialidadRepository.findAll().stream()
                .filter(e -> e.getNombre().equalsIgnoreCase("Ortodoncia"))
                .findFirst().orElse(null);
        if (esp == null) {
            return;
        }
        LocalDate hoy = LocalDate.now(ZONA);
        crearTurnoSiFalta(pacId, profId, esp.getId(), hoy,
                LocalTime.of(10, 0), LocalTime.of(10, 30), EstadoTurno.RESERVADO);
        crearTurnoSiFalta(pacId, profId, esp.getId(), hoy.minusDays(1),
                LocalTime.of(11, 0), LocalTime.of(11, 30), EstadoTurno.CANCELADO);
        crearTurnoSiFalta(pacId, profId, esp.getId(), hoy.minusDays(1),
                LocalTime.of(9, 0), LocalTime.of(9, 30), EstadoTurno.ATENDIDO);
    }

    private void crearTurnoSiFalta(Long pacId, Long profId, Long espId,
                                   LocalDate fecha, LocalTime inicio, LocalTime fin,
                                   EstadoTurno estado) {
        boolean existe = turnoRepository
                .findByProfesionalIdAndFechaAndEstadoOrderByHoraInicioAsc(
                        profId, fecha, estado).stream()
                .anyMatch(t -> t.getHoraInicio().equals(inicio)
                        && t.getPacienteId().equals(pacId));
        if (!existe) {
            Turno turno = new Turno(pacId, profId, espId, fecha, inicio, fin, estado, null);
            if (estado == EstadoTurno.CANCELADO) {
                turno.setMotivoCancelacion("seed ejemplo");
            }
            turnoRepository.save(turno);
            log.info("seed turno {} {} {} created", fecha, inicio, estado);
        } else {
            log.info("seed turno {} {} {} already exists", fecha, inicio, estado);
        }
    }
}
