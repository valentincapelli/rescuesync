package com.grupo21.rescuesync.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentación Swagger disponible en /swagger-ui.html
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI rescueSyncOpenApi() {
        return new OpenAPI().info(new Info()
                .title("RescueSync API")
                .description("Backend de la aplicación web RescueSync - DSSD 2026 - Grupo 21")
                .version("0.1.0"));
    }
}
