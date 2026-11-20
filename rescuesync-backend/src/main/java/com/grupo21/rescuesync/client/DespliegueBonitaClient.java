package com.grupo21.rescuesync.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grupo21.rescuesync.exception.BonitaIntegrationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Traduce el protocolo de E3-10 sin llamar directamente al Sistema Nacional. */
@Component
@RequiredArgsConstructor
public class DespliegueBonitaClient {
    public static final String MONITOREAR = "Monitorear despliegue";
    public static final String FINALIZAR = "Finalizar actividad";
    private final BonitaClient bonita;
    private final ObjectMapper json;

    public EstadoDespliegueBonita consultar(Long emergenciaId, Long caseId) {
        try {
            return consultarEstado(emergenciaId, caseId);
        } catch (RestClientException ex) {
            throw new BonitaIntegrationException("No se pudo actualizar el seguimiento desde Bonita", ex);
        }
    }

    private EstadoDespliegueBonita consultarEstado(Long emergenciaId, Long caseId) {
        String caso = caseId.toString();
        boolean archivado = bonita.buscarCaso(caso).isEmpty();
        if (archivado && bonita.buscarCasoArchivado(caso)
                .filter(c -> "completed".equals(c.state()) && c.end_date() != null && !c.end_date().isBlank()).isEmpty()) {
            throw error("No se pudo confirmar el estado del caso de la emergencia.");
        }
        if (!emergenciaId.toString().equals(variable(caso, "emergenciaId", archivado))) {
            throw error("El caso de Bonita no corresponde a esta emergencia.");
        }
        String seleccion = variable(caso, "ofertaSeleccionadaId", archivado);
        Long ofertaActual = numero(seleccion);
        JsonNode compromiso = documento(variable(caso, "compromisoNacionalJson", archivado));
        JsonNode procesadas = documento(variable(caso, "ofertasProcesadasJson", archivado));
        if (!compromiso.isObject() || !procesadas.isArray()) {
            throw error("Los datos de seguimiento del proceso no tienen el formato esperado.");
        }
        Long comprometida = null;
        Integer cantidad = null;
        if (!compromiso.isEmpty()) {
            if (id(compromiso, "emergenciaRef") != emergenciaId
                    || !"COMPROMETIDO".equals(compromiso.path("estado").asText())
                    || !compromiso.path("cantidad").canConvertToInt()) {
                throw error("El compromiso no coincide con la emergencia.");
            }
            comprometida = id(compromiso, "ofertaRef");
            cantidad = Math.toIntExact(id(compromiso, "cantidad"));
        }
        Set<Long> liberadas = new HashSet<>();
        for (JsonNode oferta : procesadas) {
            if (!oferta.isIntegralNumber() || !oferta.canConvertToLong() || oferta.longValue() <= 0
                    || !liberadas.add(oferta.longValue())) {
                throw error("La lista de actividades liberadas no es válida.");
            }
        }
        // E3-10 agrega el ID a ofertasProcesadasJson solo después de validar la liberación nacional.
        String monitoreo = archivado ? null : bonita.buscarTareaActiva(caso, MONITOREAR).orElse(null);
        String finalizacion = archivado ? null : bonita.buscarTareaActiva(caso, FINALIZAR).orElse(null);
        Set<String> completadas = bonita.listarActividadesArchivadas(caso).stream()
                .filter(t -> "completed".equals(t.state()))
                .map(BonitaActividadArchivada::sourceObjectId).collect(Collectors.toSet());
        // Las variables cambian al avanzar de oferta. No mezclar tareas de dos ciclos.
        if (!seleccion.equals(variable(caso, "ofertaSeleccionadaId", archivado))) {
            throw error("El proceso avanzó de actividad. Volvé a actualizar el seguimiento.");
        }
        return new EstadoDespliegueBonita(caseId, ofertaActual, comprometida, cantidad,
                Set.copyOf(liberadas), archivado, monitoreo, finalizacion, completadas);
    }

    /** Tarea del caso en incidente (failed), solo para casos todavía activos. */
    public java.util.Optional<String> tareaFallida(Long caseId) {
        try {
            return bonita.buscarTareaFallida(caseId.toString());
        } catch (RestClientException ex) {
            throw new BonitaIntegrationException("No se pudo consultar incidentes del caso en Bonita", ex);
        }
    }

    public void completar(String taskId) {
        bonita.completarTareaPorId(taskId);
    }

    private String variable(String caso, String nombre, boolean archivada) {
        return bonita.leerVariable(caso, nombre, archivada).orElseThrow(() ->
                error("El proceso todavía no dispone de los datos de integración nacional para el seguimiento."));
    }

    private JsonNode documento(String valor) {
        try {
            JsonNode resultado = json.readTree(valor);
            if (resultado == null) throw error("El proceso devolvió datos de seguimiento vacíos.");
            return resultado;
        } catch (JsonProcessingException ex) {
            throw error("El proceso devolvió datos de seguimiento inválidos.");
        }
    }

    private Long numero(String valor) {
        try {
            long id = Long.parseLong(valor);
            if (id < 0) throw error("La oferta actual del proceso no es válida.");
            return id == 0 ? null : id;
        } catch (NumberFormatException ex) {
            throw error("La oferta actual del proceso no es válida.");
        }
    }

    private long id(JsonNode nodo, String campo) {
        JsonNode valor = nodo.path(campo);
        if (!valor.isIntegralNumber() || !valor.canConvertToLong() || valor.longValue() <= 0) {
            throw error("El resultado nacional no contiene una referencia válida: " + campo + ".");
        }
        return valor.longValue();
    }

    private BonitaIntegrationException error(String mensaje) {
        return new BonitaIntegrationException(mensaje, null);
    }
}
