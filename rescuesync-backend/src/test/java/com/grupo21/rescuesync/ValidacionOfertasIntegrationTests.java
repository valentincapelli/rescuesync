package com.grupo21.rescuesync;

import com.grupo21.rescuesync.client.BonitaClient;
import com.grupo21.rescuesync.exception.BonitaIntegrationException;
import com.grupo21.rescuesync.model.Rol;
import com.grupo21.rescuesync.model.Usuario;
import com.grupo21.rescuesync.security.JwtService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** E4: el resultado de "Evaluar ofertas" de Bonita llega a la pantalla de selección del municipio. */
@SpringBootTest(properties = {
        "jwt.secret=test-signing-key-for-integration-tests-32bytes",
        "jwt.expiration=86400000"
})
@AutoConfigureMockMvc
class ValidacionOfertasIntegrationTests {

    private static final String EMAIL_OPERADOR = "operador-validacion@test.local";
    private static final String CASO = "4321";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtService jwtService;
    @MockitoBean private BonitaClient bonitaClient;

    private Cookie operador;
    private long emergenciaId;
    private long loteId;
    private long principal;
    private long apoyo;
    private long sinHabilitacion;

    @BeforeEach
    void preparar() {
        jdbcTemplate.update("INSERT INTO municipios (nombre, created_at, updated_at) "
                + "VALUES ('Municipio validacion', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        long municipioId = ultimoId("municipios");
        jdbcTemplate.update("INSERT INTO usuarios (email, password_hash, rol, municipio_id, created_at, updated_at) "
                + "VALUES (?, 'test', 'OPERADOR_MUNICIPAL', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                EMAIL_OPERADOR, municipioId);
        Usuario usuario = new Usuario();
        usuario.setEmail(EMAIL_OPERADOR);
        usuario.setRol(Rol.OPERADOR_MUNICIPAL);
        operador = new Cookie("access_token", jwtService.generarToken(usuario));

        jdbcTemplate.update("INSERT INTO emergencias (tipo_desastre, nivel_gravedad, zona_afectada, descripcion, "
                        + "municipio_id, estado, bonita_case_id, created_at, updated_at) VALUES ('INUNDACION', 'ALTO', "
                        + "'Zona de prueba', 'Emergencia de prueba', ?, 'COBERTURA_EVALUADA', ?, "
                        + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                municipioId, Long.valueOf(CASO));
        emergenciaId = ultimoId("emergencias");
        jdbcTemplate.update("INSERT INTO lotes (emergencia_id, tipo_recurso, descripcion, cantidad_requerida, "
                + "unidad_medida, estado, created_at, updated_at) VALUES (?, 'ALIMENTOS', 'Raciones', 100, "
                + "'raciones', 'CUBIERTO', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", emergenciaId);
        loteId = ultimoId("lotes");
        principal = crearOferta("ONG validacion principal", 60);
        apoyo = crearOferta("ONG validacion apoyo", 30);
        sinHabilitacion = crearOferta("ONG validacion sin habilitar", 20);
    }

    @AfterEach
    void limpiar() {
        jdbcTemplate.update("DELETE FROM historial_estados");
        jdbcTemplate.update("DELETE FROM ofertas");
        jdbcTemplate.update("DELETE FROM lotes");
        jdbcTemplate.update("DELETE FROM emergencias");
        jdbcTemplate.update("DELETE FROM usuarios WHERE email = ?", EMAIL_OPERADOR);
        jdbcTemplate.update("DELETE FROM ongs WHERE nombre LIKE 'ONG validacion%'");
        jdbcTemplate.update("DELETE FROM municipios WHERE nombre = 'Municipio validacion'");
    }

    @Test
    void clasificaLasOfertasEvaluadasEnBonitaYSoloOfreceLasValidadas() throws Exception {
        evaluacionEnBonita(evaluacion());

        listar().andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(emergenciaId))
                .andExpect(jsonPath("$[0].lotes[0].ofertas", hasSize(2)))
                .andExpect(jsonPath("$[0].lotes[0].ofertas[0].id").value(principal))
                .andExpect(jsonPath("$[0].lotes[0].ofertas[0].validacion.clasificacion").value("PRINCIPAL"))
                .andExpect(jsonPath("$[0].lotes[0].ofertas[0].validacion.nivelCompetencia").value("AVANZADO"))
                .andExpect(jsonPath("$[0].lotes[0].ofertas[0].validacion.nivelRiesgoMax").value("CRITICO"))
                .andExpect(jsonPath("$[0].lotes[0].ofertas[1].id").value(apoyo))
                .andExpect(jsonPath("$[0].lotes[0].ofertas[1].validacion.clasificacion").value("APOYO"))
                .andExpect(jsonPath("$[0].lotes[0].ofertas[1].validacion.certificacionVence").value("2027-03-31"))
                .andExpect(jsonPath("$[0].lotes[0].ofertasSinHabilitacion", hasSize(1)))
                .andExpect(jsonPath("$[0].lotes[0].ofertasSinHabilitacion[0].id").value(sinHabilitacion))
                .andExpect(jsonPath("$[0].lotes[0].ofertasSinHabilitacion[0].validacion.clasificacion")
                        .value("SIN_HABILITACION"))
                .andExpect(jsonPath("$[0].lotes[0].ofertasSinHabilitacion[0].validacion.observaciones")
                        .value("La ONG no tiene una certificación vigente"));

        assertEquals("EVALUADA", estadoOferta(principal));
        assertEquals("EVALUADA", estadoOferta(sinHabilitacion));
        assertEquals("APOYO", jdbcTemplate.queryForObject(
                "SELECT validacion_clasificacion FROM ofertas WHERE id = ?", String.class, apoyo));
        assertEquals(3, cambiosDeOfertas());
    }

    @Test
    void volverAListarNoReevaluaNiDuplicaElHistorial() throws Exception {
        evaluacionEnBonita(evaluacion());

        listar().andExpect(status().isOk());
        listar().andExpect(status().isOk()).andExpect(jsonPath("$[0].lotes[0].ofertas", hasSize(2)));

        assertEquals(3, cambiosDeOfertas());
        // La segunda vez no hay ofertas PENDIENTE: no se vuelve a consultar a Bonita.
        verify(bonitaClient, times(1)).leerVariable(CASO, "ofertasEvaluadasJson", false);
    }

    @Test
    void mientrasBonitaNoEvaluoLasOfertasSiguenPendientes() throws Exception {
        evaluacionEnBonita("[]");

        listar().andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));

