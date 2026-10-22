package com.grupo21.rescuesync.bonita;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.bonitasoft.engine.connector.ConnectorException;
import org.bonitasoft.engine.connector.ConnectorValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class OfertasConsolidadasConnectorTest {

    private HttpServer server;
    private AtomicInteger logins;
    private AtomicInteger queries;
    private volatile int loginStatus;
    private volatile int offersStatus;
    private volatile boolean issueCookie;
    private volatile String offersBody;
    private volatile long offersDelayMillis;
    private volatile boolean failFirstQuery;
    private volatile boolean cookieReceived;
    private volatile boolean correctPath;
    private volatile boolean correctLogin;

    @BeforeEach
    void setUp() throws IOException {
        logins = new AtomicInteger();
        queries = new AtomicInteger();
        loginStatus = 200;
        offersStatus = 200;
        issueCookie = true;
        offersBody = "[]";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/auth/login", exchange -> {
            logins.incrementAndGet();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            correctLogin = "POST".equals(exchange.getRequestMethod())
                    && "{\"email\":\"service@example.test\",\"password\":\"test-password\"}".equals(body);
            if (issueCookie) {
                exchange.getResponseHeaders().add("Set-Cookie", "access_token=test-jwt; HttpOnly; Path=/");
            }
            respond(exchange, loginStatus, "");
        });
        server.createContext("/api/emergencias/", exchange -> {
            queries.incrementAndGet();
            correctPath = "/api/emergencias/42/ofertas-consolidadas".equals(exchange.getRequestURI().getPath());
            cookieReceived = exchange.getRequestHeaders().getFirst("Cookie") != null
                    && exchange.getRequestHeaders().getFirst("Cookie").contains("access_token=test-jwt");
            if (offersDelayMillis > 0) {
                try {
                    Thread.sleep(offersDelayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            try {
                respond(exchange, failFirstQuery && queries.get() == 1 ? 503 : offersStatus, offersBody);
            } catch (IOException ignored) {
                // A client timeout may close the connection before this response.
            }
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private OfertasConsolidadasConnector connector(Duration timeout, int attempts) {
        var connector = new OfertasConsolidadasConnector(
                name -> name.equals(OfertasConsolidadasConnector.EMAIL_ENV) ? "service@example.test" : "test-password",
                millis -> {}, Duration.ofSeconds(1), timeout, attempts);
        connector.setInputParameters(Map.of(
                OfertasConsolidadasConnector.EMERGENCIA_ID, 42L,
                OfertasConsolidadasConnector.BACKEND_BASE_URL,
                "http://127.0.0.1:" + server.getAddress().getPort()));
        return connector;
    }

    @Test
    void returnsMultipleLotsAndOffersIncludingLotWithoutOffers() throws ConnectorException {
        offersBody = "[{\"id\":1,\"ofertas\":[{\"id\":10},{\"id\":11}],\"totalOfrecido\":7},"
                + "{\"id\":2,\"ofertas\":[],\"totalOfrecido\":0}]";
        var result = connector(Duration.ofSeconds(2), 3).execute();
        assertThat(result).containsEntry(OfertasConsolidadasConnector.OUTPUT, offersBody);
        assertThat(cookieReceived).isTrue();
        assertThat(correctLogin).isTrue();
        assertThat(correctPath).isTrue();
        assertThat(logins).hasValue(1);
        assertThat(queries).hasValue(1);
    }

    @Test
    void acceptsEmptyArray() throws ConnectorException {
        assertThat(connector(Duration.ofSeconds(2), 3).execute())
                .containsEntry(OfertasConsolidadasConnector.OUTPUT, "[]");
    }

    @Test
    void rejectsAuthenticationErrorWithoutRetry() {
        loginStatus = 401;
        ConnectorException error = assertThrows(ConnectorException.class,
                () -> connector(Duration.ofSeconds(2), 3).execute());
        assertThat(error.getMessage()).contains("401").doesNotContain("test-password");
        assertThat(logins).hasValue(1);
        assertThat(queries).hasValue(0);
    }

    @Test
    void rejectsMissingCookieWithoutRetry() {
        issueCookie = false;
        assertThat(assertThrows(ConnectorException.class,
                () -> connector(Duration.ofSeconds(2), 3).execute()).getMessage()).contains("access_token");
        assertThat(logins).hasValue(1);
        assertThat(queries).hasValue(0);
    }

    @Test
    void rejectsNotFoundWithoutRetry() {
        offersStatus = 404;
        assertThat(assertThrows(ConnectorException.class,
                () -> connector(Duration.ofSeconds(2), 3).execute()).getMessage()).contains("404");
        assertThat(logins).hasValue(1);
        assertThat(queries).hasValue(1);
    }

    @Test
    void retriesServerErrorAtMostThreeTimes() {
        offersStatus = 503;
        assertThat(assertThrows(ConnectorException.class,
                () -> connector(Duration.ofSeconds(2), 3).execute()).getMessage()).contains("3 intentos");
        assertThat(logins).hasValue(3);
        assertThat(queries).hasValue(3);
    }

    @Test
    void succeedsAfterTransientServerError() throws ConnectorException {
        failFirstQuery = true;
        assertThat(connector(Duration.ofSeconds(2), 3).execute())
                .containsEntry(OfertasConsolidadasConnector.OUTPUT, "[]");
        assertThat(logins).hasValue(2);
        assertThat(queries).hasValue(2);
    }

    @Test
    void retriesTimeoutAtMostTwice() {
        offersDelayMillis = 250;
        assertThat(assertThrows(ConnectorException.class,
                () -> connector(Duration.ofMillis(50), 2).execute()).getMessage()).contains("2 intentos");
        assertThat(logins.get()).isBetween(1, 2);
        assertThat(queries.get()).isBetween(1, 2);
    }

    @Test
    void rejectsInvalidJsonWithoutRetry() {
        offersBody = "[{\"id\":1}";
        assertThat(assertThrows(ConnectorException.class,
                () -> connector(Duration.ofSeconds(2), 3).execute()).getMessage()).contains("JSON");
        assertThat(queries).hasValue(1);
    }

    @Test
    void rejectsObjectInsteadOfArray() {
        offersBody = "{\"id\":1}";
        assertThat(assertThrows(ConnectorException.class,
                () -> connector(Duration.ofSeconds(2), 3).execute()).getMessage()).contains("array JSON");
    }

    @Test
    void rejectsMissingCredentialsBeforeMakingRequests() {
        var connector = new OfertasConsolidadasConnector(name -> null, millis -> {},
                Duration.ofSeconds(1), Duration.ofSeconds(1), 3);
        connector.setInputParameters(Map.of(
                OfertasConsolidadasConnector.EMERGENCIA_ID, 42L,
                OfertasConsolidadasConnector.BACKEND_BASE_URL,
                "http://127.0.0.1:" + server.getAddress().getPort()));
        assertThat(assertThrows(ConnectorException.class, connector::execute).getMessage())
                .contains(OfertasConsolidadasConnector.EMAIL_ENV);
        assertThat(logins).hasValue(0);
    }

    @Test
    void validatesInputTypes() {
        var connector = connector(Duration.ofSeconds(1), 3);
        connector.setInputParameters(Map.of(
                OfertasConsolidadasConnector.EMERGENCIA_ID, "42",
                OfertasConsolidadasConnector.BACKEND_BASE_URL, "http://localhost:8081"));
        assertThrows(ConnectorValidationException.class, connector::validateInputParameters);
    }
}
