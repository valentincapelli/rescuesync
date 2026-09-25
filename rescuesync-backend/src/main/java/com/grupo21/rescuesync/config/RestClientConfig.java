package com.grupo21.rescuesync.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Clientes HTTP hacia servicios externos.
 */
@Configuration
public class RestClientConfig {

    /** Cliente preconfigurado con la URL base de Bonita. */
    @Bean
    public RestClient bonitaRestClient(RestClient.Builder builder, BonitaProperties bonitaProperties) {
        return builder
                .baseUrl(bonitaProperties.baseUrl())
                .build();
    }
}
