package com.grupo21.rescuesync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EmergenciaIntegrationTests {

    private static final String URL = "/api/emergencias";
    private static final String ALTA_VALIDA = """
            {
              "tipoDesastre": "INUNDACION",
              "nivelGravedad": "ALTO",
              "zonaAfectada": "Tolosa",
              "descripcion": "Calles anegadas y familias que necesitan asistencia.",
              "municipio": "La Plata"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Sin @Transactional en el test: cada petición confirma su transacción y luego
    // verificamos los datos por JDBC, sin depender del contexto de persistencia JPA.
    @AfterEach
    void limpiarDatos() {
        jdbcTemplate.update("DELETE FROM lotes");
        jdbcTemplate.update("DELETE FROM emergencias");
    }

    @Test
    void altaPersisteTodosLosDatosYPermiteConsultarlosYListarlos() throws Exception {
        Instant antesDelAlta = Instant.now();
        MvcResult result = mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ALTA_VALIDA))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.tipoDesastre").value("INUNDACION"))
                .andExpect(jsonPath("$.nivelGravedad").value("ALTO"))
                .andExpect(jsonPath("$.zonaAfectada").value("Tolosa"))
                .andExpect(jsonPath("$.descripcion").value("Calles anegadas y familias que necesitan asistencia."))
                .andExpect(jsonPath("$.municipio").value("La Plata"))
                .andExpect(jsonPath("$.estado").value("REGISTRADA"))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        long id = response.get("id").asLong();
        assertThat(id).isPositive();
        assertThat(result.getResponse().getHeader("Location")).isEqualTo(URL + "/" + id);
        assertThat(Instant.parse(response.get("createdAt").asText())).isBetween(antesDelAlta, Instant.now());
        assertThat(response.get("bonitaCaseId").isNull()).isTrue();
        assertThat(cantidadEmergencias()).isEqualTo(1);

        Map<String, Object> fila = jdbcTemplate.queryForMap("SELECT * FROM emergencias WHERE id = ?", id);
        assertThat(fila).containsEntry("tipo_desastre", "INUNDACION")
                .containsEntry("nivel_gravedad", "ALTO")
                .containsEntry("zona_afectada", "Tolosa")
                .containsEntry("descripcion", response.get("descripcion").asText())
                .containsEntry("municipio", "La Plata")
                .containsEntry("estado", "REGISTRADA")
                .containsEntry("bonita_case_id", null);
        assertThat(fila.get("created_at")).isNotNull();
        assertThat(fila.get("updated_at")).isEqualTo(fila.get("created_at"));

        mockMvc.perform(get(result.getResponse().getHeader("Location")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.municipio").value("La Plata"))
                .andExpect(jsonPath("$.descripcion").value(response.get("descripcion").asText()));
        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id));
    }

    @ParameterizedTest
    @CsvSource({"INUNDACION, BAJO", "INCENDIO, MEDIO", "SISMO, ALTO", "TORMENTA_SEVERA, CRITICO",
            "SEQUIA, BAJO", "DESLIZAMIENTO, MEDIO", "OTRO, ALTO"})
    void aceptaLosValoresDelCatalogoActual(String tipoDesastre, String nivelGravedad) throws Exception {
        ObjectNode request = altaValida();
        request.put("tipoDesastre", tipoDesastre);
        request.put("nivelGravedad", nivelGravedad);
        JsonNode response = registrar(request);

        mockMvc.perform(get(URL + "/" + response.get("id").asLong()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoDesastre").value(tipoDesastre))
                .andExpect(jsonPath("$.nivelGravedad").value(nivelGravedad));
    }

    @Test
    void aceptaLasLongitudesMaximasSinTruncar() throws Exception {
        ObjectNode request = altaValida();
        request.put("municipio", "M".repeat(150));
        request.put("zonaAfectada", "Z".repeat(200));
        JsonNode response = registrar(request);

        mockMvc.perform(get(URL + "/" + response.get("id").asLong()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.municipio").value(request.get("municipio").asText()))
                .andExpect(jsonPath("$.zonaAfectada").value(request.get("zonaAfectada").asText()));
    }

    @ParameterizedTest
    @MethodSource("camposObligatorios")
    void rechazaCamposAusentesONulosSinPersistir(String campo, boolean nulo) throws Exception {
        ObjectNode request = altaValida();
        if (nulo) {
            request.putNull(campo);
        } else {
            request.remove(campo);
        }
        verificarErrorDeCampo(request, campo);
    }

    static Stream<Arguments> camposObligatorios() {
        return Stream.of("tipoDesastre", "nivelGravedad", "zonaAfectada", "descripcion", "municipio")
                .flatMap(campo -> Stream.of(Arguments.of(campo, false), Arguments.of(campo, true)));
    }

    @ParameterizedTest
    @MethodSource("textosInvalidos")
    void rechazaTextosInvalidosSinPersistir(String campo, String valor) throws Exception {
        ObjectNode request = altaValida();
        request.put(campo, valor);
        verificarErrorDeCampo(request, campo);
    }

    static Stream<Arguments> textosInvalidos() {
        Stream<Arguments> vacios = Stream.of("municipio", "zonaAfectada", "descripcion")
                .flatMap(campo -> Stream.of(Arguments.of(campo, ""), Arguments.of(campo, " \t\n ")));
        return Stream.concat(vacios, Stream.of(
                Arguments.of("municipio", "M".repeat(151)),
                Arguments.of("zonaAfectada", "Z".repeat(201))
        ));
    }

    @ParameterizedTest
    @MethodSource("enumsInvalidos")
    void rechazaEnumsNumericosODesconocidosSinPersistir(String campo, String valorJson) throws Exception {
        ObjectNode request = altaValida();
        request.set(campo, objectMapper.readTree(valorJson));

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.path").value(URL));
        assertThat(cantidadEmergencias()).isZero();
    }

    static Stream<Arguments> enumsInvalidos() {
        return Stream.of("tipoDesastre", "nivelGravedad")
                .flatMap(campo -> Stream.of("0", "\"0\"", "\"DESCONOCIDO\"", "\"\"")
                        .map(valor -> Arguments.of(campo, valor)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{", "[]"})
    void rechazaCuerposAusentesOMalformadosSinPersistir(String cuerpo) throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value(URL));
        assertThat(cantidadEmergencias()).isZero();
    }

    @Test
    void rechazaContenidoNoSoportadoCon415SinPersistir() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.TEXT_PLAIN).content(ALTA_VALIDA))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.error").value("Unsupported Media Type"))
                .andExpect(jsonPath("$.message").value("El tipo de contenido de la petición no está soportado"))
                .andExpect(jsonPath("$.path").value(URL));
        assertThat(cantidadEmergencias()).isZero();
    }

    @Test
    void rechazaAcceptIncompatibleCon406AntesDeGuardar() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_XML)
                        .content(ALTA_VALIDA))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(406))
                .andExpect(jsonPath("$.error").value("Not Acceptable"))
                .andExpect(jsonPath("$.message").value("El formato de respuesta solicitado no está soportado"))
                .andExpect(jsonPath("$.path").value(URL));
        assertThat(cantidadEmergencias()).isZero();
    }

    @Test
    void elClienteNoPuedeSobrescribirUnaEmergenciaNiAsignarEstadoAuditoriaOBonita() throws Exception {
        JsonNode original = registrar(altaValida());
        long idOriginal = original.get("id").asLong();
        Instant antesDelAlta = Instant.now();
        ObjectNode request = altaValida();
        request.put("id", idOriginal);
        request.put("municipio", "Berisso");
        request.put("estado", "CERRADA");
        request.put("bonitaCaseId", 123);
        request.put("createdAt", "2000-01-01T00:00:00Z");
        request.put("updatedAt", "2000-01-01T00:00:00Z");

        JsonNode nueva = registrar(request);
        assertThat(nueva.get("id").asLong()).isNotEqualTo(idOriginal);
        assertThat(nueva.get("estado").asText()).isEqualTo("REGISTRADA");
        assertThat(nueva.get("bonitaCaseId").isNull()).isTrue();
        assertThat(Instant.parse(nueva.get("createdAt").asText())).isBetween(antesDelAlta, Instant.now());
        assertThat(cantidadEmergencias()).isEqualTo(2);
        mockMvc.perform(get(URL + "/" + idOriginal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.municipio").value("La Plata"))
                .andExpect(jsonPath("$.estado").value("REGISTRADA"));
    }

    @Test
    void devuelve404SiNoExisteLaEmergencia() throws Exception {
        mockMvc.perform(get(URL + "/" + Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value(URL + "/" + Long.MAX_VALUE));
    }

    @Test
    void devuelve400SiElIdNoEsNumerico() throws Exception {
        mockMvc.perform(get(URL + "/invalido"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value(URL + "/invalido"));
    }

    @Test
    void laEmergenciaCreadaPermiteRegistrarLotes() throws Exception {
        long id = registrar(altaValida()).get("id").asLong();
        mockMvc.perform(post(URL + "/" + id + "/lotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipoRecurso":"ALIMENTOS","descripcion":"Raciones de alimento",
                                 "cantidadRequerida":1000,"unidadMedida":"raciones"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.emergenciaId").value(id))
                .andExpect(jsonPath("$.tipoRecurso").value("ALIMENTOS"))
                .andExpect(jsonPath("$.estado").value("PUBLICADO"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lotes WHERE emergencia_id = ?", Long.class, id)).isEqualTo(1);
        mockMvc.perform(get(URL + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void swaggerDocumenta201YElDtoDeRespuesta() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn();
        JsonNode api = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(api.at("/paths/~1api~1emergencias/post/responses/201/content/application~1json/schema/$ref")
                .asText()).isEqualTo("#/components/schemas/EmergenciaResponse");
    }

    private ObjectNode altaValida() throws Exception {
        return (ObjectNode) objectMapper.readTree(ALTA_VALIDA);
    }

    private JsonNode registrar(ObjectNode request) throws Exception {
        MvcResult result = mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void verificarErrorDeCampo(ObjectNode request, String campo) throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Datos inválidos"))
                .andExpect(jsonPath("$.path").value(URL))
                .andExpect(jsonPath("$.fieldErrors." + campo).isNotEmpty());
        assertThat(cantidadEmergencias()).isZero();
    }

    private long cantidadEmergencias() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM emergencias", Long.class);
    }
}
