package com.grupo21.rescuesync.bonita;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bonitasoft.engine.connector.ConnectorException;
import org.bonitasoft.engine.connector.ConnectorValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SistemaNacionalConnectorTest {

    private static final String VALIDAR = "/api/certificaciones/validar";
    private static final String COMPROMETER = "/api/compromisos";
    private static final String LIBERAR = "/api/compromisos/liberar";
    private static final String CUIT = "30500000001";
    private static final String OFERTAS = """
            [{"id":3,"tipoRecurso":"ALIMENTOS","unidadMedida":" KG ","ofertas":[
              {"id":10,"ong":{"id":4,"nombre":"ONG local","cuit":"30500000001"},"cantidadOfrecida":7},
              {"id":11,"ong":{"id":4,"nombre":"ONG local","cuit":"30500000001"},"cantidadOfrecida":2}]},
             {"id":5,"tipoRecurso":"AGUA_POTABLE","unidadMedida":"litros","ofertas":[]}]
            """;
    private HttpServer server;
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final Map<String, Integer> statuses = new ConcurrentHashMap<>();
    private final Map<String, String> bodies = new ConcurrentHashMap<>();
    private final AtomicInteger logins = new AtomicInteger();
    private final AtomicInteger reservations = new AtomicInteger();
    private final AtomicInteger releases = new AtomicInteger();
    private boolean issueCookie = true;
    private boolean expireOnce;
    private boolean lostCommitResponse;
    private boolean alwaysTransient;
    private long delayMillis;

    @BeforeEach
    void prepare() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/", this::handle);
        bodies.put("/api/auth/login", "{\"username\":\"bonita.test\",\"rol\":\"SERVICIO\"}");
        bodies.put(VALIDAR, perfil(false));
        bodies.put("/api/ongs", """
                [{"id":77,"cuit":"30500000001","estadoHabilitacion":"HABILITADA"},
                 {"id":4,"cuit":"30500000002","estadoHabilitacion":"HABILITADA"}]
                """);
        bodies.put("/api/ongs/77/recursos", """
                [{"id":900,"ongId":77,"tipoRecurso":"ALIMENTOS","unidadMedida":"kg","cantidadDisponible":0},
                 {"id":3,"ongId":77,"tipoRecurso":"ALIMENTOS","unidadMedida":"raciones","cantidadDisponible":100}]
                """);
        bodies.put(COMPROMETER, """
                {"id":123,"recursoId":900,"emergenciaRef":42,"ofertaRef":10,"cantidad":7,"estado":"COMPROMETIDO"}
                """);
        bodies.put(LIBERAR, """
                {"emergenciaRef":42,"ofertaRef":10,"liberados":1,"yaLiberados":0,
                 "compromisos":[{"compromisoId":123,"ofertaRef":10,"recursoId":900,"estado":"LIBERADO"}]}
                """);
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new Request(path, exchange.getRequestMethod(), body,
                exchange.getRequestHeaders().getFirst("Cookie")));
        int status = statuses.getOrDefault(path, path.equals(COMPROMETER) ? 201 : 200);
        if (path.equals("/api/auth/login")) {
            int login = logins.incrementAndGet();
            if (issueCookie) {
                exchange.getResponseHeaders().add("Set-Cookie", "access_token=token-" + login + "; HttpOnly; Path=/");
            }
        } else if (expireOnce) {
            expireOnce = false;
            respond(exchange, 401, "{}");
            return;
        }
        if (path.equals(COMPROMETER) && status == 201) {
            reservations.compareAndSet(0, 1); // Simula la clave idempotente nacional (recursoId, ofertaRef).
            if (lostCommitResponse) {
                lostCommitResponse = false;
                respond(exchange, 503, "respuesta perdida después de confirmar la reserva");
                return;
            }
        }
        if (path.equals(LIBERAR) && status == 200) {
            releases.compareAndSet(0, 1);
        }
        if (delayMillis > 0) {
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        try {
            respond(exchange, alwaysTransient ? 503 : status, bodies.getOrDefault(path, "{}"));
        } catch (IOException ignored) {
            // La prueba de timeout puede haber cerrado la conexión.
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private Map<String, Object> inputs(String operation, String ofertas) {
        return new HashMap<>(Map.of("operacion", operation, "emergenciaId", 42L,
                "sistemaNacionalBaseUrl", "http://127.0.0.1:" + server.getAddress().getPort(),
                "ofertasJson", ofertas, "ofertaSeleccionadaId", 10L, "ofertasProcesadasJson", "[]"));
    }

    private SistemaNacionalConnector connector(Map<String, Object> inputs) {
        SistemaNacionalConnector connector = new SistemaNacionalConnector(
                name -> name.equals(SistemaNacionalClient.USERNAME_ENV) ? "bonita.test" : "password-test",
                Duration.ofSeconds(2), millis -> {});
        connector.setInputParameters(inputs);
        return connector;
    }

    private String evaluadas() throws ConnectorException {
        return connector(inputs("EVALUAR", OFERTAS)).execute().get("resultadoJson").toString();
    }

    private List<Request> calls(String path) {
        return requests.stream().filter(r -> r.path().equals(path)).toList();
    }

    private static String perfil(boolean empty) {
        return "{\"cuit\":\"" + CUIT + "\",\"tipoRecurso\":\"ALIMENTOS\",\"estadoHabilitacion\":\"HABILITADA\","
                + "\"capacidadesHabilitadas\":" + (empty ? "[]" : "[{\"tipoRecurso\":\"ALIMENTOS\","
                + "\"nivelCompetencia\":\"BASICO\",\"nivelRiesgoMax\":\"BAJO\"}]") + "}";
    }

    @Test
    void evaluaCadaOfertaConCuitConservaLotesVaciosYNuncaReserva() throws Exception {
        var result = connector(inputs("EVALUAR", OFERTAS)).execute();
        JsonNode lotes = Jsons.parse(result.get("resultadoJson").toString(), "resultado");
        assertThat(lotes.size()).isEqualTo(2);
        assertThat(lotes.get(1).path("ofertas").size()).isZero();
        assertThat(lotes.get(0).path("ofertas").get(0).path("validacion")
                .path("capacidadesHabilitadas").get(0).path("nivelCompetencia").asText()).isEqualTo("BASICO");
        assertThat(result).containsEntry("hayOfertasPendientes", true).containsEntry("ofertasProcesadasJson", "[]");
        assertThat(calls(VALIDAR)).hasSize(2).allSatisfy(r -> {
            assertThat(r.method()).isEqualTo("POST");
            assertThat(r.body()).isEqualTo("{\"cuit\":\"30500000001\",\"tipoRecurso\":\"ALIMENTOS\"}");
            assertThat(r.cookie()).contains("access_token=token-1");
        });
        assertThat(calls("/api/auth/login").get(0).body())
                .isEqualTo("{\"username\":\"bonita.test\",\"password\":\"password-test\"}");
        assertThat(calls(COMPROMETER)).isEmpty();
    }

    @Test
    void capacidadVaciaNoEliminaNiRechazaLaOferta() throws Exception {
        bodies.put(VALIDAR, perfil(true));
        JsonNode lotes = Jsons.parse(evaluadas(), "resultado");
        assertThat(lotes.get(0).path("ofertas").size()).isEqualTo(2);
        assertThat(lotes.get(0).path("ofertas").get(0).path("validacion").path("capacidadesHabilitadas").size()).isZero();
    }

    @Test
    void sinOfertasNoHayPendientes() throws Exception {
        assertThat(connector(inputs("EVALUAR", "[]")).execute()).containsEntry("resultadoJson", "[]")
                .containsEntry("hayOfertasPendientes", false);
        assertThat(calls(VALIDAR)).isEmpty();
    }

    @Test
    void comprometeSoloLaSeleccionadaUsandoLosIdsNacionalesYLaUnidadNormalizada() throws Exception {
        connector(inputs("COMPROMETER", evaluadas())).execute();
        assertThat(calls(COMPROMETER)).hasSize(1);
        assertThat(Jsons.parse(calls(COMPROMETER).get(0).body(), "request"))
                .isEqualTo(Jsons.parse("{\"recursoId\":900,\"emergenciaRef\":42,\"ofertaRef\":10,\"cantidad\":7}", "expected"));
        assertThat(calls("/api/ongs/4/recursos")).isEmpty();
        assertThat(reservations).hasValue(1);
    }

    @Test
    void reintentaExactamenteElMismoCompromisoAnteRespuestaPerdida() throws Exception {
        String ofertas = evaluadas();
        lostCommitResponse = true;
        connector(inputs("COMPROMETER", ofertas)).execute();
        assertThat(calls(COMPROMETER)).hasSize(2);
        assertThat(calls(COMPROMETER).get(0).body()).isEqualTo(calls(COMPROMETER).get(1).body());
        assertThat(reservations).hasValue(1);
    }

    @Test
    void stockInsuficienteEsIncidenteYNoSeReintenta() throws Exception {
        String ofertas = evaluadas();
        statuses.put(COMPROMETER, 409);
        bodies.put(COMPROMETER, "no revelar cuerpo password-test token-1");
        ConnectorException error = assertThrows(ConnectorException.class,
                () -> connector(inputs("COMPROMETER", ofertas)).execute());
        assertThat(error.getMessage()).contains("409").doesNotContain("password-test", "token-1");
        assertThat(calls(COMPROMETER)).hasSize(1);
        assertThat(reservations).hasValue(0);
    }

    @Test
    void noAdivinaLaOngNiConvierteUnidadesDistintas() throws Exception {
        String ofertas = evaluadas();
        bodies.put("/api/ongs/77/recursos", "[{\"id\":3,\"ongId\":77,\"tipoRecurso\":\"ALIMENTOS\",\"unidadMedida\":\"raciones\"}]");
        assertThrows(ConnectorException.class, () -> connector(inputs("COMPROMETER", ofertas)).execute());
        bodies.put("/api/ongs", "[{\"id\":4,\"cuit\":\"30500000002\",\"estadoHabilitacion\":\"HABILITADA\"}]");
        assertThrows(ConnectorException.class, () -> connector(inputs("COMPROMETER", ofertas)).execute());
        assertThat(calls(COMPROMETER)).isEmpty();
    }

    @Test
    void noAceptaUnRecursoDeOtraOngNiResultadosDeOtroCompromiso() throws Exception {
        String ofertas = evaluadas();
        String original = bodies.get("/api/ongs/77/recursos");
        bodies.put("/api/ongs/77/recursos", original.replace("\"ongId\":77", "\"ongId\":99"));
        assertThrows(ConnectorException.class, () -> connector(inputs("COMPROMETER", ofertas)).execute());
        assertThat(calls(COMPROMETER)).isEmpty();
        bodies.put("/api/ongs/77/recursos", original);
        bodies.put(COMPROMETER, bodies.get(COMPROMETER).replace("\"emergenciaRef\":42", "\"emergenciaRef\":99"));
        assertThrows(ConnectorException.class, () -> connector(inputs("COMPROMETER", ofertas)).execute());
    }

    @Test
    void liberaSoloLaOfertaElegidaYUnReintentoNoDuplicaLasProcesadas() throws Exception {
        String ofertas = evaluadas();
        connector(inputs("COMPROMETER", ofertas)).execute();
        var in = inputs("LIBERAR", ofertas);
        var result = connector(in).execute();
        assertThat(result).containsEntry("ofertasProcesadasJson", "[10]").containsEntry("hayOfertasPendientes", true);
        assertThat(calls(LIBERAR).get(0).body()).isEqualTo("{\"emergenciaRef\":42,\"ofertaRef\":10}");
        in.put("ofertasProcesadasJson", "[10,11]");
        bodies.put(LIBERAR, bodies.get(LIBERAR).replace("\"liberados\":1,\"yaLiberados\":0", "\"liberados\":0,\"yaLiberados\":1"));
        assertThat(connector(in).execute()).containsEntry("ofertasProcesadasJson", "[10,11]")
                .containsEntry("hayOfertasPendientes", false);
        assertThat(releases).hasValue(1);
    }

    @Test
    void unaLiberacionIncompletaONoEncontradaNoMarcaLaOfertaComoFinalizada() throws Exception {
        String ofertas = evaluadas();
        bodies.put(LIBERAR, bodies.get(LIBERAR).replace("LIBERADO", "COMPROMETIDO"));
        assertThrows(ConnectorException.class, () -> connector(inputs("LIBERAR", ofertas)).execute());
        statuses.put(LIBERAR, 404);
        assertThat(assertThrows(ConnectorException.class,
                () -> connector(inputs("LIBERAR", ofertas)).execute()).getMessage()).contains("404");
    }

    @Test
    void renuevaUnaSesionVencidaYReenviaLaCookieNueva() throws Exception {
        expireOnce = true;
        evaluadas();
        assertThat(logins).hasValue(2);
        assertThat(calls(VALIDAR)).hasSize(3);
        assertThat(calls(VALIDAR).get(1).cookie()).contains("access_token=token-2");
    }

    @Test
    void limitaLaRenovacionSiElServicioSigueRespondiendo401() {
        statuses.put(VALIDAR, 401);
        assertThrows(ConnectorException.class, this::evaluadas);
        assertThat(logins).hasValue(2);
        assertThat(calls(VALIDAR)).hasSize(2);
    }

    @Test
    void exigeLoginCorrectoCookieYRolServicio() {
        statuses.put("/api/auth/login", 401);
        assertThrows(ConnectorException.class, this::evaluadas);
        statuses.clear();
        issueCookie = false;
        assertThrows(ConnectorException.class, this::evaluadas);
        issueCookie = true;
        bodies.put("/api/auth/login", "{\"rol\":\"ADMIN\"}");
        assertThrows(ConnectorException.class, this::evaluadas);
        assertThat(calls(VALIDAR)).isEmpty();
    }

    @Test
    void limitaLosFallosTransitoriosATresIntentos() {
        alwaysTransient = true;
        assertThat(assertThrows(ConnectorException.class, this::evaluadas).getMessage()).contains("3 intentos");
        assertThat(logins).hasValue(3);
    }

    @Test
    void limitaLosTimeoutsSinExponerCredenciales() {
        delayMillis = 150;
        var connector = new SistemaNacionalConnector(name -> "credencial-no-imprimir", Duration.ofMillis(40), millis -> {});
        connector.setInputParameters(inputs("EVALUAR", OFERTAS));
        assertThat(assertThrows(ConnectorException.class, connector::execute).getMessage())
                .contains("3 intentos").doesNotContain("credencial-no-imprimir");
    }

    @Test
    void rechazaRespuestasInvalidasOMalCorrelacionadas() {
        for (String body : List.of("{}", "{", "[]", perfil(false) + " {}", perfil(false).replace(CUIT, "30500000002"))) {
            bodies.put(VALIDAR, body);
            assertThrows(ConnectorException.class, this::evaluadas);
        }
    }

    @Test
    void validaTodoElJsonAntesDeHacerLlamadas() {
        for (String ofertas : List.of("{}", OFERTAS.replace("\"cuit\":\"30500000001\"", "\"cuit\":null"),
                OFERTAS.replace("30500000001", "123"), OFERTAS.replace("\"id\":11", "\"id\":10"),
                OFERTAS.replace("\"cantidadOfrecida\":7", "\"cantidadOfrecida\":0"), OFERTAS + " []")) {
            assertThrows(ConnectorValidationException.class,
                    () -> connector(inputs("EVALUAR", ofertas)).validateInputParameters());
        }
        assertThat(requests).isEmpty();
    }

    @Test
    void exigeSeleccionDeEstaEmergenciaYNoComprometeUnaOfertaYaFinalizada() throws Exception {
        var in = inputs("COMPROMETER", evaluadas());
        in.put("ofertaSeleccionadaId", 999L);
        assertThrows(ConnectorValidationException.class, () -> connector(in).validateInputParameters());
        in.put("ofertaSeleccionadaId", 10L);
        in.put("ofertasProcesadasJson", "[10]");
        assertThrows(ConnectorException.class, () -> connector(in).execute());
        assertThrows(ConnectorException.class, () -> connector(inputs("COMPROMETER", OFERTAS)).execute());
        assertThat(calls(COMPROMETER)).isEmpty();
    }

    @Test
    void exigeEntornoYOrigenSinCredencialesNiRedirecciones() {
        for (String url : List.of("file:///tmp/test", "https://user:password@example.test", "https://example.test/api", "https://example.test?token=x")) {
            var in = inputs("EVALUAR", OFERTAS);
            in.put("sistemaNacionalBaseUrl", url);
            assertThrows(ConnectorValidationException.class, () -> connector(in).validateInputParameters());
        }
        var sinCredenciales = new SistemaNacionalConnector(name -> null, Duration.ofSeconds(1), millis -> {});
        sinCredenciales.setInputParameters(inputs("EVALUAR", OFERTAS));
        assertThrows(ConnectorException.class, sinCredenciales::execute);
        assertThat(requests).isEmpty();
        statuses.put("/api/auth/login", 302);
        assertThrows(ConnectorException.class, this::evaluadas);
        assertThat(logins).hasValue(1);
    }

    @Test
    void losDescriptoresGeneradosCumplenElEsquemaDeBonita() throws Exception {
        for (String extension : List.of("def", "impl")) {
            String schema = extension.equals("def") ? "definition" : "implementation";
            var validator = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                    .newSchema(Path.of("schemas/connector-" + schema + "-descriptor.xsd").toFile()).newValidator();
            try (var resource = getClass().getResourceAsStream("/bonita-sistema-nacional-connector." + extension)) {
                assertThat(resource).isNotNull();
                validator.validate(new StreamSource(resource));
            }
        }
    }

    private record Request(String path, String method, String body, String cookie) {}
}
