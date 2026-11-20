package com.grupo21.sistemanacional.dto.compromisos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Resultado de la liberación. {@code liberados} son los que se liberaron en esta llamada y
 * {@code yaLiberados} los que ya lo estaban (un reintento de Bonita no devuelve stock dos veces).
 */
@Schema(description = "Resultado de una liberación de recursos.")
public record LiberacionResponse(
        @Schema(description = "Id de la emergencia en RescueSync.", example = "12") Long emergenciaRef,
        @Schema(description = "Id de la oferta liberada. null si se liberó toda la emergencia.", example = "34") Long ofertaRef,
        @Schema(description = "Compromisos que se liberaron en esta llamada.", example = "2") int liberados,
        @Schema(description = "Compromisos que ya estaban liberados. Un reintento da liberados=0 y no devuelve stock otra vez.", example = "0") int yaLiberados,
        @Schema(description = "Detalle de los compromisos alcanzados por la liberación.") List<CompromisoLiberadoResponse> compromisos
) {}
