package com.grupo21.rescuesync;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Desglose de emergencias en lotes y publicación de la convocatoria (E2-06 / E2-07).
 */
@SpringBootTest
@AutoConfigureMockMvc
class LoteIntegrationTests {

    private static final String EMERGENCIAS = "/api/emergencias";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void limpiarDatos() {
        jdbcTemplate.update("DELETE FROM ofertas");
        jdbcTemplate.update("DELETE FROM lotes");
        jdbcTemplate.update("DELETE FROM emergencias");
    }

    // ---------- Listado ----------

    @Test
    void listaVaciaSiLaEmergenciaNoTieneLotes() throws Exception {
        long id = registrarEmergencia();

        mockMvc.perform(get(lotes(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listaLosLotesDeLaEmergenciaEnOrdenDeCreacion() throws Exception {
        long id = registrarEmergencia();
        crearLote(id, "PERSONAL_VOLUNTARIO", "Paramédicos", 5, "personas");
        crearLote(id, "ALIMENTOS", "Raciones de alimento", 1000, "raciones");

        mockMvc.perform(get(lotes(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].emergenciaId").value(id))
                .andExpect(jsonPath("$[0].tipoRecurso").value("PERSONAL_VOLUNTARIO"))
                .andExpect(jsonPath("$[0].cantidadRequerida").value(5))
                .andExpect(jsonPath("$[1].tipoRecurso").value("ALIMENTOS"))
                .andExpect(jsonPath("$[1].unidadMedida").value("raciones"))
                .andExpect(jsonPath("$[1].estado").value("BORRADOR"));
    }

    @Test
    void noMezclaLotesDeOtrasEmergencias() throws Exception {
        long primera = registrarEmergencia();
        long segunda = registrarEmergencia();
        crearLote(primera, "AGUA_POTABLE", "Bidones de agua", 200, "bidones");
        crearLote(segunda, "MEDICAMENTOS", "Botiquines", 30, "unidades");

        mockMvc.perform(get(lotes(primera)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tipoRecurso").value("AGUA_POTABLE"));
    }

    @Test
    void devuelve404SiLaEmergenciaNoExiste() throws Exception {
        mockMvc.perform(get(lotes(999999)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ---------- Desglose (BORRADOR) ----------

    @Test
    void elPrimerLotePasaLaEmergenciaAEnRevision() throws Exception {
        long id = registrarEmergencia();
        crearLote(id, "ALIMENTOS", "Raciones", 1000, "raciones");

        mockMvc.perform(get(EMERGENCIAS + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_REVISION"));
    }

    @Test
    void editaUnLoteEnBorrador() throws Exception {
        long id = registrarEmergencia();
        long loteId = crearLote(id, "ALIMENTOS", "Raciones", 1000, "raciones");

        mockMvc.perform(put(lotes(id) + "/" + loteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loteJson("AGUA_POTABLE", "  Bidones de 5 L  ", 300, " bidones ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(loteId))
                .andExpect(jsonPath("$.tipoRecurso").value("AGUA_POTABLE"))
                .andExpect(jsonPath("$.descripcion").value("Bidones de 5 L"))
                .andExpect(jsonPath("$.cantidadRequerida").value(300))
                .andExpect(jsonPath("$.unidadMedida").value("bidones"))
                .andExpect(jsonPath("$.estado").value("BORRADOR"));

        Map<String, Object> fila = jdbcTemplate.queryForMap("SELECT * FROM lotes WHERE id = ?", loteId);
        assertThat(fila).containsEntry("tipo_recurso", "AGUA_POTABLE")
                .containsEntry("cantidad_requerida", 300);
    }

    @Test
    void editarValidaLosCampos() throws Exception {
        long id = registrarEmergencia();
        long loteId = crearLote(id, "ALIMENTOS", "Raciones", 1000, "raciones");

        mockMvc.perform(put(lotes(id) + "/" + loteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loteJson("ALIMENTOS", "Raciones", 0, "raciones")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void borraUnLoteEnBorrador() throws Exception {
        long id = registrarEmergencia();
        long aBorrar = crearLote(id, "ALIMENTOS", "Raciones", 1000, "raciones");
        crearLote(id, "MEDICAMENTOS", "Botiquines", 30, "unidades");

        mockMvc.perform(delete(lotes(id) + "/" + aBorrar))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(lotes(id)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tipoRecurso").value("MEDICAMENTOS"));
    }

    @Test
    void noSePuedeTocarUnLoteDesdeOtraEmergencia() throws Exception {
        long primera = registrarEmergencia();
        long segunda = registrarEmergencia();
        long loteId = crearLote(primera, "ALIMENTOS", "Raciones", 1000, "raciones");

        mockMvc.perform(put(lotes(segunda) + "/" + loteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loteJson("ALIMENTOS", "Raciones", 5, "raciones")))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(lotes(segunda) + "/" + loteId))
                .andExpect(status().isNotFound());
    }

    // ---------- Publicación de la convocatoria ----------

    @Test
    void publicarPasaEmergenciaYLotesAPublicado() throws Exception {
        long id = registrarEmergencia();
        crearLote(id, "PERSONAL_VOLUNTARIO", "Paramédicos", 5, "personas");
        crearLote(id, "ALIMENTOS", "Raciones", 1000, "raciones");

        mockMvc.perform(post(convocatoria(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.estado").value("CONVOCATORIA_PUBLICADA"));

        mockMvc.perform(get(lotes(id)))
                .andExpect(jsonPath("$[0].estado").value("PUBLICADO"))
                .andExpect(jsonPath("$[1].estado").value("PUBLICADO"));
    }

    @Test
    void noSePuedePublicarSinLotes() throws Exception {
        long id = registrarEmergencia();

        mockMvc.perform(post(convocatoria(id)))
                .andExpect(status().isConflict());
        mockMvc.perform(get(EMERGENCIAS + "/" + id))
                .andExpect(jsonPath("$.estado").value("REGISTRADA"));
    }

    @Test
    void noSePuedePublicarDosVeces() throws Exception {
        long id = registrarEmergencia();
        crearLote(id, "ALIMENTOS", "Raciones", 1000, "raciones");
        mockMvc.perform(post(convocatoria(id))).andExpect(status().isOk());

        mockMvc.perform(post(convocatoria(id)))
                .andExpect(status().isConflict());
    }

    @Test
    void publicarUnaEmergenciaInexistenteDevuelve404() throws Exception {
        mockMvc.perform(post(convocatoria(999999)))
                .andExpect(status().isNotFound());
    }

    @Test
    void despuesDePublicarNoSePuedenModificarLotes() throws Exception {
        long id = registrarEmergencia();
        long loteId = crearLote(id, "ALIMENTOS", "Raciones", 1000, "raciones");
        mockMvc.perform(post(convocatoria(id))).andExpect(status().isOk());

        mockMvc.perform(post(lotes(id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loteJson("MEDICAMENTOS", "Botiquines", 30, "unidades")))
                .andExpect(status().isConflict());
        mockMvc.perform(put(lotes(id) + "/" + loteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loteJson("ALIMENTOS", "Raciones", 5, "raciones")))
                .andExpect(status().isConflict());
        mockMvc.perform(delete(lotes(id) + "/" + loteId))
                .andExpect(status().isConflict());
    }

    // ---------- Ofertas según el estado del lote ----------

    @Test
    void soloSeOfertaSobreLotesPublicados() throws Exception {
        long id = registrarEmergencia();
        long loteId = crearLote(id, "ALIMENTOS", "Raciones", 1000, "raciones");
        String oferta = """
                {"ongNombre":"Cáritas","cantidadOfrecida":200,"observaciones":"Entrega en 24 h"}
                """;

        mockMvc.perform(post("/api/lotes/" + loteId + "/ofertas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oferta))
                .andExpect(status().isConflict());

        mockMvc.perform(post(convocatoria(id))).andExpect(status().isOk());

        mockMvc.perform(post("/api/lotes/" + loteId + "/ofertas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oferta))
                .andExpect(status().isCreated());
    }

    // ---------- Helpers ----------

    private static String lotes(long emergenciaId) {
        return EMERGENCIAS + "/" + emergenciaId + "/lotes";
    }

    private static String convocatoria(long emergenciaId) {
        return EMERGENCIAS + "/" + emergenciaId + "/convocatoria";
    }

    private long registrarEmergencia() throws Exception {
        MvcResult result = mockMvc.perform(post(EMERGENCIAS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipoDesastre":"INUNDACION","nivelGravedad":"ALTO",
                                 "zonaAfectada":"Tolosa","descripcion":"Calles anegadas.",
                                 "municipio":"La Plata"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String loteJson(String tipo, String descripcion, int cantidad, String unidad) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "tipoRecurso", tipo,
                "descripcion", descripcion,
                "cantidadRequerida", cantidad,
                "unidadMedida", unidad));
    }

    private long crearLote(long emergenciaId, String tipo, String descripcion,
                           int cantidad, String unidad) throws Exception {
        MvcResult result = mockMvc.perform(post(lotes(emergenciaId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loteJson(tipo, descripcion, cantidad, unidad)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
}
