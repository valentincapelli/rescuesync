package com.grupo21.sistemanacional.dto.certificaciones;

import com.grupo21.sistemanacional.model.TipoRecurso;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** El CUIT identifica a la ONG entre sistemas; no se usa su id interno. */
public record ValidarCertificacionRequest(

        @NotBlank
        @Pattern(regexp = "\\d{11}", message = "El CUIT debe tener 11 dígitos, sin guiones")
        String cuit,

        @NotNull
        TipoRecurso tipoRecurso
) {}
