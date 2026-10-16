package com.grupo21.sistemanacional.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Orígenes habilitados para CORS. Bonita llama servidor a servidor y no lo necesita;
 * queda para el onboarding de ONGs si se hace desde el frontend.
 * Se configura con app.cors.allowed-origins en application.yml.
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
