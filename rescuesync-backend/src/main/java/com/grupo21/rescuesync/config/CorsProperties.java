package com.grupo21.rescuesync.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Orígenes habilitados para CORS (el frontend React).
 * Se configura con app.cors.allowed-origins en application.yml.
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
