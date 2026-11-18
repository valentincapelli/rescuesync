package com.grupo21.sistemanacional.controller;

import com.grupo21.sistemanacional.dto.InfoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Endpoint simple para verificar que la API está arriba (útil para probar el deploy en Render
 * y la conectividad desde Bonita).
 */
@Tag(name = "Info")
@RestController
@RequestMapping("/api/info")
public class InfoController {

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${app.version}")
    private String version;

    @Operation(summary = "Estado y versión de la API",
            description = "Sirve para comprobar el despliegue y la conectividad desde Bonita. En el plan free de Render "
                    + "la primera llamada tras un rato sin tráfico puede tardar ~30-60 s en responder.")
    @SecurityRequirements
    @ApiResponse(responseCode = "200", description = "La API está arriba.",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = InfoResponse.class),
                    examples = @ExampleObject(value = "{\"application\": \"rescuesync-sistema-nacional\", "
                            + "\"version\": \"0.1.0-SNAPSHOT\", \"status\": \"UP\", \"serverTime\": \"2026-10-22T15:00:00Z\"}")))
    @GetMapping
    public InfoResponse info() {
        return new InfoResponse(applicationName, version, "UP", Instant.now());
    }
}
