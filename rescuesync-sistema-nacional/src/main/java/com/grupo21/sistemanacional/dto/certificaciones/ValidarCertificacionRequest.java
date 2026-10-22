package com.grupo21.sistemanacional.dto.certificaciones;

import io.swagger.v3.oas.annotations.media.Schema;
import com.grupo21.sistemanacional.model.TipoRecurso;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** El CUIT identifica a la ONG entre sistemas; no se usa su id interno. */
@Schema(description = "Consulta de capacidades de una ONG para un tipo de recurso.")
public record ValidarCertificacionRequest(

        @NotBlank
        @Pattern(regexp = "\\d{11}", message = "El CUIT debe tener 11 dígitos, sin guiones")
        @Schema(description = "CUIT de la ONG: 11 dígitos, sin guiones. Identifica a la ONG entre sistemas.", example = "30700000001") String cuit,

        @NotNull
        @Schema(description = "Tipo de recurso. Valores: PERSONAL_VOLUNTARIO, ALIMENTOS, AGUA_POTABLE, MEDICAMENTOS, ABRIGO_Y_ROPA, TRANSPORTE, MAQUINARIA_PESADA, REFUGIO_TEMPORAL, OTRO.", example = "ALIMENTOS") TipoRecurso tipoRecurso
) {}
