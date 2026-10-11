package com.clinica.turnos.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clinica.turnos.application.dto.CreateEspecialidadRequest;
import com.clinica.turnos.application.dto.CreateProfesionalRequest;
import com.clinica.turnos.application.dto.UpdateEspecialidadRequest;
import com.clinica.turnos.application.dto.UpdateProfesionalRequest;
import com.clinica.turnos.application.exception.DuplicateMatriculaException;
import com.clinica.turnos.application.exception.InvalidProfesionalException;
import com.clinica.turnos.application.service.EspecialidadService;
import com.clinica.turnos.application.service.ProfesionalService;
import com.clinica.turnos.domain.model.Profesional;
import com.clinica.turnos.domain.model.ProfesionalEspecialidad;
import com.clinica.turnos.domain.model.Especialidad;
import com.clinica.turnos.domain.model.EstadoTurno;
import com.clinica.turnos.domain.model.Paciente;
import com.clinica.turnos.domain.model.Turno;
import com.clinica.turnos.domain.model.Rol;
import com.clinica.turnos.domain.model.Usuario;
import com.clinica.turnos.domain.repository.EspecialidadRepository;
import com.clinica.turnos.domain.repository.PacienteRepository;
import com.clinica.turnos.domain.repository.ProfesionalEspecialidadRepository;
import com.clinica.turnos.domain.repository.ProfesionalRepository;
import com.clinica.turnos.domain.repository.TurnoRepository;
import com.clinica.turnos.domain.repository.UsuarioRepository;
import com.clinica.turnos.security.RateLimitService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * E2E profesionales ABM sobre H2 (C-07 tasks 1.2/1.3/2.x/3.x/4.x).
 * Patrón espejo de EspecialidadControllerH2Test: login real → sesión reutilizada.
 * Testcontainers MySQL no disponible en este entorno (sin Docker); H2 permitido
 * por hard rules como en C-04/C-05/C-06.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProfesionalControllerH2Test {

    @Autowired MockMvc mockMvc;
    @Autowired ProfesionalService profesionalService;
    @Autowired ProfesionalRepository profesionalRepository;
    @Autowired ProfesionalEspecialidadRepository vinculoRepository;
    @Autowired EspecialidadService especialidadService;
    @Autowired EspecialidadRepository especialidadRepository;
    @Autowired PacienteRepository pacienteRepository;
    @Autowired TurnoRepository turnoRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired RateLimitService rateLimitService;

    private Long ortodonciaId;
    private Long endodonciaId;
    private Long odontoUserId;

    @BeforeEach
    void setUp() {
        rateLimitService.clear();
        turnoRepository.deleteAll();
        vinculoRepository.deleteAll();
        profesionalRepository.deleteAll();
        especialidadRepository.deleteAll();
        pacienteRepository.deleteAll();
        usuarioRepository.deleteAll();
        usuarioRepository.save(new Usuario("admin", passwordEncoder.encode("admin123"),
                "admin@clinica.local", Rol.ADMIN, true));
        usuarioRepository.save(new Usuario("secre1", passwordEncoder.encode("secre1234"),
                "secre@clinica.local", Rol.SECRETARIA, true));
        usuarioRepository.save(new Usuario("odonto1", passwordEncoder.encode("odonto1234"),
                "odonto@clinica.local", Rol.ODONTOLOGO, true));
        odontoUserId = usuarioRepository.save(new Usuario("odonto2", passwordEncoder.encode("odonto1234"),
                "odonto2@clinica.local", Rol.ODONTOLOGO, true)).getId();
        usuarioRepository.save(new Usuario("pac1", passwordEncoder.encode("paciente123"),
                "pac1@clinica.local", Rol.PACIENTE, true));
        ortodonciaId = especialidadService.crear(new CreateEspecialidadRequest("Ortodoncia", null)).id();
        endodonciaId = especialidadService.crear(new CreateEspecialidadRequest("Endodoncia", null)).id();
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private CreateProfesionalRequest alta(String nombre, String apellido, String matricula,
                                          List<Long> espIds) {
        return new CreateProfesionalRequest(nombre, apellido, matricula, null, null, null, espIds);
    }

    // 1.2 — Persistencia: save + find, existsByMatricula, findByUsuarioId, intermedia
    @Test
    void persistenciaSaveFind() {
        var resp = profesionalService.crear(alta("Ana", "Gómez", "M-100", List.of(ortodonciaId)));
        assertThat(resp.id()).isNotNull();
        assertThat(resp.activo()).isTrue();
        assertThat(profesionalRepository.existsByMatricula("M-100")).isTrue();
        assertThat(profesionalRepository.existsByMatricula("M-999")).isFalse();
        Profesional entity = profesionalRepository.findById(resp.id()).orElseThrow();
        assertThat(entity.getNombre()).isEqualTo("Ana");
        List<ProfesionalEspecialidad> vinculos = vinculoRepository.findByProfesionalId(resp.id());
        assertThat(vinculos).hasSize(1);
        assertThat(vinculos.get(0).getEspecialidad().getId()).isEqualTo(ortodonciaId);
        // toString no vuelca datos sensibles
        assertThat(entity.toString()).doesNotContain("Gómez").doesNotContain("M-100");
    }

    // 1.2 — findByUsuarioId tras alta con link odontólogo
    @Test
    void persistenciaLinkUsuario() {
        var resp = profesionalService.crear(new CreateProfesionalRequest(
                "Luis", "Pérez", "M-101", null, null, odontoUserId, List.of(ortodonciaId)));
        assertThat(profesionalRepository.findByUsuarioId(odontoUserId)).isPresent();
        assertThat(profesionalRepository.existsByUsuarioId(odontoUserId)).isTrue();
        assertThat(resp.usuarioId()).isEqualTo(odontoUserId);
    }

    // 1.3 — Validación DTO: nombre ausente → 400 con fieldErrors.nombre, sin fila
    @Test
    void validacionNombreAusente400() throws Exception {
        MockHttpSession session = login("admin", "admin123");
        long antes = profesionalRepository.count();
        mockMvc.perform(post("/api/profesionales").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apellido\":\"Gómez\",\"especialidadIds\":[" + ortodonciaId + "]}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.nombre").exists());
        assertThat(profesionalRepository.count()).isEqualTo(antes);
    }

    // 1.3 — Email inválido → 400 con fieldErrors.email
    @Test
    void validacionEmailInvalido400() throws Exception {
        MockHttpSession session = login("admin", "admin123");
        mockMvc.perform(post("/api/profesionales").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana\",\"apellido\":\"Gómez\","
                                + "\"email\":\"no-es-email\",\"especialidadIds\":[" + ortodonciaId + "]}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
        assertThat(profesionalRepository.count()).isZero();
    }

    // 2.1 — Servicio: alta con 2 especialidades ok
    @Test
    void servicioAltaDosEspecialidades() {
        var resp = profesionalService.crear(alta("Ana", "Gómez", "M-102",
                List.of(ortodonciaId, endodonciaId)));
        assertThat(resp.especialidades()).hasSize(2);
        var obtenido = profesionalService.obtener(resp.id());
        assertThat(obtenido.especialidades()).hasSize(2);
        var pagina = profesionalService.listar(null, null, PageRequest.of(0, 10));
        assertThat(pagina.getTotalElements()).isEqualTo(1);
    }

    // 2.1 — Servicio: re-asignar reemplaza el set
    @Test
    void servicioReasignarReemplazaSet() {
        var resp = profesionalService.crear(alta("Ana", "Gómez", "M-103",
                List.of(ortodonciaId, endodonciaId)));
        var editado = profesionalService.actualizar(resp.id(),
                new UpdateProfesionalRequest(null, null, null, null, null, null,
                        List.of(endodonciaId), null));
        assertThat(editado.especialidades()).hasSize(1);
        assertThat(editado.especialidades().get(0).id()).isEqualTo(endodonciaId);
        assertThat(vinculoRepository.findByProfesionalId(resp.id())).hasSize(1);
    }

    // 2.1 — Servicio: sin especialidades / inexistente / inactiva → 400
    @Test
    void servicioEspecialidadesInvalidas400() {
        assertThatThrownBy(() -> profesionalService.crear(alta("A", "B", "M-1", List.of())))
                .isInstanceOf(InvalidProfesionalException.class);
        assertThatThrownBy(() -> profesionalService.crear(alta("A", "B", "M-1", List.of(999999L))))
                .isInstanceOf(InvalidProfesionalException.class);
        especialidadService.actualizar(endodonciaId,
                new UpdateEspecialidadRequest(null, null, false));
        assertThatThrownBy(() -> profesionalService.crear(alta("A", "B", "M-1", List.of(endodonciaId))))
                .isInstanceOf(InvalidProfesionalException.class);
        assertThat(profesionalRepository.count()).isZero();
    }

    // 2.1 — Servicio: matrícula duplicada → 409
    @Test
    void servicioMatriculaDuplicada409() {
        profesionalService.crear(alta("Ana", "Gómez", "M-104", List.of(ortodonciaId)));
        assertThatThrownBy(() -> profesionalService.crear(alta("Otro", "Otro", "M-104", List.of(ortodonciaId))))
                .isInstanceOf(DuplicateMatriculaException.class);
        assertThat(profesionalRepository.count()).isEqualTo(1);
    }

    // 2.1 — Servicio: link duplicado 409 + rol no-odontólogo 400 + inexistente 404
    @Test
    void servicioValidarLink() {
        profesionalService.crear(new CreateProfesionalRequest(
                "Luis", "Pérez", "M-105", null, null, odontoUserId, List.of(ortodonciaId)));
        Long pacId = usuarioRepository.findAll().stream()
                .filter(u -> u.getRol() == Rol.PACIENTE).findFirst().orElseThrow().getId();
        // duplicado → 409 (DuplicateLinkException)
        assertThatThrownBy(() -> profesionalService.crear(new CreateProfesionalRequest(
                "Otro", "Otro", "M-106", null, null, odontoUserId, List.of(ortodonciaId))))
                .isInstanceOf(com.clinica.turnos.application.exception.DuplicateLinkException.class);
        // rol PACIENTE → 400
        assertThatThrownBy(() -> profesionalService.crear(new CreateProfesionalRequest(
                "Otro", "Otro", "M-107", null, null, pacId, List.of(ortodonciaId))))
                .isInstanceOf(InvalidProfesionalException.class);
        // inexistente → 404
        assertThatThrownBy(() -> profesionalService.crear(new CreateProfesionalRequest(
                "Otro", "Otro", "M-108", null, null, 999999L, List.of(ortodonciaId))))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    // 2.2 — Desactivar sin turnos → 200 + activo=false (guard defensivo)
    @Test
    void desactivarSinTurnos200() throws Exception {
        MockHttpSession session = login("admin", "admin123");
        var resp = profesionalService.crear(alta("Ana", "Gómez", "M-109", List.of(ortodonciaId)));
        mockMvc.perform(patch("/api/profesionales/" + resp.id() + "/desactivar").with(csrf())
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
        assertThat(profesionalRepository.findById(resp.id()).orElseThrow().isActivo()).isFalse();
        // reactivar vía PATCH siempre ok
        mockMvc.perform(patch("/api/profesionales/" + resp.id()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":true}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(true));
    }

    // 2.2 — Guard real RN-PR-04 (C-08): turno futuro RESERVADO bloquea desactivar
    @Test
    void desactivarConTurnosFuturos400() throws Exception {
        MockHttpSession session = login("admin", "admin123");
        var resp = profesionalService.crear(alta("Ana", "Gómez", "M-109b", List.of(ortodonciaId)));
        Paciente pac = pacienteRepository.save(new Paciente("Juan", "Pérez", "99999999",
                null, null, null, null));
        turnoRepository.save(new Turno(pac.getId(), resp.id(), ortodonciaId,
                LocalDate.now().plusDays(10), LocalTime.of(10, 0), LocalTime.of(10, 30)));
        assertThat(profesionalService.tieneTurnosFuturosReservados(resp.id())).isTrue();
        mockMvc.perform(patch("/api/profesionales/" + resp.id() + "/desactivar").with(csrf())
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isBadRequest());
        assertThat(profesionalRepository.findById(resp.id()).orElseThrow().isActivo()).isTrue();
        // turno pasado/cancelado no bloquea: otro profesional sin futuros → 200
        var libre = profesionalService.crear(alta("Luis", "Pérez", "M-109c", List.of(ortodonciaId)));
        assertThat(profesionalService.tieneTurnosFuturosReservados(libre.id())).isFalse();
        mockMvc.perform(patch("/api/profesionales/" + libre.id() + "/desactivar").with(csrf())
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
    }

    // 2.3 — Envelope problem-details en 409 matrícula (fieldErrors.matricula)
    @Test
    void envelope409Matricula() throws Exception {
        MockHttpSession session = login("admin", "admin123");
        profesionalService.crear(alta("Ana", "Gómez", "M-110", List.of(ortodonciaId)));
        mockMvc.perform(post("/api/profesionales").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Otro\",\"apellido\":\"Otro\","
                                + "\"matricula\":\"M-110\",\"especialidadIds\":[" + ortodonciaId + "]}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.fieldErrors.matricula").exists());
    }

    // 2.3 — Envelope 404 en GET inexistente
    @Test
    void envelope404() throws Exception {
        MockHttpSession session = login("admin", "admin123");
        mockMvc.perform(get("/api/profesionales/999999")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    // 3.1 — Handoff: profesional cautivo bloquea desactivar especialidad (400, fila intacta)
    @Test
    void guardCautivo400FilaIntacta() throws Exception {
        MockHttpSession session = login("admin", "admin123");
        profesionalService.crear(alta("Ana", "Gómez", "M-111", List.of(ortodonciaId)));
        mockMvc.perform(patch("/api/especialidades/" + ortodonciaId).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activa\":false}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isBadRequest());
        assertThat(especialidadRepository.findById(ortodonciaId).orElseThrow().isActiva()).isTrue();
    }

    // 3.1 — Handoff: profesional multi-especialidad no es cautivo → 200
    @Test
    void guardMultiEspecialidad200() throws Exception {
        MockHttpSession session = login("admin", "admin123");
        profesionalService.crear(alta("Ana", "Gómez", "M-112",
                List.of(ortodonciaId, endodonciaId)));
        mockMvc.perform(patch("/api/especialidades/" + ortodonciaId).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activa\":false}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(false));
    }

    // 3.2 — GET /especialidades/{id}/profesionales real: 2 vinculados ordenados
    @Test
    void profesionalesPorEspecialidadReales() throws Exception {
        MockHttpSession session = login("odonto1", "odonto1234");
        profesionalService.crear(alta("Zulma", "Zárate", "M-113", List.of(ortodonciaId)));
        profesionalService.crear(alta("Ana", "Álvarez", "M-114", List.of(ortodonciaId)));
        mockMvc.perform(get("/api/especialidades/" + ortodonciaId + "/profesionales")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].apellido").value("Zárate"))
                .andExpect(jsonPath("$.content[1].apellido").value("Álvarez"));
    }

    // 3.2 — Sin vinculados → content:[]
    @Test
    void profesionalesPorEspecialidadVacia() throws Exception {
        MockHttpSession session = login("odonto1", "odonto1234");
        mockMvc.perform(get("/api/especialidades/" + endodonciaId + "/profesionales")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
        mockMvc.perform(get("/api/especialidades/999999/profesionales")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isNotFound());
    }

    // 4.1 — Alta con 2 especialidades → 201
    @Test
    void alta201DosEspecialidades() throws Exception {
        MockHttpSession session = login("admin", "admin123");
        mockMvc.perform(post("/api/profesionales").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana\",\"apellido\":\"Gómez\",\"matricula\":\"M-115\","
                                + "\"especialidadIds\":[" + ortodonciaId + "," + endodonciaId + "]}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.especialidades.length()").value(2));
    }

    // 4.1 — Sin especialidades → 400
    @Test
    void altaSinEspecialidades400() throws Exception {
        MockHttpSession session = login("admin", "admin123");
        mockMvc.perform(post("/api/profesionales").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana\",\"apellido\":\"Gómez\",\"especialidadIds\":[]}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.especialidadIds").exists());
        assertThat(profesionalRepository.count()).isZero();
    }

    // 4.1 — Usuario no-odontólogo → 400
    @Test
    void altaUsuarioNoOdontologo400() throws Exception {
        MockHttpSession session = login("admin", "admin123");
        Long pacId = usuarioRepository.findAll().stream()
                .filter(u -> u.getRol() == Rol.PACIENTE).findFirst().orElseThrow().getId();
        mockMvc.perform(post("/api/profesionales").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana\",\"apellido\":\"Gómez\",\"usuarioId\":" + pacId
                                + ",\"especialidadIds\":[" + ortodonciaId + "]}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isBadRequest());
        assertThat(profesionalRepository.count()).isZero();
    }

    // 4.1 — Link duplicado → 409
    @Test
    void altaLinkDuplicado409() throws Exception {
        MockHttpSession session = login("admin", "admin123");
        profesionalService.crear(new CreateProfesionalRequest(
                "Luis", "Pérez", "M-116", null, null, odontoUserId, List.of(ortodonciaId)));
        mockMvc.perform(post("/api/profesionales").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Otro\",\"apellido\":\"Otro\",\"usuarioId\":" + odontoUserId
                                + ",\"especialidadIds\":[" + ortodonciaId + "]}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.usuarioId").exists());
    }

    // 4.1 — Filtro por especialidad + activo
    @Test
    void listaFiltroEspecialidadYActivo() throws Exception {
        MockHttpSession session = login("admin", "admin123");
        var activo = profesionalService.crear(alta("Ana", "Gómez", "M-117", List.of(ortodonciaId)));
        profesionalService.crear(alta("Luis", "Pérez", "M-118", List.of(ortodonciaId)));
        profesionalService.desactivar(
                profesionalRepository.findByMatricula("M-118").orElseThrow().getId());
        mockMvc.perform(get("/api/profesionales?especialidad=" + ortodonciaId + "&activo=true")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(activo.id().intValue()));
    }

    // 4.1 — PATCH re-asignar + GET detalle + 404
    @Test
    void editarYObtener() throws Exception {
        MockHttpSession session = login("admin", "admin123");
        var resp = profesionalService.crear(alta("Ana", "Gómez", "M-119",
                List.of(ortodonciaId, endodonciaId)));
        mockMvc.perform(get("/api/profesionales/" + resp.id())
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matricula").value("M-119"));
        mockMvc.perform(patch("/api/profesionales/" + resp.id()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"especialidadIds\":[" + endodonciaId + "]}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.especialidades.length()").value(1))
                .andExpect(jsonPath("$.especialidades[0].id").value(endodonciaId.intValue()));
    }

    // 4.1/4.2 — RBAC: PACIENTE POST 403, SECRETARIA GET 200 / POST 403, no-auth 401
    @Test
    void rbac() throws Exception {
        var resp = profesionalService.crear(alta("Ana", "Gómez", "M-120", List.of(ortodonciaId)));
        MockHttpSession pac = login("pac1", "paciente123");
        mockMvc.perform(post("/api/profesionales").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"X\",\"apellido\":\"Y\",\"especialidadIds\":[" + ortodonciaId + "]}")
                        .accept(MediaType.APPLICATION_JSON).session(pac))
                .andExpect(status().isForbidden());
        MockHttpSession secre = login("secre1", "secre1234");
        mockMvc.perform(get("/api/profesionales")
                        .accept(MediaType.APPLICATION_JSON).session(secre))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/profesionales/" + resp.id())
                        .accept(MediaType.APPLICATION_JSON).session(secre))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/profesionales").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"X\",\"apellido\":\"Y\",\"especialidadIds\":[" + ortodonciaId + "]}")
                        .accept(MediaType.APPLICATION_JSON).session(secre))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/profesionales/" + resp.id()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telefono\":\"123\"}")
                        .accept(MediaType.APPLICATION_JSON).session(secre))
                .andExpect(status().isForbidden());
        MockHttpSession odonto = login("odonto1", "odonto1234");
        mockMvc.perform(get("/api/profesionales")
                        .accept(MediaType.APPLICATION_JSON).session(odonto))
                .andExpect(status().isOk());
        assertThat(profesionalRepository.count()).isEqualTo(1);
        mockMvc.perform(get("/api/profesionales").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/profesionales").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection());
    }

    // 4.2 — ADMIN CRUD completo E2E: alta → listar → editar → desactivar
    @Test
    void flujoE2EAdmin() throws Exception {
        MockHttpSession admin = login("admin", "admin123");
        MvcResult created = mockMvc.perform(post("/api/profesionales").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana\",\"apellido\":\"Gómez\",\"matricula\":\"M-121\","
                                + "\"telefono\":\"381-123\",\"especialidadIds\":[" + ortodonciaId + "]}")
                        .accept(MediaType.APPLICATION_JSON).session(admin))
                .andExpect(status().isCreated())
                .andReturn();
        Number idNum = com.jayway.jsonpath.JsonPath.read(
                created.getResponse().getContentAsString(), "$.id");
        Long id = idNum.longValue();
        mockMvc.perform(get("/api/profesionales")
                        .accept(MediaType.APPLICATION_JSON).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].matricula").value("M-121"));
        mockMvc.perform(patch("/api/profesionales/" + id).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telefono\":\"381-999\"}")
                        .accept(MediaType.APPLICATION_JSON).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefono").value("381-999"));
        mockMvc.perform(patch("/api/profesionales/" + id + "/desactivar").with(csrf())
                        .accept(MediaType.APPLICATION_JSON).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
    }

    // 4.2 — PATCH con especialidad_ids vacío → 400 (no deja sin especialidades)
    @Test
    void patchSetVacio400() throws Exception {
        MockHttpSession session = login("admin", "admin123");
        var resp = profesionalService.crear(alta("Ana", "Gómez", "M-122", List.of(ortodonciaId)));
        mockMvc.perform(patch("/api/profesionales/" + resp.id()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"especialidadIds\":[]}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isBadRequest());
        assertThat(vinculoRepository.findByProfesionalId(resp.id())).hasSize(1);
    }

    // 4.2 — Desvincular usuario con null explícito (Optional vacío)
    @Test
    void desvincularUsuario() {
        var resp = profesionalService.crear(new CreateProfesionalRequest(
                "Luis", "Pérez", "M-123", null, null, odontoUserId, List.of(ortodonciaId)));
        var editado = profesionalService.actualizar(resp.id(),
                new UpdateProfesionalRequest(null, null, null, null, null,
                        Optional.empty(), null, null));
        assertThat(editado.usuarioId()).isNull();
        assertThat(profesionalRepository.existsByUsuarioId(odontoUserId)).isFalse();
    }
}
