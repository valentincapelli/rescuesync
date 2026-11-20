package com.grupo21.rescuesync.bonita;

import com.fasterxml.jackson.databind.JsonNode;
import org.bonitasoft.engine.connector.ConnectorException;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Sesión privada de una ejecución. Los reintentos conservan exactamente el cuerpo de la operación. */
final class SistemaNacionalClient {

    static final String USERNAME_ENV = "SISTEMA_NACIONAL_BONITA_USERNAME";
    static final String PASSWORD_ENV = "SISTEMA_NACIONAL_BONITA_PASSWORD";

    private final URI base;
    private final String username;
    private final String password;
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
    private final HttpClient http;
    private final Duration requestTimeout;
    private final Sleeper sleeper;

    SistemaNacionalClient(URI base, String username, String password, Duration requestTimeout, Sleeper sleeper) {
        this.base = base;
        this.username = username;
        this.password = password;
        this.requestTimeout = requestTimeout;
        this.sleeper = sleeper;
        this.http = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    static URI origin(String value) throws ConnectorException {
        try {
            URI uri = new URI(value.trim());
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || uri.getPort() > 65535
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty() && !uri.getRawPath().equals("/"))) {
                throw new URISyntaxException("", "origen inválido");
            }
            return URI.create(uri.toString().replaceAll("/+$", ""));
        } catch (URISyntaxException | IllegalArgumentException e) {
            throw new ConnectorException("sistemaNacionalBaseUrl debe ser el origen HTTP(S) del Sistema Nacional.");
        }
    }

    void login() throws ConnectorException {
        cookies.getCookieStore().removeAll();
        String body = Jsons.JSON.createObjectNode().put("username", username).put("password", password).toString();
        HttpResponse<String> response = send("POST", "/api/auth/login", body);
        requireStatus(response, 200, "login");
        boolean token = cookies.getCookieStore().get(base.resolve("/api/auth/login")).stream()
                .anyMatch(c -> "access_token".equals(c.getName()) && !c.getValue().isBlank() && !c.hasExpired());
        if (!token) {
            throw new ConnectorException("El login nacional no devolvió la cookie access_token.");
        }
        if (!"SERVICIO".equals(Jsons.parse(response.body(), "login").path("rol").asText())) {
            throw new ConnectorException("La cuenta técnica del Sistema Nacional debe tener rol SERVICIO.");
        }
    }

    JsonNode get(String path) throws ConnectorException {
        return call("GET", path, null, 200);
    }

    JsonNode post(String path, JsonNode body, int expectedStatus) throws ConnectorException {
        return call("POST", path, body.toString(), expectedStatus);
    }

    private JsonNode call(String method, String path, String body, int expectedStatus) throws ConnectorException {
        HttpResponse<String> response = send(method, path, body);
        if (response.statusCode() == 401) {
            login();
            response = send(method, path, body);
        }
        requireStatus(response, expectedStatus, path);
        return Jsons.parse(response.body(), "Respuesta de " + path);
    }

    private HttpResponse<String> send(String method, String path, String body) throws ConnectorException {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder(base.resolve(path)).timeout(requestTimeout)
                        .header("Accept", "application/json");
                if (body == null) {
                    builder.GET();
                } else {
                    builder.header("Content-Type", "application/json")
                            .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
                }
                HttpResponse<String> response = http.send(builder.build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int status = response.statusCode();
                if (status != 429 && !(status >= 500 && status <= 599)) {
                    return response;
                }
            } catch (IOException ignored) {
                // Un POST pudo haberse aplicado: se reenvía la misma operación idempotente.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ConnectorException("Integración con el Sistema Nacional interrumpida.");
            }
            if (attempt < 3) {
                try {
                    sleeper.sleep(200L * attempt);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ConnectorException("Integración con el Sistema Nacional interrumpida.");
                }
            }
        }
        throw new ConnectorException("Sistema Nacional: " + path + " falló tras 3 intentos (conexión, timeout, 429 o 5xx).");
    }

    private static void requireStatus(HttpResponse<?> response, int expected, String operation)
            throws ConnectorException {
        if (response.statusCode() != expected) {
            throw new ConnectorException("Sistema Nacional: " + operation + " devolvió HTTP " + response.statusCode() + ".");
        }
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
