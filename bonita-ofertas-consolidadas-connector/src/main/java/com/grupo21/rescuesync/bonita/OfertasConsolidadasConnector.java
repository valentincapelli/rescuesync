package com.grupo21.rescuesync.bonita;

import java.io.IOException;
import java.io.StringWriter;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

import org.bonitasoft.engine.connector.AbstractConnector;
import org.bonitasoft.engine.connector.ConnectorException;
import org.bonitasoft.engine.connector.ConnectorValidationException;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

public class OfertasConsolidadasConnector extends AbstractConnector {

    static final String EMERGENCIA_ID = "emergenciaId";
    static final String BACKEND_BASE_URL = "backendBaseUrl";
    static final String OUTPUT = "ofertasConsolidadasJson";
    static final String EMAIL_ENV = "RESCUESYNC_BONITA_SERVICE_EMAIL";
    static final String PASSWORD_ENV = "RESCUESYNC_BONITA_SERVICE_PASSWORD";
    private static final JsonFactory JSON = new JsonFactory();

    private final CredentialSource credentials;
    private final Sleeper sleeper;
    private final Duration connectTimeout;
    private final Duration requestTimeout;
    private final int maxAttempts;

    public OfertasConsolidadasConnector() {
        this(System::getenv, Thread::sleep, Duration.ofSeconds(3), Duration.ofSeconds(10), 3);
    }

    OfertasConsolidadasConnector(CredentialSource credentials, Sleeper sleeper,
            Duration connectTimeout, Duration requestTimeout, int maxAttempts) {
        this.credentials = Objects.requireNonNull(credentials);
        this.sleeper = Objects.requireNonNull(sleeper);
        this.connectTimeout = Objects.requireNonNull(connectTimeout);
        this.requestTimeout = Objects.requireNonNull(requestTimeout);
        this.maxAttempts = maxAttempts;
    }

    @Override
    public void validateInputParameters() throws ConnectorValidationException {
        try {
            readInputs();
        } catch (ConnectorException e) {
            throw new ConnectorValidationException(this, e.getMessage());
        }
    }

