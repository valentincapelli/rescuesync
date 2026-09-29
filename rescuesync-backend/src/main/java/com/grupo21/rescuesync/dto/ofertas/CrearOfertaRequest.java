package com.grupo21.rescuesync.dto.ofertas;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CrearOfertaRequest(
        @NotBlank
        @Size(max = 150)
        String ongNombre,

        @NotNull
        @Positive
        Integer cantidadOfrecida,

        String observaciones
) {}