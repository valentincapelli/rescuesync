package com.grupo21.rescuesync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grupo21.rescuesync.model.Rol;
import com.grupo21.rescuesync.model.Usuario;
import com.grupo21.rescuesync.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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
@SpringBootTest(properties = {
                "jwt.secret=test-signing-key-for-integration-tests-32bytes",
                "jwt.expiration=86400000"
})
@AutoConfigureMockMvc
class LoteIntegrationTests {

    private static final String EMERGENCIAS = "/api/emergencias";
        private static final String EMAIL_OPERADOR = "operador-lotes-test@rescuesync.local";
        private static final String EMAIL_CENTRO = "centro-lotes-test@rescuesync.local";
        private static final String EMAIL_ONG = "ong-lotes-test@rescuesync.local";
        private static final String NOMBRE_MUNICIPIO = "La Plata";
        private static final String NOMBRE_ONG = "ONG de prueba";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    private Long municipioId;
    private Long ongId;
    private String tokenOperador;
    private String tokenCentro;
    private String tokenOng;

    @BeforeEach
    void prepararUsuariosYEntidades() {
        jdbcTemplate.update("INSERT INTO municipios (nombre, created_at, updated_at) "
                + "VALUES (?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", NOMBRE_MUNICIPIO);
        municipioId = jdbcTemplate.queryForObject(
                "SELECT id FROM municipios WHERE nombre = ?", Long.class, NOMBRE_MUNICIPIO);
        jdbcTemplate.update("INSERT INTO ongs (nombre, created_at, updated_at) "
                + "VALUES (?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", NOMBRE_ONG);
        ongId = jdbcTemplate.queryForObject("SELECT id FROM ongs WHERE nombre = ?", Long.class, NOMBRE_ONG);

        crearUsuarioMunicipal(EMAIL_OPERADOR, Rol.OPERADOR_MUNICIPAL, municipioId);
        crearUsuario(EMAIL_CENTRO, Rol.CENTRO_COORDINADOR);
        crearUsuarioOng(EMAIL_ONG, Rol.REPRESENTANTE_ONG, ongId);
        tokenOperador = crearToken(EMAIL_OPERADOR, Rol.OPERADOR_MUNICIPAL);
        tokenCentro = crearToken(EMAIL_CENTRO, Rol.CENTRO_COORDINADOR);
        tokenOng = crearToken(EMAIL_ONG, Rol.REPRESENTANTE_ONG);
    }

    @AfterEach
    void limpiarDatos() {
        jdbcTemplate.update("DELETE FROM ofertas");
        jdbcTemplate.update("DELETE FROM lotes");
        jdbcTemplate.update("DELETE FROM emergencias");
                jdbcTemplate.update("DELETE FROM usuarios WHERE email IN (?, ?, ?)", EMAIL_OPERADOR, EMAIL_CENTRO, EMAIL_ONG);
                jdbcTemplate.update("DELETE FROM municipios WHERE nombre = ?", NOMBRE_MUNICIPIO);
                jdbcTemplate.update("DELETE FROM ongs WHERE nombre = ?", NOMBRE_ONG);
    }

    // ---------- Listado ----------

