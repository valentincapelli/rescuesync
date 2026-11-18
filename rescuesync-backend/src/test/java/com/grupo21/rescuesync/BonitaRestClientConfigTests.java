package com.grupo21.rescuesync;

import com.grupo21.rescuesync.config.BonitaProperties;
import com.grupo21.rescuesync.config.RestClientConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El cliente de Bonita contra un servidor HTTP real (no MockRestServiceServer, que reemplaza el
 * cliente HTTP y no detecta estos problemas).
 */
class BonitaRestClientConfigTests {

    private HttpServer servidor;
    private final AtomicReference<String> contentTypeRecibido = new AtomicReference<>("sin request");

    @BeforeEach
    void levantarServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/bonita/API/bpm/userTask/1/execution", intercambio -> {
            contentTypeRecibido.set(intercambio.getRequestHeaders().getFirst("Content-Type"));
            intercambio.sendResponseHeaders(204, -1);
            intercambio.close();
        });
        servidor.createContext("/bonita/lento", intercambio -> {
            try {
                Thread.sleep(2_000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            intercambio.sendResponseHeaders(204, -1);
            intercambio.close();
        });
        servidor.start();
    }

    @AfterEach
    void bajarServidor() {
        servidor.stop(0);
    }

    @Test
    void postSinCuerpoNoMandaContentTypeDeFormulario() {
        // Bonita responde 415 si "ejecutar tarea" llega como application/x-www-form-urlencoded.
        cliente(Duration.ofSeconds(5)).post()
                .uri("/API/bpm/userTask/{id}/execution", 1)
                .retrieve()
                .toBodilessEntity();

        assertThat(contentTypeRecibido.get()).isNotEqualTo("sin request");
        assertThat(contentTypeRecibido.get()).isNull();
    }

    @Test
    void aplicaElTimeoutDeLectura() {
        assertThatThrownBy(() -> cliente(Duration.ofMillis(300)).get()
                .uri("/lento")
                .retrieve()
                .toBodilessEntity())
                .isInstanceOf(ResourceAccessException.class);
    }

    private RestClient cliente(Duration readTimeout) {
        String base = "http://127.0.0.1:" + servidor.getAddress().getPort() + "/bonita";
        return new RestClientConfig().bonitaRestClient(RestClient.builder(),
                new BonitaProperties(base, "u", "p", "RescueSync"), Duration.ofSeconds(2), readTimeout);
    }
}
