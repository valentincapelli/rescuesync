package com.grupo21.rescuesync.bonita;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bonitasoft.engine.connector.AbstractConnector;
import org.bonitasoft.engine.connector.ConnectorException;
import org.bonitasoft.engine.connector.ConnectorValidationException;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/** E3-10. Una ejecución por tarea: evaluar, comprometer la oferta elegida o liberar esa misma oferta. */
public class SistemaNacionalConnector extends AbstractConnector {

    private final Function<String, String> environment;
    private final Duration timeout;
    private final SistemaNacionalClient.Sleeper sleeper;

    public SistemaNacionalConnector() {
        this(System::getenv, Duration.ofSeconds(90), Thread::sleep);
    }

    SistemaNacionalConnector(Function<String, String> environment, Duration timeout,
                             SistemaNacionalClient.Sleeper sleeper) {
        this.environment = environment;
        this.timeout = timeout;
        this.sleeper = sleeper;
    }

    @Override
    public void validateInputParameters() throws ConnectorValidationException {
        try {
            inputs();
        } catch (ConnectorException e) {
            throw new ConnectorValidationException(this, e.getMessage());
        }
    }

    @Override
    protected void executeBusinessLogic() throws ConnectorException {
        Inputs in = inputs();
        SistemaNacionalClient client = new SistemaNacionalClient(in.base(),
                requiredEnvironment(SistemaNacionalClient.USERNAME_ENV),
                requiredEnvironment(SistemaNacionalClient.PASSWORD_ENV), timeout, sleeper);
        client.login();

        JsonNode result = switch (in.operacion()) {
            case "EVALUAR" -> evaluar(in, client);
            case "COMPROMETER" -> comprometer(in, client);
            case "LIBERAR" -> liberar(in, client);
            default -> throw new ConnectorException("Operación no soportada.");
        };

        // Ninguna salida se publica si falló una llamada o su respuesta no respeta el contrato.
        setOutputParameter("resultadoJson", result.toString());
        setOutputParameter("ofertasProcesadasJson", in.procesadas().toString());
        Set<Long> processed = processedIds(in.procesadas());
        setOutputParameter("hayOfertasPendientes", in.ofertas().stream().anyMatch(o -> !processed.contains(o.id())));
    }

    private JsonNode evaluar(Inputs in, SistemaNacionalClient client) throws ConnectorException {
        for (Oferta oferta : in.ofertas()) {
            ObjectNode request = Jsons.JSON.createObjectNode().put("cuit", oferta.cuit())
                    .put("tipoRecurso", oferta.tipo());
            JsonNode perfil = client.post("/api/certificaciones/validar", request, 200);
            if (!oferta.cuit().equals(perfil.path("cuit").asText())
                    || !oferta.tipo().equals(perfil.path("tipoRecurso").asText())) {
                throw new ConnectorException("La validación nacional no corresponde a la ONG y al tipo consultados.");
            }
            Jsons.array(perfil.get("capacidadesHabilitadas"), "capacidadesHabilitadas");
            // La capacidad vacía es información para el municipio, no un rechazo automático de la oferta.
            oferta.json().set("validacion", perfil);
        }
        return in.lotes();
    }

    private JsonNode comprometer(Inputs in, SistemaNacionalClient client) throws ConnectorException {
        Oferta oferta = selected(in);
        if (processedIds(in.procesadas()).contains(oferta.id())) {
            throw new ConnectorException("La oferta seleccionada ya finalizó en este proceso.");
        }
        JsonNode perfil = oferta.json().path("validacion");
        if (!perfil.isObject() || !oferta.cuit().equals(perfil.path("cuit").asText())
                || !oferta.tipo().equals(perfil.path("tipoRecurso").asText())) {
            throw new ConnectorException("La oferta seleccionada debe provenir de ofertasEvaluadasJson.");
        }
        Jsons.array(perfil.get("capacidadesHabilitadas"), "capacidadesHabilitadas");

        List<JsonNode> ongs = new ArrayList<>();
        for (JsonNode ong : Jsons.array(client.get("/api/ongs"), "ONGs")) {
            if (oferta.cuit().equals(ong.path("cuit").asText())
                    && "HABILITADA".equals(ong.path("estadoHabilitacion").asText())) {
                ongs.add(ong);
            }
        }
        if (ongs.size() != 1) {
            throw new ConnectorException("No se encontró una única ONG nacional habilitada para el CUIT de la oferta.");
        }
        long ongId = Jsons.id(ongs.get(0), "id");
        List<JsonNode> recursos = new ArrayList<>();
        for (JsonNode recurso : Jsons.array(client.get("/api/ongs/" + ongId + "/recursos"), "recursos")) {
            if (oferta.tipo().equals(recurso.path("tipoRecurso").asText())
                    && oferta.unidad().equals(normalizarUnidad(Jsons.text(recurso, "unidadMedida")))) {
                Jsons.equalId(recurso, "ongId", ongId);
                recursos.add(recurso);
            }
        }
        if (recursos.size() != 1) {
            throw new ConnectorException("No se encontró un único recurso nacional de la ONG con el tipo y unidad del lote.");
        }
        long recursoId = Jsons.id(recursos.get(0), "id");
        ObjectNode request = Jsons.JSON.createObjectNode().put("recursoId", recursoId)
                .put("emergenciaRef", in.emergenciaId()).put("ofertaRef", oferta.id())
                .put("cantidad", oferta.cantidad());
        // La API decide el stock dentro del bloqueo. Una lectura previa de disponible impediría reintentos válidos.
        JsonNode result = client.post("/api/compromisos", request, 201);
        Jsons.equalId(result, "emergenciaRef", in.emergenciaId());
        Jsons.equalId(result, "ofertaRef", oferta.id());
        Jsons.equalId(result, "recursoId", recursoId);
        Jsons.equalId(result, "cantidad", oferta.cantidad());
        if (!"COMPROMETIDO".equals(result.path("estado").asText())) {
            throw new ConnectorException("El compromiso nacional no está en estado COMPROMETIDO.");
        }
        return result;
    }

