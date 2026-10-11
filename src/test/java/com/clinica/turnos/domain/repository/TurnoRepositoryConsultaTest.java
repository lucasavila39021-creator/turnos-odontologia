package com.clinica.turnos.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.clinica.turnos.domain.model.EstadoTurno;
import com.clinica.turnos.domain.model.Turno;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * C-09 task 1.1 (RED→GREEN): queries derivadas paginadas de TurnoRepository.
 * Verifica que cada una filtra por su criterio y ordena por hora_inicio ASC.
 * Índices V6 reutilizados: (profesional_id,fecha), (paciente_id,fecha),
 * (fecha,estado). Sin migración nueva.
 */
@SpringBootTest
class TurnoRepositoryConsultaTest {

    @Autowired TurnoRepository turnoRepository;

    private final LocalDate hoy = LocalDate.now();
    private final LocalDate manana = hoy.plusDays(1);
    private final Pageable page = PageRequest.of(0, 20);

    @BeforeEach
    void setUp() {
        turnoRepository.deleteAll();
        // profesional 1, hoy: 3 turnos insertados desordenados
        turnoRepository.save(new Turno(10L, 1L, 100L, hoy,
                LocalTime.of(11, 0), LocalTime.of(11, 30)));
        turnoRepository.save(new Turno(10L, 1L, 100L, hoy,
                LocalTime.of(9, 0), LocalTime.of(9, 30)));
        turnoRepository.save(new Turno(11L, 1L, 100L, hoy,
                LocalTime.of(10, 0), LocalTime.of(10, 30), EstadoTurno.CANCELADO, null));
        // profesional 2, mañana
        turnoRepository.save(new Turno(10L, 2L, 100L, manana,
                LocalTime.of(9, 0), LocalTime.of(9, 30)));
    }

    @Test
    void porProfesionalYFechaOrdenaPorHora() {
        Page<Turno> p = turnoRepository
                .findByProfesionalIdAndFechaOrderByFechaAscHoraInicioAsc(1L, hoy, page);
        assertThat(p.getTotalElements()).isEqualTo(3);
        assertThat(p.getContent()).extracting(Turno::getHoraInicio).containsExactly(
                LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(11, 0));
    }

    @Test
    void porProfesionalFechaYEstadoFiltra() {
        Page<Turno> p = turnoRepository
                .findByProfesionalIdAndFechaAndEstadoOrderByFechaAscHoraInicioAsc(
                        1L, hoy, EstadoTurno.RESERVADO, page);
        assertThat(p.getTotalElements()).isEqualTo(2);
        assertThat(p.getContent()).extracting(Turno::getHoraInicio).containsExactly(
                LocalTime.of(9, 0), LocalTime.of(11, 0));
    }

    @Test
    void porPacienteYFechaOrdenaPorHora() {
        Page<Turno> p = turnoRepository
                .findByPacienteIdAndFechaOrderByFechaAscHoraInicioAsc(10L, hoy, page);
        assertThat(p.getTotalElements()).isEqualTo(2);
        assertThat(p.getContent()).extracting(Turno::getHoraInicio).containsExactly(
                LocalTime.of(9, 0), LocalTime.of(11, 0));
    }

    @Test
    void porPacienteFechaYEstadoFiltra() {
        Page<Turno> p = turnoRepository
                .findByPacienteIdAndFechaAndEstadoOrderByFechaAscHoraInicioAsc(
                        10L, hoy, EstadoTurno.RESERVADO, page);
        assertThat(p.getTotalElements()).isEqualTo(2);
    }

    @Test
    void porFechaSinDuenoDevuelveTodosOrdenados() {
        Page<Turno> p = turnoRepository
                .findByFechaOrderByFechaAscHoraInicioAsc(hoy, page);
        assertThat(p.getTotalElements()).isEqualTo(3);
        Page<Turno> e = turnoRepository
                .findByFechaAndEstadoOrderByFechaAscHoraInicioAsc(
                        hoy, EstadoTurno.CANCELADO, page);
        assertThat(e.getTotalElements()).isEqualTo(1);
    }

    @Test
    void rangosBetweenInclusivos() {
        Page<Turno> r = turnoRepository
                .findByFechaBetweenOrderByFechaAscHoraInicioAsc(hoy, manana, page);
        assertThat(r.getTotalElements()).isEqualTo(4);
        Page<Turno> rp = turnoRepository
                .findByProfesionalIdAndFechaBetweenOrderByFechaAscHoraInicioAsc(
                        1L, hoy, manana, page);
        assertThat(rp.getTotalElements()).isEqualTo(3);
        Page<Turno> rpac = turnoRepository
                .findByPacienteIdAndFechaBetweenOrderByFechaAscHoraInicioAsc(
                        10L, hoy, manana, page);
        assertThat(rpac.getTotalElements()).isEqualTo(3);
        Page<Turno> re = turnoRepository
                .findByFechaBetweenAndEstadoOrderByFechaAscHoraInicioAsc(
                        hoy, manana, EstadoTurno.RESERVADO, page);
        assertThat(re.getTotalElements()).isEqualTo(3);
        Page<Turno> rpe = turnoRepository
                .findByProfesionalIdAndFechaBetweenAndEstadoOrderByFechaAscHoraInicioAsc(
                        1L, hoy, manana, EstadoTurno.RESERVADO, page);
        assertThat(rpe.getTotalElements()).isEqualTo(2);
        Page<Turno> rpace = turnoRepository
                .findByPacienteIdAndFechaBetweenAndEstadoOrderByFechaAscHoraInicioAsc(
                        10L, hoy, manana, EstadoTurno.RESERVADO, page);
        assertThat(rpace.getTotalElements()).isEqualTo(3);
    }

    @Test
    void paginacionRespetaPageable() {
        Page<Turno> p0 = turnoRepository.findByFechaOrderByFechaAscHoraInicioAsc(
                hoy, PageRequest.of(0, 2));
        Page<Turno> p1 = turnoRepository.findByFechaOrderByFechaAscHoraInicioAsc(
                hoy, PageRequest.of(1, 2));
        assertThat(p0.getContent()).hasSize(2);
        assertThat(p1.getContent()).hasSize(1);
        assertThat(p0.getTotalElements()).isEqualTo(3);
    }
}
