package com.grupo21.sistemanacional.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Credenciales para iniciar sesión.")
public record LoginRequest(

        @NotBlank
        @Schema(description = "Nombre de usuario.", example = "bonita") String username,

        @NotBlank
        @Schema(description = "Contraseña.", example = "secreto123") String password
) {}