    @Override
    protected void executeBusinessLogic() throws ConnectorException {
        Inputs inputs = readInputs();
        String email = requiredEnvironment(EMAIL_ENV);
        String password = requiredEnvironment(PASSWORD_ENV);
        String loginBody = loginBody(email, password);

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                String response = fetch(inputs, loginBody);
                setOutputParameter(OUTPUT, response);
                return;
            } catch (TemporaryFailure e) {
                if (attempt == maxAttempts) {
                    throw new ConnectorException("RescueSync no respondió tras " + maxAttempts
                            + " intentos: " + e.getMessage());
                }
                try {
                    sleeper.sleep(200L * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new ConnectorException("Consulta a RescueSync interrumpida.");
                }
            }
        }
    }

    private Inputs readInputs() throws ConnectorException {
        Object id = getInputParameter(EMERGENCIA_ID);
        if (!(id instanceof Long) || ((Long) id) <= 0) {
            throw new ConnectorException("emergenciaId debe ser un Long positivo.");
        }
        Object url = getInputParameter(BACKEND_BASE_URL);
        if (!(url instanceof String) || ((String) url).isBlank()) {
            throw new ConnectorException("backendBaseUrl debe ser una URL HTTP(S).");
        }
        try {
            URI base = new URI(((String) url).trim());
            if (!("http".equalsIgnoreCase(base.getScheme()) || "https".equalsIgnoreCase(base.getScheme()))
                    || base.getHost() == null || base.getRawUserInfo() != null
                    || base.getRawQuery() != null || base.getRawFragment() != null
                    || (base.getRawPath() != null && !base.getRawPath().isEmpty() && !"/".equals(base.getRawPath()))
                    || base.getPort() > 65535) {
                throw new ConnectorException("backendBaseUrl debe ser el origen HTTP(S) del backend.");
            }
            String origin = base.toString().replaceAll("/+$", "");
            return new Inputs((Long) id, URI.create(origin));
        } catch (URISyntaxException | IllegalArgumentException e) {
            throw new ConnectorException("backendBaseUrl debe ser una URL HTTP(S) válida.");
        }
    }

    private String requiredEnvironment(String name) throws ConnectorException {
        String value = credentials.get(name);
        if (value == null || value.isBlank()) {
            throw new ConnectorException("Falta la variable de entorno " + name + " en el proceso Bonita.");
        }
        return value;
    }

    private String loginBody(String email, String password) throws ConnectorException {
        try {
            StringWriter writer = new StringWriter();
            try (JsonGenerator json = JSON.createGenerator(writer)) {
                json.writeStartObject();
                json.writeStringField("email", email);
                json.writeStringField("password", password);
                json.writeEndObject();
            }
            return writer.toString();
        } catch (IOException e) {
            throw new ConnectorException("No se pudo preparar la autenticación técnica.");
        }
    }

    private String fetch(Inputs inputs, String loginBody) throws ConnectorException, TemporaryFailure {
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        URI loginUri = inputs.base.resolve("/api/auth/login");
        HttpRequest login = HttpRequest.newBuilder(loginUri)
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(loginBody, StandardCharsets.UTF_8))
                .build();
        HttpResponse<Void> loginResponse = send(client, login, HttpResponse.BodyHandlers.discarding());
        requireSuccess(loginResponse.statusCode(), "login");
        boolean hasToken = cookies.getCookieStore().get(loginUri).stream()
                .anyMatch(cookie -> "access_token".equals(cookie.getName())
                        && !cookie.getValue().isBlank() && !cookie.hasExpired());
        if (!hasToken) {
            throw new ConnectorException("El login técnico no devolvió la cookie access_token.");
        }

        URI offersUri = inputs.base.resolve("/api/emergencias/" + inputs.emergenciaId + "/ofertas-consolidadas");
        HttpRequest offers = HttpRequest.newBuilder(offersUri)
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = send(client, offers, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        requireSuccess(response.statusCode(), "ofertas consolidadas");
        validateJsonArray(response.body());
        return response.body();
    }

    private <T> HttpResponse<T> send(HttpClient client, HttpRequest request, HttpResponse.BodyHandler<T> handler)
            throws ConnectorException, TemporaryFailure {
        try {
            return client.send(request, handler);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConnectorException("Consulta a RescueSync interrumpida.");
        } catch (IOException e) {
            throw new TemporaryFailure("fallo de conexión o timeout");
        }
    }

    private void requireSuccess(int status, String operation) throws ConnectorException, TemporaryFailure {
        if (status == 200) {
            return;
        }
        if (status == 429 || (status >= 500 && status < 600)) {
            throw new TemporaryFailure(operation + " devolvió HTTP " + status);
        }
        throw new ConnectorException(operation + " devolvió HTTP " + status + ".");
    }

    private void validateJsonArray(String body) throws ConnectorException {
        try (JsonParser parser = JSON.createParser(body)) {
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new ConnectorException("La respuesta de ofertas consolidadas no es un array JSON.");
            }
            parser.skipChildren();
            if (parser.nextToken() != null) {
                throw new ConnectorException("La respuesta de ofertas consolidadas contiene datos adicionales.");
            }
        } catch (IOException e) {
            throw new ConnectorException("La respuesta de ofertas consolidadas no contiene JSON válido.");
        }
    }

    @Override
    public void connect() throws ConnectorException {
    }

    @Override
    public void disconnect() throws ConnectorException {
    }

    private record Inputs(Long emergenciaId, URI base) {
    }

    @FunctionalInterface
    interface CredentialSource {
        String get(String name);
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private static class TemporaryFailure extends Exception {
        TemporaryFailure(String message) {
            super(message);
        }
    }
}
