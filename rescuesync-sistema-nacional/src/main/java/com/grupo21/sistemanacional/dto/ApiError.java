package com.grupo21.sistemanacional.dto;

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
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors
) {
    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, null);
    }
}
