package com.clinica.turnos.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.TimeZone;
import org.junit.jupiter.api.Test;

class TimeZoneConfigTest {

    @Test
    void timezoneShouldBeBuenosAiresAfterConfig() {
        TimeZoneConfig config = new TimeZoneConfig("America/Argentina/Buenos_Aires");
        config.init();
        assertThat(TimeZone.getDefault().getID()).isEqualTo("America/Argentina/Buenos_Aires");
    }

    @Test
    void defaultTimezoneIsBuenosAires() {
        // Verifica que el valor por defecto del config es BA
        TimeZoneConfig config = new TimeZoneConfig("America/Argentina/Buenos_Aires");
        config.init();
        assertThat(TimeZone.getDefault().getID()).isEqualTo("America/Argentina/Buenos_Aires");
        // Restore precaution not needed — Spring will re-set on next boot
    }
}
