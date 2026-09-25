package com.grupo21.rescuesync.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Datos de conexión a la API REST de Bonita.
 * Se usan en la integración (tareas E2-10 a E2-12).
 *
 * @param baseUrl     URL base del servidor Bonita, ej. http://localhost:8080/bonita
 * @param username    usuario técnico con el que el backend se loguea en Bonita
 * @param password    contraseña de ese usuario
 * @param processName nombre del proceso desplegado en Bonita
 */
@ConfigurationProperties(prefix = "bonita")
public record BonitaProperties(
        String baseUrl,
        String username,
        String password,
        String processName
) {
}