    @Test
    void listaVaciaSiLaEmergenciaNoTieneLotes() throws Exception {
        long id = registrarEmergencia();

        mockMvc.perform(conTokenCentro(get(lotes(id))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listaLosLotesDeLaEmergenciaEnOrdenDeCreacion() throws Exception {
        long id = registrarEmergencia();
        crearLote(id, "PERSONAL_VOLUNTARIO", "Paramédicos", 5, "personas");
        crearLote(id, "ALIMENTOS", "Raciones de alimento", 1000, "raciones");

        mockMvc.perform(conTokenCentro(get(lotes(id))))
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

        mockMvc.perform(conTokenCentro(get(lotes(primera))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tipoRecurso").value("AGUA_POTABLE"));
    }

    @Test
    void devuelve404SiLaEmergenciaNoExiste() throws Exception {
        mockMvc.perform(conTokenCentro(get(lotes(999999))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ---------- Desglose (BORRADOR) ----------

    @Test
    void elPrimerLotePasaLaEmergenciaAEnRevision() throws Exception {
        long id = registrarEmergencia();
        crearLote(id, "ALIMENTOS", "Raciones", 1000, "raciones");

        mockMvc.perform(conTokenCentro(get(EMERGENCIAS + "/" + id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_REVISION"));
    }

    @Test
    void editaUnLoteEnBorrador() throws Exception {
        long id = registrarEmergencia();
        long loteId = crearLote(id, "ALIMENTOS", "Raciones", 1000, "raciones");

        mockMvc.perform(conTokenCentro(put(lotes(id) + "/" + loteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loteJson("AGUA_POTABLE", "  Bidones de 5 L  ", 300, " bidones "))))
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

        mockMvc.perform(conTokenCentro(put(lotes(id) + "/" + loteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loteJson("ALIMENTOS", "Raciones", 0, "raciones"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void borraUnLoteEnBorrador() throws Exception {
        long id = registrarEmergencia();
        long aBorrar = crearLote(id, "ALIMENTOS", "Raciones", 1000, "raciones");
        crearLote(id, "MEDICAMENTOS", "Botiquines", 30, "unidades");

        mockMvc.perform(conTokenCentro(delete(lotes(id) + "/" + aBorrar)))
                .andExpect(status().isNoContent());

        mockMvc.perform(conTokenCentro(get(lotes(id))))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tipoRecurso").value("MEDICAMENTOS"));
    }

    @Test
    void noSePuedeTocarUnLoteDesdeOtraEmergencia() throws Exception {
        long primera = registrarEmergencia();
        long segunda = registrarEmergencia();
        long loteId = crearLote(primera, "ALIMENTOS", "Raciones", 1000, "raciones");

        mockMvc.perform(conTokenCentro(put(lotes(segunda) + "/" + loteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loteJson("ALIMENTOS", "Raciones", 5, "raciones"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(conTokenCentro(delete(lotes(segunda) + "/" + loteId)))
                .andExpect(status().isNotFound());
    }

    // ---------- Publicación de la convocatoria ----------

    @Test
    void publicarPasaEmergenciaYLotesAPublicado() throws Exception {
        long id = registrarEmergencia();
        crearLote(id, "PERSONAL_VOLUNTARIO", "Paramédicos", 5, "personas");
        crearLote(id, "ALIMENTOS", "Raciones", 1000, "raciones");

        mockMvc.perform(conTokenOperador(post(convocatoria(id))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.estado").value("CONVOCATORIA_PUBLICADA"));

        mockMvc.perform(conTokenCentro(get(lotes(id))))
                .andExpect(jsonPath("$[0].estado").value("PUBLICADO"))
                .andExpect(jsonPath("$[1].estado").value("PUBLICADO"));
    }

    @Test
    void noSePuedePublicarSinLotes() throws Exception {
        long id = registrarEmergencia();

        mockMvc.perform(conTokenOperador(post(convocatoria(id))))
                .andExpect(status().isConflict());
        mockMvc.perform(conTokenCentro(get(EMERGENCIAS + "/" + id)))
                .andExpect(jsonPath("$.estado").value("REGISTRADA"));
    }

    @Test
    void noSePuedePublicarDosVeces() throws Exception {
        long id = registrarEmergencia();
        crearLote(id, "ALIMENTOS", "Raciones", 1000, "raciones");
        mockMvc.perform(conTokenOperador(post(convocatoria(id)))).andExpect(status().isOk());

        mockMvc.perform(conTokenOperador(post(convocatoria(id))))
                .andExpect(status().isConflict());
    }

    @Test
    void publicarUnaEmergenciaInexistenteDevuelve404() throws Exception {
        mockMvc.perform(conTokenOperador(post(convocatoria(999999))))
                .andExpect(status().isNotFound());
    }

    @Test
    void despuesDePublicarNoSePuedenModificarLotes() throws Exception {
        long id = registrarEmergencia();
        long loteId = crearLote(id, "ALIMENTOS", "Raciones", 1000, "raciones");
        mockMvc.perform(conTokenOperador(post(convocatoria(id)))).andExpect(status().isOk());

        mockMvc.perform(conTokenCentro(post(lotes(id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loteJson("MEDICAMENTOS", "Botiquines", 30, "unidades"))))
                .andExpect(status().isConflict());
        mockMvc.perform(conTokenCentro(put(lotes(id) + "/" + loteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loteJson("ALIMENTOS", "Raciones", 5, "raciones"))))
                .andExpect(status().isConflict());
        mockMvc.perform(conTokenCentro(delete(lotes(id) + "/" + loteId)))
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

        mockMvc.perform(conTokenOng(post("/api/lotes/" + loteId + "/ofertas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oferta)))
                .andExpect(status().isConflict());

        mockMvc.perform(conTokenOperador(post(convocatoria(id)))).andExpect(status().isOk());

        mockMvc.perform(conTokenOng(post("/api/lotes/" + loteId + "/ofertas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oferta)))
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
                        .header("Authorization", "Bearer " + tokenOperador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipoDesastre":"INUNDACION","nivelGravedad":"ALTO",
                                 "zonaAfectada":"Tolosa","descripcion":"Calles anegadas."}
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
                        .header("Authorization", "Bearer " + tokenCentro)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loteJson(tipo, descripcion, cantidad, unidad)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

        private MockHttpServletRequestBuilder conTokenCentro(MockHttpServletRequestBuilder request) {
                return request.header("Authorization", "Bearer " + tokenCentro);
        }

        private MockHttpServletRequestBuilder conTokenOperador(MockHttpServletRequestBuilder request) {
                return request.header("Authorization", "Bearer " + tokenOperador);
        }

        private MockHttpServletRequestBuilder conTokenOng(MockHttpServletRequestBuilder request) {
                return request.header("Authorization", "Bearer " + tokenOng);
        }

        private void crearUsuario(String email, Rol rol) {
                jdbcTemplate.update("INSERT INTO usuarios (email, password_hash, rol, created_at, updated_at) "
                                + "VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", email, "test", rol.name());
        }

        private void crearUsuarioMunicipal(String email, Rol rol, Long idMunicipio) {
                jdbcTemplate.update("INSERT INTO usuarios "
                                                + "(email, password_hash, rol, municipio_id, created_at, updated_at) "
                                                + "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                                email, "test", rol.name(), idMunicipio);
        }

        private void crearUsuarioOng(String email, Rol rol, Long idOng) {
                jdbcTemplate.update("INSERT INTO usuarios "
                                                + "(email, password_hash, rol, ong_id, created_at, updated_at) "
                                                + "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                                email, "test", rol.name(), idOng);
        }

        private String crearToken(String email, Rol rol) {
                Usuario usuario = new Usuario();
                usuario.setEmail(email);
                usuario.setRol(rol);
                return jwtService.generarToken(usuario);
        }
}
