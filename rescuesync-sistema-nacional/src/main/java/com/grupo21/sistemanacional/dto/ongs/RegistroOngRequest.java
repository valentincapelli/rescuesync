package com.grupo21.sistemanacional.dto.ongs;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Onboarding: crea la ONG y su usuario representante en un solo paso. */
public record RegistroOngRequest(

        @NotBlank
        @Pattern(regexp = "\\d{11}", message = "El CUIT debe tener 11 dígitos, sin guiones")
        String cuit,

        @NotBlank
        @Size(max = 150)
        String razonSocial,

        @NotBlank
        @Size(max = 100)
        String username,

        // 72 = máximo que soporta BCrypt
        @NotBlank
        @Size(min = 6, max = 72, message = "La contraseña debe tener entre 6 y 72 caracteres")
        String password
) {}