        assertEquals("PENDIENTE", estadoOferta(principal));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT validacion_clasificacion FROM ofertas WHERE id = ?", String.class, principal));
        assertEquals(0, cambiosDeOfertas());
    }

    @Test
    void unFalloDeBonitaNoRompeElListado() throws Exception {
        when(bonitaClient.leerVariable(anyString(), anyString(), eq(false)))
                .thenThrow(new BonitaIntegrationException("Bonita no responde", null));

        listar().andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));

        assertEquals("PENDIENTE", estadoOferta(principal));
    }

    @Test
    void ignoraLaEvaluacionDeUnaOfertaQueNoEsDeEseLote() throws Exception {
        evaluacionEnBonita(evaluacion().replaceFirst("^\\[\\{\"id\": \\d+,", "[{\"id\": 999999,"));

        listar().andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));

        assertEquals("PENDIENTE", estadoOferta(principal));
    }

    @Test
    void noSePuedeSeleccionarUnaOfertaSinHabilitacion() throws Exception {
        evaluacionEnBonita(evaluacion());
        listar().andExpect(status().isOk());

        mockMvc.perform(put("/api/ofertas/seleccion")
                        .cookie(operador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emergenciaId\": " + emergenciaId + ", \"ofertaIds\": ["
                                + principal + ", " + sinHabilitacion + "]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Una o más ofertas no tienen habilitación del Sistema Nacional para el tipo de recurso"));

        assertEquals("EVALUADA", estadoOferta(principal));
        assertEquals("COBERTURA_EVALUADA", jdbcTemplate.queryForObject(
                "SELECT estado FROM emergencias WHERE id = ?", String.class, emergenciaId));
    }

    @Test
    void unaOfertaMayorAlStockNacionalNoSeOfreceNiSePuedeSeleccionar() throws Exception {
        evaluacionEnBonita(evaluacionConStock(59)); // la oferta principal es de 60
        listar().andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lotes[0].ofertas", hasSize(1)))
                .andExpect(jsonPath("$[0].lotes[0].ofertas[0].id").value(apoyo))
                .andExpect(jsonPath("$[0].lotes[0].ofertasSinHabilitacion", hasSize(2)))
                .andExpect(jsonPath("$[0].lotes[0].ofertasSinHabilitacion[?(@.id==" + principal
                        + ")].validacion.stockDisponible").value(59))
                .andExpect(jsonPath("$[0].lotes[0].ofertasSinHabilitacion[?(@.id==" + principal
                        + ")].validacion.observaciones").value(
                        "Stock insuficiente en el Sistema Nacional: la ONG ofrece 60 raciones pero tiene 59 disponibles."));

        mockMvc.perform(put("/api/ofertas/seleccion")
                        .cookie(operador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emergenciaId\": " + emergenciaId + ", \"ofertaIds\": [" + principal + "]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Una o más ofertas superan el stock disponible de la ONG en el Sistema Nacional"));

        assertEquals("EVALUADA", estadoOferta(principal));
        assertEquals("COBERTURA_EVALUADA", jdbcTemplate.queryForObject(
                "SELECT estado FROM emergencias WHERE id = ?", String.class, emergenciaId));
    }

    @Test
    void unaOfertaQueEntraEnElStockNacionalSigueSiendoSeleccionable() throws Exception {
        evaluacionEnBonita(evaluacionConStock(60));

        listar().andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lotes[0].ofertas", hasSize(2)))
                .andExpect(jsonPath("$[0].lotes[0].ofertas[0].id").value(principal))
                .andExpect(jsonPath("$[0].lotes[0].ofertas[0].validacion.stockDisponible").value(60))
                .andExpect(jsonPath("$[0].lotes[0].ofertasSinHabilitacion", hasSize(1)));
    }

    @Test
    void sinCasoEnBonitaNoConsulta() throws Exception {
        jdbcTemplate.update("UPDATE emergencias SET bonita_case_id = NULL WHERE id = ?", emergenciaId);

        listar().andExpect(status().isOk());

        verify(bonitaClient, never()).leerVariable(anyString(), anyString(), eq(false));
    }

    private ResultActions listar() throws Exception {
        return mockMvc.perform(get("/api/ofertas/evaluadas").cookie(operador));
    }

    private void evaluacionEnBonita(String json) {
        when(bonitaClient.leerVariable(CASO, "ofertasEvaluadasJson", false)).thenReturn(Optional.of(json));
    }

    /** Forma de ofertasEvaluadasJson que deja el conector de E3-10 (ofertas consolidadas + validacion). */
    private String evaluacion() {
        return """
                [{"id": %d, "tipoRecurso": "ALIMENTOS", "unidadMedida": "raciones", "ofertas": [
                  {"id": %d, "cantidadOfrecida": 60, "validacion": {"cuit": "30500000001", "tipoRecurso": "ALIMENTOS",
                    "estadoHabilitacion": "HABILITADA", "capacidadesHabilitadas": [{"tipoRecurso": "ALIMENTOS",
                    "nivelCompetencia": "AVANZADO", "nivelRiesgoMax": "CRITICO", "fechaVencimiento": null}],
                    "observaciones": "Se informan el nivel de competencia"}},
                  {"id": %d, "cantidadOfrecida": 30, "validacion": {"cuit": "30500000002", "tipoRecurso": "ALIMENTOS",
                    "estadoHabilitacion": "HABILITADA", "capacidadesHabilitadas": [{"tipoRecurso": "ALIMENTOS",
                    "nivelCompetencia": "BASICO", "nivelRiesgoMax": "MEDIO", "fechaVencimiento": "2027-03-31"}]}},
                  {"id": %d, "cantidadOfrecida": 20, "validacion": {"cuit": "30500000003", "tipoRecurso": "ALIMENTOS",
                    "estadoHabilitacion": "HABILITADA", "capacidadesHabilitadas": [],
                    "observaciones": "La ONG no tiene una certificación vigente"}}
                ]}]""".formatted(loteId, principal, apoyo, sinHabilitacion);
    }

    /** Igual que evaluacion(), con el stock nacional (en raciones) que informa el SN para la oferta principal. */
    private String evaluacionConStock(int disponible) {
        return evaluacion().replace("\"observaciones\": \"Se informan el nivel de competencia\"}}",
                "\"observaciones\": \"Se informan el nivel de competencia\", \"disponibilidad\": "
                        + "[{\"unidadMedida\": \"raciones\", \"cantidadDisponible\": " + disponible + "}]}}");
    }

    private long crearOferta(String ong, int cantidad) {
        jdbcTemplate.update("INSERT INTO ongs (nombre, created_at, updated_at) "
                + "VALUES (?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", ong);
        long ongId = ultimoId("ongs");
        jdbcTemplate.update("INSERT INTO ofertas (lote_id, ong_id, cantidad_ofrecida, estado, created_at, updated_at) "
                + "VALUES (?, ?, ?, 'PENDIENTE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", loteId, ongId, cantidad);
        return ultimoId("ofertas");
    }

    private String estadoOferta(long ofertaId) {
        return jdbcTemplate.queryForObject("SELECT estado FROM ofertas WHERE id = ?", String.class, ofertaId);
    }

    private int cambiosDeOfertas() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM historial_estados "
                + "WHERE emergencia_id = ? AND tipo_entidad = 'OFERTA'", Integer.class, emergenciaId);
    }

    private long ultimoId(String tabla) {
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM " + tabla, Long.class);
    }
}
