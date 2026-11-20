package com.grupo21.sistemanacional.dto.certificaciones;

import io.swagger.v3.oas.annotations.media.Schema;

/** Stock disponible (total menos comprometido) de la ONG para una unidad de medida del tipo consultado. */
@Schema(description = "Stock disponible de la ONG para una unidad de medida del tipo de recurso consultado.")
public record DisponibilidadUnidadResponse(
        @Schema(description = "Unidad de medida del recurso, tal como la cargó la ONG.", example = "raciones") String unidadMedida,
        @Schema(description = "Cantidad disponible: total menos lo ya comprometido.", example = "1000") int cantidadDisponible
) {}
