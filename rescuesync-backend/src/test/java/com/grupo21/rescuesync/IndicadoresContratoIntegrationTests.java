package com.grupo21.rescuesync;

import com.grupo21.rescuesync.model.Rol;
import com.grupo21.rescuesync.model.Usuario;
import com.grupo21.rescuesync.security.JwtService;
import jakarta.servlet.http.Cookie;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato de /api/indicadores (seguridad, filtros y forma del JSON) e historial de estados.
 * Los valores de los indicadores se prueban en los tests de cada servicio, no acá.
 */
@SpringBootTest(properties = {
        "jwt.secret=test-signing-key-for-integration-tests-32bytes",
        "jwt.expiration=86400000",
        "bonita.base-url=http://localhost:1/bonita",
        "app.convocatoria.plazo-ms=60000"
})
@AutoConfigureMockMvc
class IndicadoresContratoIntegrationTests {

    private static final String DIRECTIVO = "directivo-contrato@test.local";
    private static final String CENTRO = "centro-contrato@test.local";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtService jwtService;

    private Cookie cookieDirectivo;
    private Cookie cookieCentro;
    private Long municipioId;

    @BeforeEach
    void preparar() {
        jdbcTemplate.update("INSERT INTO municipios (nombre, created_at, updated_at) "
                + "VALUES ('Municipio contrato', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        municipioId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM municipios", Long.class);
        crearUsuario(DIRECTIVO, Rol.DIRECTIVO);
        crearUsuario(CENTRO, Rol.CENTRO_COORDINADOR);
        cookieDirectivo = cookie(DIRECTIVO, Rol.DIRECTIVO);
        cookieCentro = cookie(CENTRO, Rol.CENTRO_COORDINADOR);
    }

    @AfterEach
    void limpiar() {
        jdbcTemplate.update("DELETE FROM historial_estados");
        jdbcTemplate.update("DELETE FROM ofertas");
        jdbcTemplate.update("DELETE FROM lotes");
        jdbcTemplate.update("DELETE FROM emergencias");
        jdbcTemplate.update("DELETE FROM usuarios WHERE email IN (?, ?)", DIRECTIVO, CENTRO);
        jdbcTemplate.update("DELETE FROM municipios WHERE nombre = 'Municipio contrato'");
    }

    @ParameterizedTest
    @ValueSource(strings = {"resumen", "suministros", "consorcios", "proyectos", "tiempos-etapas", "reporte"})
    void exigeSesionYPermisoDeIndicadores(String endpoint) throws Exception {
        String ruta = "/api/indicadores/" + endpoint;
        mockMvc.perform(get(ruta)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(ruta).cookie(cookieCentro)).andExpect(status().isForbidden());
        mockMvc.perform(get(ruta).cookie(cookieDirectivo)).andExpect(status().isOk());
    }

