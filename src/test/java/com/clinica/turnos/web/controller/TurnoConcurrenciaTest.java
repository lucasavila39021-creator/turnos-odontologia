package com.clinica.turnos.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.clinica.turnos.application.dto.CreateEspecialidadRequest;
import com.clinica.turnos.application.dto.CreateProfesionalRequest;
import com.clinica.turnos.application.dto.TurnoCreateRequest;
import com.clinica.turnos.application.exception.TurnoOcupadoException;
import com.clinica.turnos.application.service.EspecialidadService;
import com.clinica.turnos.application.service.ProfesionalService;
import com.clinica.turnos.application.service.TurnoService;
import com.clinica.turnos.domain.model.Paciente;
import com.clinica.turnos.domain.model.Rol;
import com.clinica.turnos.domain.model.Usuario;
import com.clinica.turnos.domain.repository.EspecialidadRepository;
import com.clinica.turnos.domain.repository.PacienteRepository;
import com.clinica.turnos.domain.repository.ProfesionalEspecialidadRepository;
import com.clinica.turnos.domain.repository.ProfesionalRepository;
import com.clinica.turnos.domain.repository.TurnoRepository;
import com.clinica.turnos.domain.repository.UsuarioRepository;
import com.clinica.turnos.security.CustomUserDetails;
import com.clinica.turnos.security.RateLimitService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Concurrencia de reserva sobre H2 (C-08 task 3.2): 2 creates simultáneos del
 * mismo hueco → solo 1 gana, el otro recibe 409 (TurnoOcupadoException).
 *
 * <p>Nota MySQL: en producción la TX SERIALIZABLE + gap-locks (REPEATABLE READ)
 * cierra la carrera entre nodos; el lock de fila del profesional
 * ({@code findByIdForUpdate}) serializa a los contendientes del mismo hueco.
 * Ante deadlock 1213 el servicio traduce a 409 y el cliente reintenta con
 * backoff. H2 no da gap-locks reales: este test valida la lógica (1-gana),
 * no el aislamiento MySQL.
 */
@SpringBootTest
class TurnoConcurrenciaTest {

    @Autowired TurnoService turnoService;
    @Autowired TurnoRepository turnoRepository;
    @Autowired ProfesionalService profesionalService;
    @Autowired ProfesionalRepository profesionalRepository;
    @Autowired ProfesionalEspecialidadRepository vinculoRepository;
    @Autowired EspecialidadService especialidadService;
    @Autowired EspecialidadRepository especialidadRepository;
    @Autowired PacienteRepository pacienteRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired RateLimitService rateLimitService;

    private Long ortodonciaId;
    private Long profId;
    private Long pacAId;
    private Long pacBId;
    private Long secreId;

    @BeforeEach
    void setUp() {
        rateLimitService.clear();
        SecurityContextHolder.clearContext();
        turnoRepository.deleteAll();
        vinculoRepository.deleteAll();
        profesionalRepository.deleteAll();
        especialidadRepository.deleteAll();
        pacienteRepository.deleteAll();
        usuarioRepository.deleteAll();
        secreId = usuarioRepository.save(new Usuario("secre1",
                passwordEncoder.encode("secre1234"), "secre@clinica.local",
                Rol.SECRETARIA, true)).getId();
        ortodonciaId = especialidadService.crear(
                new CreateEspecialidadRequest("Ortodoncia", null)).id();
        profId = profesionalService.crear(new CreateProfesionalRequest(
                "Ana", "Gómez", "M-300", null, null, null, List.of(ortodonciaId))).id();
        pacAId = pacienteRepository.save(new Paciente("Juan", "Pérez", "11111111",
                null, null, null, null)).getId();
        pacBId = pacienteRepository.save(new Paciente("María", "López", "22222222",
                null, null, null, null)).getId();
    }

    private void autenticarComoSecretaria() {
        CustomUserDetails cud = new CustomUserDetails(secreId, "secre1", "x",
                "secre@clinica.local", Rol.SECRETARIA, true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(cud, null, cud.getAuthorities()));
    }

    @Test
    void dosReservasSimultaneasSoloUnaGana() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(30);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch listos = new CountDownLatch(2);
        CountDownLatch largada = new CountDownLatch(1);
        AtomicInteger creados = new AtomicInteger();
        AtomicInteger conflictos = new AtomicInteger();

        var tarea = (java.util.concurrent.Callable<Void>) () -> {
            autenticarComoSecretaria();
            listos.countDown();
            assertThat(largada.await(10, TimeUnit.SECONDS)).isTrue();
            try {
                turnoService.crear(new TurnoCreateRequest(pacAId, profId, ortodonciaId,
                        fecha, LocalTime.of(10, 0)));
                creados.incrementAndGet();
            } catch (TurnoOcupadoException ex) {
                assertThat(ex.getSugeridos()).isNotNull();
                conflictos.incrementAndGet();
            } finally {
                SecurityContextHolder.clearContext();
            }
            return null;
        };

        Future<Void> f1 = pool.submit(tarea);
        // El segundo hilo reserva para OTRO paciente el mismo hueco del profesional.
        Future<Void> f2 = pool.submit((java.util.concurrent.Callable<Void>) () -> {
            autenticarComoSecretaria();
            listos.countDown();
            assertThat(largada.await(10, TimeUnit.SECONDS)).isTrue();
            try {
                turnoService.crear(new TurnoCreateRequest(pacBId, profId, ortodonciaId,
                        fecha, LocalTime.of(10, 0)));
                creados.incrementAndGet();
            } catch (TurnoOcupadoException ex) {
                conflictos.incrementAndGet();
            } finally {
                SecurityContextHolder.clearContext();
            }
            return null;
        });

        assertThat(listos.await(10, TimeUnit.SECONDS)).isTrue();
        largada.countDown();
        f1.get(30, TimeUnit.SECONDS);
        f2.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(creados.get()).isEqualTo(1);
        assertThat(conflictos.get()).isEqualTo(1);
        assertThat(turnoRepository.count()).isEqualTo(1);
    }
}
