package com.grupo21.rescuesync.bonita;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.bonitasoft.engine.connector.ConnectorException;

/** Validación del contrato JSON sin incluir cuerpos de peticiones ni respuestas en los errores. */
final class Jsons {

    static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private Jsons() {}

    static JsonNode parse(String value, String campo) throws ConnectorException {
        try {
            JsonNode node = JSON.readTree(value);
            if (node != null && !node.isNull()) {
                return node;
            }
        } catch (Exception ignored) {
            // El error del parser puede contener datos del cuerpo; no se propaga.
        }
        throw new ConnectorException(campo + " debe contener JSON válido.");
    }

    static ArrayNode array(JsonNode node, String campo) throws ConnectorException {
        if (node == null || !node.isArray()) {
            throw new ConnectorException(campo + " debe ser un array JSON.");
        }
        return (ArrayNode) node;
    }

    static String text(JsonNode node, String campo) throws ConnectorException {
        JsonNode value = node.path(campo);
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new ConnectorException("Falta el texto " + campo + " en el contrato JSON.");
        }
        return value.textValue();
    }

    static long id(JsonNode node, String campo) throws ConnectorException {
        JsonNode value = node.path(campo);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0) {
            throw new ConnectorException(campo + " debe ser un entero positivo.");
        }
        return value.longValue();
    }

    static void equalId(JsonNode node, String campo, long expected) throws ConnectorException {
        if (id(node, campo) != expected) {
            throw new ConnectorException("La respuesta nacional no coincide con " + campo + " de la petición.");
        }
    }
}
