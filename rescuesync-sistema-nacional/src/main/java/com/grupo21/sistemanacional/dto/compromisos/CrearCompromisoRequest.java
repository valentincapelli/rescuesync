package com.grupo21.sistemanacional.dto.compromisos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Reserva de unidades de un recurso para una oferta de RescueSync.")
public record CrearCompromisoRequest(
        @NotNull @Positive @Schema(description = "Id del recurso (inventario de una ONG) del que se reservan unidades.", example = "5") Long recursoId,
        @NotNull @Positive @Schema(description = "Id de la emergencia en RescueSync. Se usa luego para liberar.", example = "12") Long emergenciaRef,
        @NotNull @Positive @Schema(description = "Id de la oferta en RescueSync. Junto con recursoId hace idempotente el pedido.", example = "34") Long ofertaRef,
        @NotNull @Positive @Schema(description = "Unidades a reservar. Debe ser mayor que 0 y no superar lo disponible.", example = "2") Integer cantidad
) {}