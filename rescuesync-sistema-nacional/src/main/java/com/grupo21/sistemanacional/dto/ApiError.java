package com.grupo21.sistemanacional.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Formato único de error que devuelve la API (mismo formato que el backend de RescueSync,
 * así Bonita y el backend manejan los errores de las dos APIs igual).
 *
 * @param fieldErrors errores de validación por campo (solo en 400 de validación)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Formato único de error de la API.")
public record ApiError(
        @Schema(description = "Momento del error (UTC).", example = "2026-10-22T15:00:00Z") Instant timestamp,
        @Schema(description = "Código HTTP.", example = "409") int status,
        @Schema(description = "Texto estándar del código HTTP.", example = "Conflict") String error,
        @Schema(description = "Explicación del error, pensada para mostrarse.", example = "Stock insuficiente en el recurso 5: se pidieron 11 y hay 10 disponibles") String message,
        @Schema(description = "Ruta que se llamó.", example = "/api/compromisos") String path,
        @Schema(description = "Solo en los 400 de validación: mensaje de error por cada campo inválido.", example = "{\"cantidad\": \"debe ser mayor que 0\"}") Map<String, String> fieldErrors
) {
    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, null);
    }
}
