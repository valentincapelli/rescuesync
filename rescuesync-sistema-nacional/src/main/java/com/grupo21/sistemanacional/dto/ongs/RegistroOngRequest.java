package com.grupo21.sistemanacional.dto.ongs;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Onboarding: crea la ONG y su usuario representante en un solo paso. */
@Schema(description = "Onboarding: crea la ONG y su usuario representante en un solo paso.")
public record RegistroOngRequest(

        @NotBlank
        @Pattern(regexp = "\\d{11}", message = "El CUIT debe tener 11 dígitos, sin guiones")
        @Schema(description = "CUIT de la ONG: 11 dígitos, sin guiones. No puede repetirse.", example = "30711111111") String cuit,

        @NotBlank
        @Size(max = 150)
        @Schema(description = "Razón social. Hasta 150 caracteres.", example = "Ayuda Sur") String razonSocial,

        @NotBlank
        @Size(max = 100)
        @Schema(description = "Usuario del representante, para el login. Hasta 100 caracteres. No puede repetirse.", example = "ayuda.sur") String username,

        // 72 = máximo que soporta BCrypt
        @NotBlank
        @Size(min = 6, max = 72, message = "La contraseña debe tener entre 6 y 72 caracteres")
        @Schema(description = "Contraseña del representante. Entre 6 y 72 caracteres.", example = "secreto123") String password
) {}
