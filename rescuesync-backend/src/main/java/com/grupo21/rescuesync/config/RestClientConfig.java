package com.grupo21.rescuesync.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Clientes HTTP hacia servicios externos.
 */
@Configuration
public class RestClientConfig {

    /**
     * Cliente preconfigurado con la URL base de Bonita.
     *
     * <p>Con timeouts: si Bonita acepta la conexión pero no responde, la llamada falla en vez de
     * colgar el request (por ejemplo, el dashboard de indicadores). Configurables con
     * {@code bonita.connect-timeout} y {@code bonita.read-timeout}.
     *
     * <p>El cliente HTTP lo elige Spring Boot igual que sin timeouts ({@code detect()}: hoy, el
     * {@code HttpClient} del JDK). <b>No usar {@code SimpleClientHttpRequestFactory}</b>: con
     * {@code HttpURLConnection}, un POST sin cuerpo sale con
     * {@code Content-Type: application/x-www-form-urlencoded} y Bonita responde 415 al ejecutar
     * una tarea ({@code POST /API/bpm/userTask/{id}/execution}). Lo cubre
     * {@code BonitaRestClientConfigTests}.
     */
    @Bean
    public RestClient bonitaRestClient(RestClient.Builder builder,
                                       BonitaProperties bonitaProperties,
                                       @Value("${bonita.connect-timeout:5s}") Duration connectTimeout,
                                       @Value("${bonita.read-timeout:15s}") Duration readTimeout) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(connectTimeout)
                .withReadTimeout(readTimeout);
        return builder
                .baseUrl(bonitaProperties.baseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
    }
}
