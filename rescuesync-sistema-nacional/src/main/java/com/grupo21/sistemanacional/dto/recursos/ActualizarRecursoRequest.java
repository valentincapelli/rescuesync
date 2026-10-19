package com.grupo21.sistemanacional.dto.recursos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Nuevo stock total. No puede quedar por debajo de lo ya comprometido. */
public record ActualizarRecursoRequest(

        @NotNull
        @Min(0)
        Integer cantidadTotal
) {}
