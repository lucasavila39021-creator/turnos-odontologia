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
import com.clinica.turnos.application.dto.UpdateEspecialidadRequest;
import com.clinica.turnos.application.exception.DuplicateEspecialidadException;
import com.clinica.turnos.application.exception.EspecialidadConProfesionalesException;
import com.clinica.turnos.application.service.EspecialidadService;
import com.clinica.turnos.application.service.EspecialidadUsagePort;
import com.clinica.turnos.domain.model.Especialidad;
import com.clinica.turnos.domain.model.Rol;
import com.clinica.turnos.domain.model.Usuario;
import com.clinica.turnos.domain.repository.EspecialidadRepository;
import com.clinica.turnos.domain.repository.PacienteRepository;
import com.clinica.turnos.domain.repository.ProfesionalEspecialidadRepository;
import com.clinica.turnos.domain.repository.ProfesionalRepository;
import com.clinica.turnos.domain.repository.TurnoRepository;
import com.clinica.turnos.domain.repository.UsuarioRepository;
import com.clinica.turnos.security.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * E2E especialidades ABM sobre H2 (C-06 tasks 1.3/2.x/3.x/4.1).
 * Patrón espejo de PacienteControllerH2Test: login real → sesión reutilizada.
 * Testcontainers MySQL no disponible en este entorno (sin Docker); H2 permitido
 * por hard rules como en C-04/C-05.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EspecialidadControllerH2Test {

    @Autowired MockMvc mockMvc;
    @Autowired EspecialidadRepository especialidadRepository;
    @Autowired EspecialidadService especialidadService;
    @Autowired TurnoRepository turnoRepository;
    @Autowired ProfesionalEspecialidadRepository vinculoRepository;
    @Autowired ProfesionalRepository profesionalRepository;
    @Autowired PacienteRepository pacienteRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired RateLimitService rateLimitService;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        rateLimitService.clear();
        // Orden FK-seguro: el contexto H2 se comparte entre suites (@SpringBootTest
        // cacheado) y otras suites dejan profesionales/vínculos/turnos (C-07/C-08).
        turnoRepository.deleteAll();
        vinculoRepository.deleteAll();
        profesionalRepository.deleteAll();
        pacienteRepository.deleteAll();
        especialidadRepository.deleteAll();
        usuarioRepository.deleteAll();
        usuarioRepository.save(new Usuario("admin", passwordEncoder.encode("admin123"),
                "admin@clinica.local", Rol.ADMIN, true));
        usuarioRepository.save(new Usuario("secre1", passwordEncoder.encode("secre1234"),
                "secre@clinica.local", Rol.SECRETARIA, true));
        usuarioRepository.save(new Usuario("odonto1", passwordEncoder.encode("odonto1234"),
                "odonto@clinica.local", Rol.ODONTOLOGO, true));
        usuarioRepository.save(new Usuario("pac1", passwordEncoder.encode("paciente123"),
                "pac1@clinica.local", Rol.PACIENTE, true));
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

    // 1.3 — Repositorio: persiste dos, pre-check case-insensitive, findByActiva filtra
    @Test
    void repositorioPreCheckCaseInsensitiveYFiltroActiva() {
        especialidadRepository.save(new Especialidad("Ortodoncia", "Aparatos", true));
        especialidadRepository.save(new Especialidad("Endodoncia", null, false));
        assertThat(especialidadRepository.existsByNombreIgnoreCase("ortodoncia")).isTrue();
        assertThat(especialidadRepository.existsByNombreIgnoreCase("ORTODONCIA")).isTrue();
        assertThat(especialidadRepository.existsByNombreIgnoreCase("Implantología")).isFalse();
        assertThat(especialidadRepository.findByActiva(false)).hasSize(1);
        assertThat(especialidadRepository.findByActiva(false).get(0).getNombre()).isEqualTo("Endodoncia");
        assertThat(especialidadRepository.findByActiva(true,
                PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
        // toString sin volcar descripción completa
        String str = especialidadRepository.findByActiva(false).get(0).toString();
        assertThat(str).doesNotContain("Aparatos");
    }

    // Alta ok 201 como admin + trim normaliza
    @Test
    void altaOk201YTrim() throws Exception {
        MockHttpSession session = login("admin", "admin123");
        mockMvc.perform(post("/api/especialidades").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"  Ortodoncia  \",\"descripcion\":\"  Aparatos y alineación  \"}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Ortodoncia"))
                .andExpect(jsonPath("$.descripcion").value("Aparatos y alineación"))
                .andExpect(jsonPath("$.activa").value(true))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.createdAt").exists());
        assertThat(especialidadRepository.existsByNombreIgnoreCase("ortodoncia")).isTrue();
    }

    // Descripcion ausente o en blanco → null
    @Test
    void altaDescripcionBlancoNull() throws Exception {
        MockHttpSession session = login("admin", "admin123");
        mockMvc.perform(post("/api/especialidades").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Limpieza\",\"descripcion\":\"   \"}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.descripcion", org.hamcrest.Matchers.nullValue()));
    }

    // Duplicado case-insensitive con espacios → 409 con fieldErrors.nombre, sin fila nueva
    @Test
    void duplicadoCaseInsensitive409() throws Exception {
        MockHttpSession session = login("admin", "admin123");
        especialidadService.crear(new CreateEspecialidadRequest("Ortodoncia", null));
        long antes = especialidadRepository.count();
        mockMvc.perform(post("/api/especialidades").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"  ortodoncia  \"}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.nombre").exists());
        assertThat(especialidadRepository.count()).isEqualTo(antes);
    }

    // 2.1 — Validación: cada campo inválido produce fieldErrors, nunca 500
    @Test
    void validacionCampos400() throws Exception {
        MockHttpSession session = login("admin", "admin123");
        // nombre ausente
        mockMvc.perform(post("/api/especialidades").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descripcion\":\"x\"}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.nombre").exists());
        // nombre solo espacios
        mockMvc.perform(post("/api/especialidades").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"   \"}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.nombre").exists());
        // descripcion > 255
        mockMvc.perform(post("/api/especialidades").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Prueba\",\"descripcion\":\"" + "x".repeat(256) + "\"}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.descripcion").exists());
        assertThat(especialidadRepository.existsByNombreIgnoreCase("Prueba")).isFalse();
    }

    // 2.2 — Excepciones mapean al código esperado (servicio + handler)
    @Test
    void excepcionesMapeanCodigoEsperado() {
        especialidadService.crear(new CreateEspecialidadRequest("Ortodoncia", null));
        assertThatThrownBy(() -> especialidadService.crear(new CreateEspecialidadRequest("ortodoncia", null)))
                .isInstanceOf(DuplicateEspecialidadException.class);
        EspecialidadUsagePort cautivo = id -> true;
        EspecialidadService svcCautivo = new EspecialidadService(especialidadRepository, cautivo);
        Long id = especialidadRepository.findByActiva(true,
                PageRequest.of(0, 10)).getContent().get(0).getId();
        assertThatThrownBy(() -> svcCautivo.actualizar(id, new UpdateEspecialidadRequest(null, null, false)))
                .isInstanceOf(EspecialidadConProfesionalesException.class);
    }

    // 2.3 — Servicio: alta ok, dup 409, edición 409 contra otra fila, desactivar ok NoOp, guard-400 fake
    @Test
    void servicioReglas() {
        var a = especialidadService.crear(new CreateEspecialidadRequest("Ortodoncia", "A"));
        var b = especialidadService.crear(new CreateEspecialidadRequest("Endodoncia", null));
        assertThat(a.activa()).isTrue();
        assertThatThrownBy(() -> especialidadService.crear(new CreateEspecialidadRequest(" ORTODONCIA ", null)))
                .isInstanceOf(DuplicateEspecialidadException.class);
        assertThatThrownBy(() -> especialidadService.actualizar(a.id(),
                new UpdateEspecialidadRequest("endodoncia", null, null)))
                .isInstanceOf(DuplicateEspecialidadException.class);
        // renombrar a sí misma con distinta capitalización: ok
        var misma = especialidadService.actualizar(a.id(),
                new UpdateEspecialidadRequest("ORTODONCIA", null, null));
        assertThat(misma.nombre()).isEqualTo("ORTODONCIA");
        // desactivar ok con NoOp
        var off = especialidadService.actualizar(b.id(), new UpdateEspecialidadRequest(null, null, false));
        assertThat(off.activa()).isFalse();
        // guard-400 con fake que retorna true
        EspecialidadService svcCautivo = new EspecialidadService(especialidadRepository, id -> true);
        assertThatThrownBy(() -> svcCautivo.actualizar(a.id(),
                new UpdateEspecialidadRequest(null, null, false)))
                .isInstanceOf(EspecialidadConProfesionalesException.class);
        // reactivar siempre permitido aun con cautivo
        var on = svcCautivo.actualizar(b.id(), new UpdateEspecialidadRequest(null, null, true));
        assertThat(on.activa()).isTrue();
    }

    // Lista paginada con filtro activa + orden nombre,asc
    @Test
    void listaFiltroYOrden() throws Exception {
        MockHttpSession session = login("pac1", "paciente123");
        especialidadService.crear(new CreateEspecialidadRequest("Ortodoncia", null));
        var endo = especialidadService.crear(new CreateEspecialidadRequest("Endodoncia", null));
        especialidadService.actualizar(endo.id(), new UpdateEspecialidadRequest(null, null, false));
        mockMvc.perform(get("/api/especialidades?activa=false")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].nombre").value("Endodoncia"));
        mockMvc.perform(get("/api/especialidades")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nombre").value("Endodoncia"))
                .andExpect(jsonPath("$.content[1].nombre").value("Ortodoncia"));
        mockMvc.perform(get("/api/especialidades?activa=true")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    // PATCH descripcion ok + blanco → null + renombrar a existente 409 + 404
    @Test
    void patchFlujo() throws Exception {
        MockHttpSession session = login("admin", "admin123");
        var a = especialidadService.crear(new CreateEspecialidadRequest("Ortodoncia", "Vieja"));
        especialidadService.crear(new CreateEspecialidadRequest("Endodoncia", null));
        mockMvc.perform(patch("/api/especialidades/" + a.id()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descripcion\":\"Nueva descripción\"}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descripcion").value("Nueva descripción"))
                .andExpect(jsonPath("$.nombre").value("Ortodoncia"));
        mockMvc.perform(patch("/api/especialidades/" + a.id()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descripcion\":\"   \"}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descripcion", org.hamcrest.Matchers.nullValue()));
        long antes = especialidadRepository.count();
        mockMvc.perform(patch("/api/especialidades/" + a.id()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"endodoncia\"}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.nombre").exists());
        assertThat(especialidadRepository.count()).isEqualTo(antes);
        mockMvc.perform(patch("/api/especialidades/999999").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descripcion\":\"x\"}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isNotFound());
    }

    // Desactivar ok 200 + reactivar ok (NoOp)
    @Test
    void desactivarReactivar200() throws Exception {
        MockHttpSession session = login("admin", "admin123");
        var a = especialidadService.crear(new CreateEspecialidadRequest("Ortodoncia", null));
        mockMvc.perform(patch("/api/especialidades/" + a.id()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activa\":false}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(false));
        mockMvc.perform(patch("/api/especialidades/" + a.id()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activa\":true}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(true));
    }

    // GET /{id}/profesionales: página vacía pre-C-07 + 404 si no existe
    @Test
    void profesionalesStubVacio() throws Exception {
        MockHttpSession session = login("odonto1", "odonto1234");
        var a = especialidadService.crear(new CreateEspecialidadRequest("Ortodoncia", null));
        mockMvc.perform(get("/api/especialidades/" + a.id() + "/profesionales")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
        mockMvc.perform(get("/api/especialidades/999999/profesionales")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isNotFound());
    }

    // 3.2 — Unicidad DB como red final: inserción directa duplicada → constraint + 409 mapeada
    // Nota: H2 es case-sensitive (MySQL ai_ci no); la inserción directa usa el
    // nombre exacto para disparar el UNIQUE en ambas DB. El path case-insensitive
    // está cubierto por duplicadoCaseInsensitive409 (pre-check IgnoreCase, portable).
    @Test
    void unicidadDbRedFinal() throws Exception {
        especialidadRepository.saveAndFlush(new Especialidad("Ortodoncia", null, true));
        assertThatThrownBy(() ->
                especialidadRepository.saveAndFlush(new Especialidad("Ortodoncia", null, true)))
                .isInstanceOf(DataIntegrityViolationException.class);
        // vía HTTP la carrera también termina en 409 con fieldErrors.nombre
        MockHttpSession session = login("admin", "admin123");
        mockMvc.perform(post("/api/especialidades").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"ORTOdoncia\"}")
                        .accept(MediaType.APPLICATION_JSON).session(session))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.nombre").exists());
    }

    // 3.2 — unicidad enforced a nivel DB (índice único sobre nombre) + índice en activa.
    // Nota: esquema generado por Hibernate (ddl-auto, Flyway off en tests). H2 nombra
    // el índice que respalda el UNIQUE como CONSTRAINT_INDEX_N (ignora el nombre
    // lógico uk_especialidad_nombre), por eso se asserts sustancia y no nombre:
    // índice con NON_UNIQUE=false sobre la columna NOMBRE. El nombre canónico
    // uk_especialidad_nombre vive en V4__especialidad.sql y se verifica post-deploy
    // con SHOW INDEX (design Migration Plan paso 3). unicidadDbRedFinal prueba la red final.
    @Test
    void findByActivaUsaIndice() throws Exception {
        especialidadService.crear(new CreateEspecialidadRequest("Ortodoncia", null));
        String plan = jdbcTemplate.queryForList("EXPLAIN SELECT * FROM especialidad WHERE activa = TRUE")
                .toString().toUpperCase();
        assertThat(plan).doesNotContain("FULL SCAN");
        assertThat(plan).doesNotContain("TABLE SCAN");
        assertThat(plan).doesNotContain(" SEQ SCAN ");
        boolean uniqueOnNombre = false;
        boolean activaFound = false;
        try (var conn = jdbcTemplate.getDataSource().getConnection()) {
            var meta = conn.getMetaData();
            for (String table : new String[]{"ESPECIALIDAD", "especialidad"}) {
                try (var rs = meta.getIndexInfo(null, null, table, false, false)) {
                    while (rs.next()) {
                        String col = rs.getString("COLUMN_NAME");
                        boolean nonUnique = rs.getBoolean("NON_UNIQUE");
                        if ("NOMBRE".equalsIgnoreCase(col) && !nonUnique) uniqueOnNombre = true;
                        if ("idx_especialidad_activa".equalsIgnoreCase(rs.getString("INDEX_NAME"))) {
                            activaFound = true;
                        }
                    }
                }
            }
        }
        assertThat(uniqueOnNombre).as("índice único sobre nombre existe").isTrue();
        assertThat(activaFound).as("idx_especialidad_activa existe").isTrue();
    }

    // RBAC: secretaria POST/PATCH 403; paciente GET 200 POST 403; odontólogo GET 200; no auth 401/302
    @Test
    void rbac() throws Exception {
        var a = especialidadService.crear(new CreateEspecialidadRequest("Ortodoncia", null));
        MockHttpSession secre = login("secre1", "secre1234");
        mockMvc.perform(post("/api/especialidades").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Nueva\"}")
                        .accept(MediaType.APPLICATION_JSON).session(secre))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/especialidades/" + a.id()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descripcion\":\"x\"}")
                        .accept(MediaType.APPLICATION_JSON).session(secre))
                .andExpect(status().isForbidden());
        MockHttpSession pac = login("pac1", "paciente123");
        mockMvc.perform(get("/api/especialidades")
                        .accept(MediaType.APPLICATION_JSON).session(pac))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/especialidades").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Otra\"}")
                        .accept(MediaType.APPLICATION_JSON).session(pac))
                .andExpect(status().isForbidden());
        MockHttpSession odonto = login("odonto1", "odonto1234");
        mockMvc.perform(get("/api/especialidades")
                        .accept(MediaType.APPLICATION_JSON).session(odonto))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/especialidades/" + a.id() + "/profesionales")
                        .accept(MediaType.APPLICATION_JSON).session(odonto))
                .andExpect(status().isOk());
        assertThat(especialidadRepository.existsByNombreIgnoreCase("Nueva")).isFalse();
        assertThat(especialidadRepository.existsByNombreIgnoreCase("Otra")).isFalse();
        mockMvc.perform(get("/api/especialidades").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/especialidades").accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection());
    }

    // 4.1 — E2E: alta → lista filtro → PATCH descripcion → renombrar 409 → off → on → 403 → 400
    @Test
    void flujoE2E() throws Exception {
        MockHttpSession admin = login("admin", "admin123");
        // alta
        MvcResult created = mockMvc.perform(post("/api/especialidades").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ortodoncia\",\"descripcion\":\"Aparatos\"}")
                        .accept(MediaType.APPLICATION_JSON).session(admin))
                .andExpect(status().isCreated())
                .andReturn();
        String body = created.getResponse().getContentAsString();
        Number idNum = com.jayway.jsonpath.JsonPath.read(body, "$.id");
        Long id = idNum.longValue();
        // lista con filtro activa=true la contiene
        mockMvc.perform(get("/api/especialidades?activa=true")
                        .accept(MediaType.APPLICATION_JSON).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nombre").value("Ortodoncia"));
        // PATCH descripcion
        mockMvc.perform(patch("/api/especialidades/" + id).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descripcion\":\"Alineación\"}")
                        .accept(MediaType.APPLICATION_JSON).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descripcion").value("Alineación"));
        // segunda especialidad + renombrar a existente → 409
        mockMvc.perform(post("/api/especialidades").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Endodoncia\"}")
                        .accept(MediaType.APPLICATION_JSON).session(admin))
                .andExpect(status().isCreated());
        mockMvc.perform(patch("/api/especialidades/" + id).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"endodoncia\"}")
                        .accept(MediaType.APPLICATION_JSON).session(admin))
                .andExpect(status().isConflict());
        // desactivar ok → reactivar ok
        mockMvc.perform(patch("/api/especialidades/" + id).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activa\":false}")
                        .accept(MediaType.APPLICATION_JSON).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(false));
        mockMvc.perform(patch("/api/especialidades/" + id).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activa\":true}")
                        .accept(MediaType.APPLICATION_JSON).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(true));
        // secretaria 403
        MockHttpSession secre = login("secre1", "secre1234");
        mockMvc.perform(post("/api/especialidades").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Bloqueada\"}")
                        .accept(MediaType.APPLICATION_JSON).session(secre))
                .andExpect(status().isForbidden());
        // validación nombre vacío 400
        mockMvc.perform(post("/api/especialidades").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\"}")
                        .accept(MediaType.APPLICATION_JSON).session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.nombre").exists());
    }
}
