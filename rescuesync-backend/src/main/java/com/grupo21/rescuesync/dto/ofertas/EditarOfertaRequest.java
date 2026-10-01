package com.grupo21.rescuesync.dto.ofertas;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record EditarOfertaRequest(
        @NotNull
        @Positive
        Integer cantidadOfrecida,

        String observaciones
) {}