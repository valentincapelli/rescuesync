package com.grupo21.sistemanacional.dto.compromisos;

import io.swagger.v3.oas.annotations.media.Schema;
import com.grupo21.sistemanacional.model.Compromiso;
import com.grupo21.sistemanacional.model.EstadoCompromiso;
import com.grupo21.sistemanacional.model.TipoRecurso;

import java.time.Instant;

@Schema(description = "Un compromiso incluido en una liberación.")
public record CompromisoLiberadoResponse(
        @Schema(description = "Id del compromiso.", example = "1") Long compromisoId,
        @Schema(description = "Id de la oferta en RescueSync.", example = "34") Long ofertaRef,
        @Schema(description = "Id de la ONG dueña del recurso.", example = "1") Long ongId,
        @Schema(description = "Id del recurso liberado.", example = "5") Long recursoId,
        @Schema(description = "Tipo de recurso. Valores: PERSONAL_VOLUNTARIO, ALIMENTOS, AGUA_POTABLE, MEDICAMENTOS, ABRIGO_Y_ROPA, TRANSPORTE, MAQUINARIA_PESADA, REFUGIO_TEMPORAL, OTRO.", example = "ALIMENTOS") TipoRecurso tipoRecurso,
        @Schema(description = "Unidad de medida del recurso.", example = "kg") String unidadMedida,
        @Schema(description = "Unidades que estaban comprometidas.", example = "2") int cantidad,
        @Schema(description = "Estado del compromiso: COMPROMETIDO mientras el stock está reservado, LIBERADO cuando se devolvió.", example = "LIBERADO") EstadoCompromiso estado,
        @Schema(description = "Momento en que se liberó (UTC).", example = "2026-10-22T15:00:00Z") Instant fechaLiberacion
) {
    public static CompromisoLiberadoResponse from(Compromiso c) {
        return new CompromisoLiberadoResponse(
                c.getId(),
                c.getOfertaRef(),
                c.getRecurso().getOng().getId(),
                c.getRecurso().getId(),
                c.getRecurso().getTipoRecurso(),
                c.getRecurso().getUnidadMedida(),
                c.getCantidad(),
                c.getEstado(),
                c.getFechaLiberacion());
    }
}
