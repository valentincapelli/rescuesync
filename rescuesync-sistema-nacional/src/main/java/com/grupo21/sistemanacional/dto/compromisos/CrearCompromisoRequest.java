package com.grupo21.sistemanacional.dto.compromisos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CrearCompromisoRequest(
        @NotNull @Positive Long recursoId,
        @NotNull @Positive Long emergenciaRef,
        @NotNull @Positive Long ofertaRef,
        @NotNull @Positive Integer cantidad
) {}