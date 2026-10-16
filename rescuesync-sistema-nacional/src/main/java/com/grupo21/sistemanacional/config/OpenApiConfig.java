package com.grupo21.sistemanacional.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentación Swagger disponible en /swagger-ui.html (se completa en E3-11).
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI sistemaNacionalOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Sistema Nacional de Gestión de Recursos y Riesgos")
                .description("API en el Cloud que consume Bonita: catálogo de ONGs, validación por "
                        + "niveles de competencia, compromiso y liberación de recursos - DSSD 2026 - Grupo 21")
                .version("0.1.0"));
    }
}
