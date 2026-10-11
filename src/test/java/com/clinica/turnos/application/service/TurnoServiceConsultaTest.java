package com.clinica.turnos.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.clinica.turnos.application.dto.AgendaResponse;
import com.clinica.turnos.application.dto.TurnoListResponse;
import com.clinica.turnos.application.dto.TurnoQueryParams;
import com.clinica.turnos.application.dto.TurnoResponse;
import com.clinica.turnos.application.exception.TurnoForbiddenException;
import com.clinica.turnos.application.exception.TurnoInvalidoException;
import com.clinica.turnos.domain.model.EstadoTurno;
import com.clinica.turnos.domain.model.Paciente;
import com.clinica.turnos.domain.model.Profesional;
import com.clinica.turnos.domain.model.Rol;
import com.clinica.turnos.domain.model.Turno;
import com.clinica.turnos.domain.model.Usuario;
import com.clinica.turnos.domain.repository.EspecialidadRepository;
import com.clinica.turnos.domain.repository.PacienteRepository;
import com.clinica.turnos.domain.repository.ProfesionalEspecialidadRepository;
import com.clinica.turnos.domain.repository.ProfesionalRepository;
import com.clinica.turnos.domain.repository.TurnoRepository;
import com.clinica.turnos.domain.repository.UsuarioRepository;
import com.clinica.turnos.security.CustomUserDetails;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * C-09 tasks 2.1/2.2/3.2 (RED→GREEN): listar + detalle + agenda con
 * scoping por rol en TurnoService, readOnly, TZ America/Argentina/Buenos_Aires.
 */
@SpringBootTest
class TurnoServiceConsultaTest {

    private static final ZoneId TZ = ZoneId.of("America/Argentina/Buenos_Aires");

    @Autowired TurnoService turnoService;
    @Autowired TurnoRepository turnoRepository;
    @Autowired PacienteRepository pacienteRepository;
    @Autowired ProfesionalRepository profesionalRepository;
    @Autowired EspecialidadRepository especialidadRepository;
    @Autowired ProfesionalEspecialidadRepository vinculoRepository;
    @Autowired UsuarioRepository usuarioRepository;

    private Long pacAId;
    private Long pacBId;
    private Long prof1Id;
    private Long prof2Id;
    private Long espId;
    private Long pacUserA;
    private Long pacUserB;
    private Long odontoUser1;
    private Long secreUser;
    private LocalDate dia;

