package com.grupo21.sistemanacional.dto.compromisos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Qué liberar. Los ids son los de RescueSync, los mismos que se mandaron al comprometer.
 *
 * @param ofertaRef opcional: sin ella se liberan todos los compromisos de la emergencia (cierre del proyecto)
 */
public record LiberarCompromisosRequest(

        @NotNull
        @Positive
        Long emergenciaRef,

        @Positive
        Long ofertaRef
) {}
