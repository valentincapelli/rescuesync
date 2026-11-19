package com.grupo21.sistemanacional.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.grupo21.sistemanacional.model.Rol;

@Schema(description = "Usuario autenticado. El JWT no viaja acá: llega en la cookie access_token.")
public record LoginResponse(
        @Schema(description = "Nombre de usuario.", example = "bonita") String username,
        @Schema(description = "Rol del usuario: ADMIN, ONG o SERVICIO (Bonita).", example = "SERVICIO") Rol rol
) {}