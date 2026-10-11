package com.clinica.turnos.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * C-09 task 3.1 (RED→GREEN): HuecosCalculator puro, sin IO.
 * Slots [DESDE, HASTA-duracion] con step; excluye solapados RESERVADO con
 * semántica de bordes C-08 (adyacente fin==inicio NO solapa).
 */
class HuecosCalculatorTest {

    private static final LocalTime DESDE = LocalTime.of(8, 0);
    private static final LocalTime HASTA = LocalTime.of(18, 0);

    @Test
    void excluyeReservado() {
        List<HuecosCalculator.Ocupado> ocupados = List.of(
                new HuecosCalculator.Ocupado(LocalTime.of(10, 0), LocalTime.of(10, 30)));
        List<LocalTime> libres = HuecosCalculator.libres(DESDE, HASTA, 30, ocupados);
        assertThat(libres).doesNotContain(LocalTime.of(10, 0));
        assertThat(libres).contains(LocalTime.of(9, 30), LocalTime.of(10, 30));
        // 08:00→18:00 step 30 = 20 slots menos 1 ocupado
        assertThat(libres).hasSize(19);
    }

    @Test
    void adyacenteNoSolapa() {
        List<HuecosCalculator.Ocupado> ocupados = List.of(
                new HuecosCalculator.Ocupado(LocalTime.of(10, 0), LocalTime.of(10, 30)));
        List<LocalTime> libres = HuecosCalculator.libres(DESDE, HASTA, 30, ocupados);
        // slot 10:30→11:00 empieza donde termina el ocupado → libre
        assertThat(libres).contains(LocalTime.of(10, 30));
        // slot 09:30→10:00 termina donde empieza el ocupado → libre
        assertThat(libres).contains(LocalTime.of(9, 30));
    }

    @Test
    void sinOcupadosDevuelveTodosLosSlots() {
        List<LocalTime> libres = HuecosCalculator.libres(DESDE, HASTA, 30, List.of());
        assertThat(libres).hasSize(20);
        assertThat(libres.get(0)).isEqualTo(LocalTime.of(8, 0));
        assertThat(libres.get(19)).isEqualTo(LocalTime.of(17, 30));
    }

    @Test
    void solapeParcialExcluyeSlot() {
        // ocupado 10:15→10:45 solapa slots 10:00 y 10:30, no los adyacentes
        List<HuecosCalculator.Ocupado> ocupados = List.of(
                new HuecosCalculator.Ocupado(LocalTime.of(10, 15), LocalTime.of(10, 45)));
        List<LocalTime> libres = HuecosCalculator.libres(DESDE, HASTA, 30, ocupados);
        assertThat(libres).doesNotContain(LocalTime.of(10, 0), LocalTime.of(10, 30));
        assertThat(libres).contains(LocalTime.of(9, 30), LocalTime.of(11, 0));
    }
}
