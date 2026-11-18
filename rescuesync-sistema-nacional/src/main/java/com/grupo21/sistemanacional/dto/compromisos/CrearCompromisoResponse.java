package com.grupo21.sistemanacional.dto.compromisos;

import io.swagger.v3.oas.annotations.media.Schema;
import com.grupo21.sistemanacional.model.EstadoCompromiso;

import java.time.Instant;

@Schema(description = "Compromiso creado (o el ya existente, si es un reintento).")
public record CrearCompromisoResponse(
        @Schema(description = "Id del compromiso.", example = "1") Long id,
        @Schema(description = "Id del recurso reservado.", example = "5") Long recursoId,
        @Schema(description = "Id de la emergencia en RescueSync.", example = "12") Long emergenciaRef,
        @Schema(description = "Id de la oferta en RescueSync.", example = "34") Long ofertaRef,
        @Schema(description = "Unidades reservadas.", example = "2") Integer cantidad,
        @Schema(description = "Estado del compromiso: COMPROMETIDO mientras el stock está reservado, LIBERADO cuando se devolvió.", example = "COMPROMETIDO") EstadoCompromiso estado,
        @Schema(description = "Momento en que se liberó (UTC). null mientras siga comprometido.") Instant fechaLiberacion
) {}