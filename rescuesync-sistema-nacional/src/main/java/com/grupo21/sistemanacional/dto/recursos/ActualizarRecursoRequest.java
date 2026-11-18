package com.grupo21.sistemanacional.dto.recursos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Nuevo stock total. No puede quedar por debajo de lo ya comprometido. */
@Schema(description = "Nuevo stock total. No puede quedar por debajo de lo ya comprometido.")
public record ActualizarRecursoRequest(

        @NotNull
        @Min(0)
        @Schema(description = "Nuevo stock total. 0 o más.", example = "150") Integer cantidadTotal
) {}
