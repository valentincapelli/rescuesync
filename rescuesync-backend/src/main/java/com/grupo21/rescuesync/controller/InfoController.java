package com.grupo21.rescuesync.controller;

import com.grupo21.rescuesync.dto.InfoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Endpoint simple para que el frontend verifique que el backend está arriba.
 */
@Tag(name = "Info")
@RestController
@RequestMapping("/api/info")
public class InfoController {

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${app.version}")
    private String version;

    @Operation(summary = "Estado y versión del backend")
    @GetMapping
    public InfoResponse info() {
        return new InfoResponse(applicationName, version, "UP", Instant.now());
    }
}
