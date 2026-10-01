package com.grupo21.rescuesync.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de la ventana temporal de la convocatoria.
 *
 * @param plazoMs duración de la convocatoria en milisegundos
 */
@ConfigurationProperties(prefix = "app.convocatoria")
public record ConvocatoriaProperties(
        Long plazoMs
) {
}