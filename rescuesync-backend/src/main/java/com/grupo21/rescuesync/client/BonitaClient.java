package com.grupo21.rescuesync.client;

import com.grupo21.rescuesync.config.BonitaProperties;
import com.grupo21.rescuesync.dto.BonitaEstadoResponse;
import com.grupo21.rescuesync.exception.BonitaIntegrationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Cliente de la REST API de Bonita Engine (no confundir con nuestra propia API).
 *
 * Maneja el login (POST /loginservice) y la sesión resultante (cookie
 * JSESSIONID + token anti-CSRF X-Bonita-API-Token), reintentando el login una
 * sola vez si una llamada devuelve 401 porque la sesión expiró.
 *
 * También expone instanciarProceso (E2-11/E2-12): arranca una instancia del
 * proceso pasando las entradas del Contrato de instanciación. Si el proceso
 * no tiene contrato definido en Studio, mandar un Map vacío.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BonitaClient {

    @Qualifier("bonitaRestClient")
    private final RestClient bonitaRestClient;
    private final BonitaProperties bonitaProperties;

    private final Object lock = new Object();
    private volatile BonitaSession session;

    private record BonitaHumanTask(
        String id,
        String name,
        String state,
        String rootCaseId,
        String assigned_id
    ) {
    }

    private record BonitaSessionInfo(
        String user_id,
        String user_name
    ) {
    }

    /**
     * Verifica que el backend puede autenticarse contra Bonita y que el proceso
     * configurado (bonita.process-name) existe y está habilitado.
     */
    public BonitaEstadoResponse verificarConexion() {
        String nombreProceso = bonitaProperties.processName();

        String idProceso = buscarIdProceso(nombreProceso)
            .orElseThrow(() -> new BonitaIntegrationException(
                "Bonita respondió, pero no hay un proceso habilitado llamado '"
                + nombreProceso + "'. ¿Está desplegado y habilitado en Bonita Portal?",
            null));

        return new BonitaEstadoResponse(idProceso, nombreProceso);
    }

    /** Busca el id de un proceso habilitado por nombre. Vacío si no existe. */
    public Optional<String> buscarIdProceso(String nombreProceso) {
        return withSession(sesion -> {
            List<BonitaProcessSummary> procesos = bonitaRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/API/bpm/process")
                            .queryParam("f", "name=" + nombreProceso)
                            .queryParam("f", "activationState=ENABLED")
                            .build())
                    .headers(headers -> agregarHeadersDeSesion(headers, sesion))
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<BonitaProcessSummary>>() {});

            return procesos == null
                    ? Optional.<String>empty()
                    : procesos.stream().findFirst().map(BonitaProcessSummary::id);
        });
    }

    /**
     * Instancia el proceso (E2-11) mandando `entradasContrato` como body del POST
     * de instanciación (E2-12). Esas claves tienen que matchear EXACTO los inputs
     * definidos en el Contrato de instanciación del proceso en Bonita Studio — si
     * el proceso no tiene contrato (o no tiene inputs), mandar un Map vacío.
     *
     * Devuelve el caseId de la instancia creada.
     */
    public String instanciarProceso(String idProceso, Map<String, Object> entradasContrato) {
        try {
            return withSession(sesion -> {
                Map<String, Object> respuesta = bonitaRestClient.post()
                        .uri("/API/bpm/process/{id}/instantiation", idProceso)
                        .headers(headers -> agregarHeadersDeSesion(headers, sesion))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(entradasContrato)
                        .retrieve()
                        .body(new ParameterizedTypeReference<Map<String, Object>>() {});

                Object caseId = respuesta == null ? null : respuesta.getOrDefault("caseId", respuesta.get("id"));
                if (caseId == null) {
                    throw new BonitaIntegrationException(
                            "Bonita instanció el proceso pero la respuesta no trae un id de caso reconocible: "
                                    + respuesta, null);
                }
                return String.valueOf(caseId);
            });
        } catch (HttpClientErrorException ex) {
            log.error(
                    "Error HTTP al instanciar proceso en Bonita. Status: {}, respuesta: {}",
                    ex.getStatusCode(),
                    ex.getResponseBodyAsString()
            );

            throw new BonitaIntegrationException(
                    "No se pudo instanciar el proceso '" + idProceso + "' en Bonita",
                    ex
            );
        } catch (RestClientException ex) {
            throw new BonitaIntegrationException(
                    "No se pudo instanciar el proceso '" + idProceso + "' en Bonita",
                    ex
            );
        }
    }

    /**
     * Busca una User Task activa de una instancia de proceso por nombre.
     *
     * @param caseId id de la instancia de proceso en Bonita
     * @param nombreTarea nombre exacto de la User Task definida en el BPMN
     * @return id de la tarea si existe y está en estado ready
     */
    public Optional<String> buscarTareaActiva(String caseId, String nombreTarea) {
        return withSession(sesion -> {
            List<BonitaHumanTask> tareas = bonitaRestClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/API/bpm/humanTask")
                    .queryParam("f", "rootCaseId=" + caseId)
                    .queryParam("p", "0")
                    .queryParam("c", "100")
                    .build())
                .headers(headers -> agregarHeadersDeSesion(headers, sesion))
                .retrieve()
                .body(new ParameterizedTypeReference<List<BonitaHumanTask>>() {});

            if (tareas == null) {
                return Optional.empty();
            }

            return tareas.stream()
                .filter(tarea -> nombreTarea.equals(tarea.name()))
                .filter(tarea -> "ready".equalsIgnoreCase(tarea.state()))
                .findFirst()
                .map(BonitaHumanTask::id);
        });
    }

    public void asignarTarea(String taskId, String userId) {
        withSession(sesion -> {
            bonitaRestClient.put()
                .uri("/API/bpm/humanTask/{taskId}", taskId)
                .headers(headers -> agregarHeadersDeSesion(headers, sesion))
                .body(Map.of("assigned_id", userId))
                .retrieve()
                .toBodilessEntity();

            return null;
        });
    }

    public void ejecutarTarea(String taskId) {
        withSession(sesion -> {
            bonitaRestClient.post()
                .uri("/API/bpm/userTask/{taskId}/execution", taskId)
                .headers(headers -> agregarHeadersDeSesion(headers, sesion))
                .retrieve()
                .toBodilessEntity();

            return null;
        });
    }

    public void completarTarea(String caseId, String nombreTarea) {
        String taskId = null;

        for (int intento = 1; intento <= 10; intento++) {
            Optional<String> tarea = buscarTareaActiva(caseId, nombreTarea);

            if (tarea.isPresent()) {
                taskId = tarea.get();
                break;
            }

            try {
                Thread.sleep(200);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "Interrumpida la espera de la tarea de Bonita",
                        ex
                );
            }
        }

        if (taskId == null) {
            throw new IllegalStateException(
                    "No se encontró la tarea activa '%s' para el caso %s"
                            .formatted(nombreTarea, caseId)
            );
        }

        final String taskIdFinal = taskId;

        withSession(sesion -> {
            String userId = obtenerUserId(sesion);

            bonitaRestClient.put()
                    .uri("/API/bpm/humanTask/{taskId}", taskIdFinal)
                    .headers(headers -> agregarHeadersDeSesion(headers, sesion))
                    .body(Map.of("assigned_id", userId))
                    .retrieve()
                    .toBodilessEntity();

            bonitaRestClient.post()
                    .uri("/API/bpm/userTask/{taskId}/execution", taskIdFinal)
                    .headers(headers -> agregarHeadersDeSesion(headers, sesion))
                    .retrieve()
                    .toBodilessEntity();

            return null;
        });
    }

    /**
     * Ejecuta `accion` con una sesión válida. Si la sesión (cacheada) expiró del
     * lado del engine, la llamada devuelve 401: en ese caso se reautentica una
     * vez y se reintenta.
     */
    private <T> T withSession(Function<BonitaSession, T> accion) {
        BonitaSession actual = obtenerSesion();
        try {
            return accion.apply(actual);
        } catch (HttpClientErrorException.Unauthorized ex) {
            log.warn("Sesión de Bonita vencida o inválida, reautenticando");
            BonitaSession renovada = login();
            return accion.apply(renovada);
        }
    }

    private BonitaSession obtenerSesion() {
        BonitaSession actual = session;
        if (actual != null) {
            return actual;
        }
        synchronized (lock) {
            if (session == null) {
                session = login();
            }
            return session;
        }
    }

    private BonitaSession login() {
        synchronized (lock) {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("username", bonitaProperties.username());
            form.add("password", bonitaProperties.password());
            form.add("redirect", "false");

            try {
                ResponseEntity<Void> response = bonitaRestClient.post()
                        .uri("/loginservice")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .toBodilessEntity();

                List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
                String jSessionId = extraerCookie(cookies, "JSESSIONID");
                String apiToken = extraerCookie(cookies, "X-Bonita-API-Token");

                if (jSessionId == null || apiToken == null) {
                    throw new BonitaIntegrationException(
                            "Login a Bonita respondió OK pero sin las cookies de sesión esperadas "
                                    + "(JSESSIONID / X-Bonita-API-Token)", null);
                }

                log.info("Login a Bonita OK (usuario: {})", bonitaProperties.username());
                BonitaSession nueva = new BonitaSession(jSessionId, apiToken);
                session = nueva;
                return nueva;
            } catch (RestClientException ex) {
                throw new BonitaIntegrationException(
                        "No se pudo autenticar contra Bonita en " + bonitaProperties.baseUrl()
                                + " (usuario: " + bonitaProperties.username() + ")", ex);
            }
        }
    }

    private void agregarHeadersDeSesion(HttpHeaders headers, BonitaSession sesion) {
        headers.add(HttpHeaders.COOKIE,
                "JSESSIONID=" + sesion.jSessionId() + "; X-Bonita-API-Token=" + sesion.apiToken());
        // Bonita exige este header (no alcanza con la cookie) en todo request que no sea GET;
        // se manda siempre para no tener dos caminos distintos.
        headers.add("X-Bonita-API-Token", sesion.apiToken());
    }

    private String extraerCookie(List<String> cookies, String nombre) {
        if (cookies == null) {
            return null;
        }
        for (String cookie : cookies) {
            String[] atributoValor = cookie.split(";", 2)[0].split("=", 2);
            if (atributoValor.length == 2 && atributoValor[0].trim().equals(nombre)) {
                return atributoValor[1].trim();
            }
        }
        return null;
    }

    private String obtenerUserId(BonitaSession sesion) {
        BonitaSessionInfo info = bonitaRestClient.get()
            .uri("/API/system/session/unusedId")
            .headers(headers -> agregarHeadersDeSesion(headers, sesion))
            .retrieve()
            .body(BonitaSessionInfo.class);

        if (info == null || info.user_id() == null || info.user_id().isBlank()) {
            throw new IllegalStateException(
                "Bonita no devolvió el id del usuario autenticado"
            );
        }

        return info.user_id();
    }
}
