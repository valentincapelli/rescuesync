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
import java.util.Optional;
import java.util.function.Function;

/**
 * Cliente de la REST API de Bonita Engine (no confundir con nuestra propia API).
 *
 * Maneja el login (POST /loginservice) y la sesión resultante (cookie
 * JSESSIONID + token anti-CSRF X-Bonita-API-Token), reintentando el login una
 * sola vez si una llamada devuelve 401 porque la sesión expiró.
 *
 * Alcance de esta tarea (E2-10): autenticarse y poder resolver el id del
 * proceso configurado en BonitaProperties.processName. Iniciar instancias y
 * setear variables es tarea de E2-11/E2-12.
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
    Optional<String> buscarIdProceso(String nombreProceso) {
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
}