    @Test
    void resumenAplicaFechasPorDefectoYMarcaBonitaNoDisponible() throws Exception {
        // Sin casos de Bonita no se consulta Bonita (disponible = true con 0 muestras). Con un caso,
        // se consulta bonita.base-url (localhost:1, sin nada escuchando) y queda no disponible.
        jdbcTemplate.update("INSERT INTO emergencias "
                        + "(tipo_desastre, nivel_gravedad, zona_afectada, descripcion, municipio_id, estado, "
                        + "bonita_case_id, created_at, updated_at) "
                        + "VALUES ('INUNDACION', 'ALTO', 'Zona Bonita', 'Con caso', ?, 'REGISTRADA', 4242, "
                        + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                municipioId);
        LocalDate hoy = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires"));
        mockMvc.perform(get("/api/indicadores/resumen").cookie(cookieDirectivo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filtro.hasta").value(hoy.toString()))
                .andExpect(jsonPath("$.filtro.desde").value(hoy.minusMonths(11).withDayOfMonth(1).toString()))
                .andExpect(jsonPath("$.generadoEn").exists())
                .andExpect(jsonPath("$.emergenciasTotales").isNumber())
                .andExpect(jsonPath("$.bonita.disponible").value(false))
                .andExpect(jsonPath("$.bonita.mensaje").isString());
    }

    @Test
    void resumenRespetaLosFiltrosRecibidos() throws Exception {
        mockMvc.perform(get("/api/indicadores/resumen").cookie(cookieDirectivo)
                        .param("desde", "2026-01-01").param("hasta", "2026-06-30")
                        .param("municipioId", municipioId.toString())
                        .param("tipoDesastre", "INUNDACION").param("tipoRecurso", "ALIMENTOS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filtro.desde").value("2026-01-01"))
                .andExpect(jsonPath("$.filtro.hasta").value("2026-06-30"))
                .andExpect(jsonPath("$.filtro.municipioId").value(municipioId))
                .andExpect(jsonPath("$.filtro.tipoDesastre").value("INUNDACION"))
                .andExpect(jsonPath("$.filtro.tipoRecurso").value("ALIMENTOS"));
    }

    @Test
    void rechazaFiltrosInvalidos() throws Exception {
        mockMvc.perform(get("/api/indicadores/resumen").cookie(cookieDirectivo)
                        .param("desde", "2026-07-01").param("hasta", "2026-06-30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        mockMvc.perform(get("/api/indicadores/resumen").cookie(cookieDirectivo).param("desde", "01/07/2026"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/indicadores/resumen").cookie(cookieDirectivo).param("tipoDesastre", "NO_EXISTE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tiemposEtapasSiempreDevuelveTodasLasEtapas() throws Exception {
        mockMvc.perform(get("/api/indicadores/tiempos-etapas").cookie(cookieDirectivo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bonita.disponible").isBoolean())
                .andExpect(jsonPath("$.etapas", hasSize(6)))
                .andExpect(jsonPath("$.etapas[0].etapa").value("REGISTRO"))
                .andExpect(jsonPath("$.etapas[0].tareas[0]").value("Registrar emergencia"))
                .andExpect(jsonPath("$.etapas[5].etapa").value("CIERRE"));
    }

    @Test
    void proyectosDevuelveUnaEntradaPorEstado() throws Exception {
        mockMvc.perform(get("/api/indicadores/proyectos").cookie(cookieDirectivo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.porEstado", hasSize(8)))
                .andExpect(jsonPath("$.porEstado[0].estado").value("REGISTRADA"))
                .andExpect(jsonPath("$.proyectos").isArray());
    }

    @Test
    void reporteJsonIncluyeTodasLasSecciones() throws Exception {
        mockMvc.perform(get("/api/indicadores/reporte").cookie(cookieDirectivo).param("formato", "JSON"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumen.filtro.desde").exists())
                .andExpect(jsonPath("$.suministros.series").isArray())
                .andExpect(jsonPath("$.suministros.totales").isArray())
                .andExpect(jsonPath("$.consorcios.porTipoRecurso").isArray())
                .andExpect(jsonPath("$.proyectos.porEstado").isArray())
                .andExpect(jsonPath("$.tiemposEtapas.etapas", hasSize(6)));
    }

    @Test
    void reporteRechazaFormatoDesconocido() throws Exception {
        mockMvc.perform(get("/api/indicadores/reporte").cookie(cookieDirectivo).param("formato", "pdf"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reporteCsvSeDescargaConNombreYSecciones() throws Exception {
        byte[] cuerpo = mockMvc.perform(get("/api/indicadores/reporte").cookie(cookieDirectivo)
                        .param("formato", "csv").param("desde", "2026-01-01").param("hasta", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"indicadores_2026-01-01_2026-06-30.csv\""))
                .andReturn().getResponse().getContentAsByteArray();

        String csv = new String(cuerpo, StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿# Filtros\r\n");
        assertThat(csv).contains("# Resumen\r\nIndicador;Valor\r\nEmergencias totales;0\r\n");
        assertThat(csv).contains("Desde;2026-01-01\r\n");
        assertThat(csv).contains("# Tiempos por etapa\r\n");
    }

    @Test
    void reporteXlsxSeDescargaConUnaHojaPorSeccion() throws Exception {
        byte[] cuerpo = mockMvc.perform(get("/api/indicadores/reporte").cookie(cookieDirectivo).param("formato", "XLSX"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.endsWith(".xlsx\"")))
                .andReturn().getResponse().getContentAsByteArray();

        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(cuerpo))) {
            assertThat(libro.getNumberOfSheets()).isEqualTo(9);
            assertThat(libro.getSheetName(0)).isEqualTo("Filtros");
            assertThat(libro.getSheetName(1)).isEqualTo("Resumen");
            assertThat(libro.getSheet("Tiempos por etapa").getLastRowNum()).isEqualTo(6);
        }
    }

    @Test
    void registraHistorialAlCrearLoteYPublicarConvocatoria() throws Exception {
        jdbcTemplate.update("INSERT INTO emergencias "
                        + "(tipo_desastre, nivel_gravedad, zona_afectada, descripcion, municipio_id, estado, created_at, updated_at) "
                        + "VALUES ('INUNDACION', 'ALTO', 'Zona', 'Descripción', ?, 'REGISTRADA', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                municipioId);
        long emergenciaId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM emergencias", Long.class);

        mockMvc.perform(post("/api/emergencias/" + emergenciaId + "/lotes").cookie(cookieCentro)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipoRecurso\":\"ALIMENTOS\",\"descripcion\":\"Raciones\","
                                + "\"cantidadRequerida\":10,\"unidadMedida\":\"raciones\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/emergencias/" + emergenciaId + "/convocatoria").cookie(cookieCentro))
                .andExpect(status().isOk());

        List<Map<String, Object>> filas = jdbcTemplate.queryForList(
                "SELECT tipo_entidad, estado_anterior, estado_nuevo, emergencia_id FROM historial_estados "
                        + "WHERE emergencia_id = ? ORDER BY id", emergenciaId);

        assertThat(filas).extracting(f -> f.get("TIPO_ENTIDAD") + ":" + f.get("ESTADO_ANTERIOR") + "->" + f.get("ESTADO_NUEVO"))
                .containsExactly(
                        "EMERGENCIA:REGISTRADA->EN_REVISION",
                        "LOTE:null->BORRADOR",
                        "LOTE:BORRADOR->PUBLICADO",
                        "EMERGENCIA:EN_REVISION->CONVOCATORIA_PUBLICADA");
    }

    private void crearUsuario(String email, Rol rol) {
        jdbcTemplate.update("INSERT INTO usuarios (email, password_hash, rol, created_at, updated_at) "
                + "VALUES (?, 'test', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", email, rol.name());
    }

    private Cookie cookie(String email, Rol rol) {
        Usuario usuario = new Usuario();
        usuario.setEmail(email);
        usuario.setRol(rol);
        return new Cookie("access_token", jwtService.generarToken(usuario));
    }
}
