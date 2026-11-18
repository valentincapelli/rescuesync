package com.grupo21.rescuesync.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
     */
    @Bean
    public RestClient bonitaRestClient(RestClient.Builder builder,
                                       BonitaProperties bonitaProperties,
                                       @Value("${bonita.connect-timeout:5s}") Duration connectTimeout,
                                       @Value("${bonita.read-timeout:15s}") Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        return builder
                .baseUrl(bonitaProperties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
