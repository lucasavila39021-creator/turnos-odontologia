package com.clinica.turnos.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.clinica.turnos.domain.model.EstadoTurno;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/**
 * C-09 task 4.1 (RED→GREEN): DTOs de consulta — envelope Page,
 * AgendaResponse/HuecosResponse, TurnoQueryParams. Verifica compilación y
 * serialización JSON de un ejemplo.
 */
class TurnoConsultaDtoTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private TurnoResponse ejemplo() {
        return new TurnoResponse(5L,
                new TurnoResponse.PacienteInfo(1L, "Juan", "Pérez"),
                new TurnoResponse.ProfesionalInfo(2L, "Ana", "Gómez"),
                new TurnoResponse.EspecialidadInfo(3L, "Ortodoncia"),
                LocalDate.of(2026, 10, 12), LocalTime.of(10, 0), LocalTime.of(10, 30),
                EstadoTurno.RESERVADO, null, null, null);
    }

    @Test
    void listEnvelopeSerializaContentYTotales() throws Exception {
        var page = new PageImpl<>(List.of(ejemplo()), PageRequest.of(0, 20), 1);
        TurnoListResponse envelope = TurnoListResponse.from(page);
        String json = mapper.writeValueAsString(envelope);
        assertThat(json).contains("\"totalElements\":1");
        assertThat(json).contains("\"content\"");
        assertThat(json).contains("Ortodoncia");
    }

    @Test
    void agendaYHuecosSerializanOcupadosYLibres() throws Exception {
        AgendaResponse agenda = new AgendaResponse(LocalDate.of(2026, 10, 12),
                List.of(ejemplo()), List.of(LocalTime.of(8, 0), LocalTime.of(11, 0)));
        String json = mapper.writeValueAsString(agenda);
        assertThat(json).contains("\"ocupados\"");
        assertThat(json).contains("\"libres\"");
        HuecosResponse huecos = HuecosResponse.from(agenda);
        assertThat(mapper.writeValueAsString(huecos)).contains("\"libres\"");
    }

    @Test
    void queryParamsTransportaFiltros() {
        TurnoQueryParams params = new TurnoQueryParams(2L,
                LocalDate.of(2026, 10, 12), null, null, EstadoTurno.RESERVADO);
        assertThat(params.profesionalId()).isEqualTo(2L);
        assertThat(params.estado()).isEqualTo(EstadoTurno.RESERVADO);
    }
}
