package com.grupo21.sistemanacional.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Estado de la API.")
public record InfoResponse(
        @Schema(description = "Nombre de la aplicación.", example = "rescuesync-sistema-nacional") String application,
        @Schema(description = "Versión desplegada.", example = "0.1.0-SNAPSHOT") String version,
        @Schema(description = "UP si la API está funcionando.", example = "UP") String status,
        @Schema(description = "Hora del servidor (UTC).", example = "2026-10-22T15:00:00Z") Instant serverTime
) {
}