    private JsonNode liberar(Inputs in, SistemaNacionalClient client) throws ConnectorException {
        Oferta oferta = selected(in);
        ObjectNode request = Jsons.JSON.createObjectNode().put("emergenciaRef", in.emergenciaId())
                .put("ofertaRef", oferta.id());
        JsonNode result = client.post("/api/compromisos/liberar", request, 200);
        Jsons.equalId(result, "emergenciaRef", in.emergenciaId());
        Jsons.equalId(result, "ofertaRef", oferta.id());
        ArrayNode compromisos = Jsons.array(result.get("compromisos"), "compromisos");
        if (compromisos.isEmpty()) {
            throw new ConnectorException("La liberación no confirmó ningún compromiso.");
        }
        for (JsonNode compromiso : compromisos) {
            Jsons.equalId(compromiso, "ofertaRef", oferta.id());
            if (!"LIBERADO".equals(compromiso.path("estado").asText())) {
                throw new ConnectorException("La liberación no confirmó el estado LIBERADO.");
            }
        }
        if (!processedIds(in.procesadas()).contains(oferta.id())) {
            in.procesadas().add(oferta.id());
        }
        return result;
    }

    private Inputs inputs() throws ConnectorException {
        String operation = requiredText("operacion");
        if (!Set.of("EVALUAR", "COMPROMETER", "LIBERAR").contains(operation)) {
            throw new ConnectorException("operacion debe ser EVALUAR, COMPROMETER o LIBERAR.");
        }
        URI base = SistemaNacionalClient.origin(requiredText("sistemaNacionalBaseUrl"));
        long emergenciaId = positiveLong("emergenciaId");
        ArrayNode lotes = Jsons.array(Jsons.parse(requiredText("ofertasJson"), "ofertasJson"), "ofertasJson");
        List<Oferta> ofertas = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        Set<Long> loteIds = new HashSet<>();
        for (JsonNode lote : lotes) {
            if (!loteIds.add(Jsons.id(lote, "id"))) {
                throw new ConnectorException("El JSON contiene lotes repetidos.");
            }
            String tipo = Jsons.text(lote, "tipoRecurso");
            String unidad = normalizarUnidad(Jsons.text(lote, "unidadMedida"));
            for (JsonNode oferta : Jsons.array(lote.get("ofertas"), "ofertas")) {
                long id = Jsons.id(oferta, "id");
                String cuit = Jsons.text(oferta.path("ong"), "cuit");
                if (!cuit.matches("[0-9]{11}")) {
                    throw new ConnectorException("Completar el CUIT de 11 dígitos de la ONG en RescueSync.");
                }
                long cantidad = Jsons.id(oferta, "cantidadOfrecida");
                if (!ids.add(id) || cantidad > Integer.MAX_VALUE) {
                    throw new ConnectorException("Ofertas repetidas o cantidadOfrecida fuera de rango.");
                }
                ofertas.add(new Oferta(id, cuit, tipo, unidad, (int) cantidad, (ObjectNode) oferta));
            }
        }
        ArrayNode procesadas = Jsons.JSON.createArrayNode();
        Long seleccionada = null;
        if (!operation.equals("EVALUAR")) {
            seleccionada = positiveLong("ofertaSeleccionadaId");
            procesadas = Jsons.array(Jsons.parse(requiredText("ofertasProcesadasJson"), "ofertasProcesadasJson"),
                    "ofertasProcesadasJson");
            if (!ids.containsAll(processedIds(procesadas)) || !ids.contains(seleccionada)) {
                throw new ConnectorException("La oferta seleccionada o procesada no pertenece a esta emergencia.");
            }
        }
        return new Inputs(operation, base, emergenciaId, lotes, ofertas, seleccionada, procesadas);
    }

    private static Set<Long> processedIds(ArrayNode values) throws ConnectorException {
        Set<Long> ids = new HashSet<>();
        for (JsonNode value : values) {
            if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0
                    || !ids.add(value.longValue())) {
                throw new ConnectorException("ofertasProcesadasJson debe contener IDs positivos sin repetidos.");
            }
        }
        return ids;
    }

    private static Oferta selected(Inputs in) throws ConnectorException {
        return in.ofertas().stream().filter(o -> o.id() == in.seleccionada()).findFirst()
                .orElseThrow(() -> new ConnectorException("La oferta seleccionada no pertenece a esta emergencia."));
    }

    private String requiredText(String name) throws ConnectorException {
        Object value = getInputParameter(name);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new ConnectorException("Falta el parámetro " + name + ".");
        }
        return text;
    }

    private long positiveLong(String name) throws ConnectorException {
        if (!(getInputParameter(name) instanceof Long value) || value <= 0) {
            throw new ConnectorException(name + " debe ser un Long positivo.");
        }
        return value;
    }

    private String requiredEnvironment(String name) throws ConnectorException {
        String value = environment.apply(name);
        if (value == null || value.isBlank()) {
            throw new ConnectorException("Falta la variable de entorno " + name + " en Bonita.");
        }
        return value;
    }

    private static String normalizarUnidad(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    @Override public void connect() {}
    @Override public void disconnect() {}

    private record Inputs(String operacion, URI base, long emergenciaId, ArrayNode lotes, List<Oferta> ofertas,
                          Long seleccionada, ArrayNode procesadas) {}
    private record Oferta(long id, String cuit, String tipo, String unidad, int cantidad, ObjectNode json) {}
}
