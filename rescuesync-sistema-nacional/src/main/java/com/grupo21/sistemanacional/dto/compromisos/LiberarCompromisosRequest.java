package com.grupo21.sistemanacional.dto.compromisos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Qué liberar. Los ids son los de RescueSync, los mismos que se mandaron al comprometer.
 *
 * @param ofertaRef opcional: sin ella se liberan todos los compromisos de la emergencia (cierre del proyecto)
 */
@Schema(description = "Qué liberar. Los ids son los de RescueSync, los mismos que se enviaron al comprometer.")
public record LiberarCompromisosRequest(

        @NotNull
        @Positive
        @Schema(description = "Id de la emergencia en RescueSync.", example = "12") Long emergenciaRef,

        @Positive
        @Schema(description = "Opcional. Con ella se libera solo esa oferta (todas las ONGs de un consorcio); sin ella, todos los compromisos de la emergencia (cierre del proyecto).", example = "34") Long ofertaRef
) {}