    private void comoUsuario(Long uid, Rol rol) {
        CustomUserDetails cud = new CustomUserDetails(
                uid, "u" + uid, "hash", "u" + uid + "@x.local", rol, true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        cud, null, List.of(() -> "ROLE_" + rol.name())));
    }

    @BeforeEach
    void setUp() {
        turnoRepository.deleteAll();
        vinculoRepository.deleteAll();
        profesionalRepository.deleteAll();
        especialidadRepository.deleteAll();
        pacienteRepository.deleteAll();
        usuarioRepository.deleteAll();
        secreUser = usuarioRepository.save(
                new Usuario("secre", "h", "s@x.local", Rol.SECRETARIA, true)).getId();
        odontoUser1 = usuarioRepository.save(
                new Usuario("odo1", "h", "o@x.local", Rol.ODONTOLOGO, true)).getId();
        Long odontoUser2 = usuarioRepository.save(
                new Usuario("odo2", "h", "o2@x.local", Rol.ODONTOLOGO, true)).getId();
        pacUserA = usuarioRepository.save(
                new Usuario("pa", "h", "a@x.local", Rol.PACIENTE, true)).getId();
        pacUserB = usuarioRepository.save(
                new Usuario("pb", "h", "b@x.local", Rol.PACIENTE, true)).getId();
        var esp = new com.clinica.turnos.domain.model.Especialidad("Ortodoncia", null);
        espId = especialidadRepository.save(esp).getId();
        prof1Id = profesionalRepository.save(
                new Profesional("Ana", "Gómez", "M-1", null, null, odontoUser1, true)).getId();
        prof2Id = profesionalRepository.save(
                new Profesional("Luis", "Pérez", "M-2", null, null, odontoUser2, true)).getId();
        vinculoRepository.save(new com.clinica.turnos.domain.model.ProfesionalEspecialidad(
                profesionalRepository.findById(prof1Id).orElseThrow(),
                especialidadRepository.findById(espId).orElseThrow()));
        pacAId = pacienteRepository.save(
                new Paciente("Juan", "Pérez", "11111111", null, null, null, pacUserA)).getId();
        pacBId = pacienteRepository.save(
                new Paciente("María", "López", "22222222", null, null, null, pacUserB)).getId();
        dia = LocalDate.now(TZ);
        // 3 turnos: A+prof1 09:00, B+prof1 10:00 (desorden de inserción), A+prof2 11:00
        turnoRepository.save(new Turno(pacBId, prof1Id, espId, dia,
                LocalTime.of(10, 0), LocalTime.of(10, 30)));
        turnoRepository.save(new Turno(pacAId, prof1Id, espId, dia,
                LocalTime.of(9, 0), LocalTime.of(9, 30)));
        turnoRepository.save(new Turno(pacAId, prof2Id, espId, dia,
                LocalTime.of(11, 0), LocalTime.of(11, 30)));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private TurnoQueryParams diaProf(Long prof) {
        return new TurnoQueryParams(prof, dia, null, null, null);
    }

    // 2.1 — secretaria ve todos ordenados por hora
    @Test
    void secretariaVeTodosOrdenados() {
        comoUsuario(secreUser, Rol.SECRETARIA);
        TurnoListResponse r = turnoService.listar(diaProf(prof1Id), 0, 20);
        assertThat(r.totalElements()).isEqualTo(2);
        assertThat(r.content()).extracting(TurnoResponse::horaInicio).containsExactly(
                LocalTime.of(9, 0), LocalTime.of(10, 0));
        TurnoResponse primero = r.content().get(0);
        assertThat(primero.paciente().nombre()).isEqualTo("Juan");
        assertThat(primero.profesional().nombre()).isEqualTo("Ana");
        assertThat(primero.especialidad().nombre()).isEqualTo("Ortodoncia");
        assertThat(primero.estado()).isEqualTo(EstadoTurno.RESERVADO);
    }

    // 2.1 — paciente solo suyos e ignora profesional ajeno
    @Test
    void pacienteSoloSuyosIgnoraProfesional() {
        comoUsuario(pacUserA, Rol.PACIENTE);
        // pide profesional 1 pero el filtro se ignora: ve los suyos de ambos
        TurnoListResponse r = turnoService.listar(diaProf(prof1Id), 0, 20);
        assertThat(r.content()).extracting(t -> t.paciente().id())
                .containsOnly(pacAId);
        assertThat(r.totalElements()).isEqualTo(2);
    }

    // 2.1 — odontólogo solo propia + 403 ante otra
    @Test
    void odontologoSoloPropiaY403AnteOtra() {
        comoUsuario(odontoUser1, Rol.ODONTOLOGO);
        TurnoListResponse r = turnoService.listar(
                new TurnoQueryParams(null, dia, null, null, null), 0, 20);
        assertThat(r.content()).extracting(t -> t.profesional().id())
                .containsOnly(prof1Id);
        assertThat(r.totalElements()).isEqualTo(2);
        assertThatThrownBy(() -> turnoService.listar(diaProf(prof2Id), 0, 20))
                .isInstanceOf(TurnoForbiddenException.class);
    }

    // 2.1 — fecha default hoy + cap 31 días
    @Test
    void fechaDefaultHoyYCapRango() {
        comoUsuario(secreUser, Rol.SECRETARIA);
        TurnoListResponse r = turnoService.listar(
                new TurnoQueryParams(null, null, null, null, null), 0, 20);
        assertThat(r.totalElements()).isEqualTo(3);
        assertThatThrownBy(() -> turnoService.listar(new TurnoQueryParams(null, null,
                dia, dia.plusDays(32), null), 0, 20))
                .isInstanceOf(TurnoInvalidoException.class);
    }

    // 2.1 — vista semana con rango 7 días
    @Test
    void vistaSemanaRango() {
        comoUsuario(secreUser, Rol.SECRETARIA);
        turnoRepository.save(new Turno(pacAId, prof1Id, espId, dia.plusDays(3),
                LocalTime.of(9, 0), LocalTime.of(9, 30)));
        TurnoListResponse r = turnoService.listar(new TurnoQueryParams(null, null,
                dia, dia.plusDays(6), null), 0, 20);
        assertThat(r.totalElements()).isEqualTo(4);
    }

    // 2.2 — detalle ok / 404 / scoping
    @Test
    void detalleOk404YScoping() {
        Long id = turnoRepository.findByFechaOrderByFechaAscHoraInicioAsc(
                dia, org.springframework.data.domain.PageRequest.of(0, 1))
                .getContent().get(0).getId();
        comoUsuario(secreUser, Rol.SECRETARIA);
        TurnoResponse ok = turnoService.detalle(id);
        assertThat(ok.paciente().nombre()).isEqualTo("Juan");
        assertThatThrownBy(() -> turnoService.detalle(999999L))
                .isInstanceOf(NoSuchElementException.class);
        // paciente B pide detalle de turno de A → 404
        comoUsuario(pacUserB, Rol.PACIENTE);
        assertThatThrownBy(() -> turnoService.detalle(id))
                .isInstanceOf(NoSuchElementException.class);
        // odontólogo 2 pide detalle de turno de prof1 → 403
        Long odo2 = usuarioRepository.findAll().stream()
                .filter(u -> u.getUsername().equals("odo2")).findFirst().orElseThrow().getId();
        comoUsuario(odo2, Rol.ODONTOLOGO);
        assertThatThrownBy(() -> turnoService.detalle(id))
                .isInstanceOf(TurnoForbiddenException.class);
    }

    // 3.2 — agenda: ocupados solo RESERVADO, libres excluyen solo ese
    @Test
    void agendaExcluyeSoloReservado() {
        Turno cancelado = turnoRepository.save(new Turno(pacBId, prof1Id, espId, dia,
                LocalTime.of(11, 0), LocalTime.of(11, 30)));
        cancelado.setEstado(EstadoTurno.CANCELADO);
        turnoRepository.saveAndFlush(cancelado);
        comoUsuario(secreUser, Rol.SECRETARIA);
        AgendaResponse agenda = turnoService.agenda(prof1Id, dia);
        assertThat(agenda.ocupados()).hasSize(2);
        assertThat(agenda.libres()).doesNotContain(LocalTime.of(9, 0), LocalTime.of(10, 0));
        assertThat(agenda.libres()).contains(LocalTime.of(11, 0));
        assertThatThrownBy(() -> turnoService.agenda(999999L, dia))
                .isInstanceOf(NoSuchElementException.class);
    }

    // 3.2 — odontólogo agenda ajena → 403
    @Test
    void agendaOdontologoAjeno403() {
        comoUsuario(odontoUser1, Rol.ODONTOLOGO);
        assertThatThrownBy(() -> turnoService.agenda(prof2Id, dia))
                .isInstanceOf(TurnoForbiddenException.class);
        AgendaResponse propia = turnoService.agenda(prof1Id, dia);
        assertThat(propia.ocupados()).hasSize(2);
    }
}
