package com.grupo21.sistemanacional.dto.recursos;

import io.swagger.v3.oas.annotations.media.Schema;
import com.grupo21.sistemanacional.model.Recurso;
import com.grupo21.sistemanacional.model.TipoRecurso;

@Schema(description = "Recurso del inventario de una ONG.")
public record RecursoResponse(
        @Schema(description = "Id del recurso. Es el recursoId que se usa para comprometer.", example = "5") Long id,
        @Schema(description = "Id de la ONG dueña.", example = "1") Long ongId,
        @Schema(description = "Tipo de recurso. Valores: PERSONAL_VOLUNTARIO, ALIMENTOS, AGUA_POTABLE, MEDICAMENTOS, ABRIGO_Y_ROPA, TRANSPORTE, MAQUINARIA_PESADA, REFUGIO_TEMPORAL, OTRO.", example = "ALIMENTOS") TipoRecurso tipoRecurso,
        @Schema(description = "Unidad de medida.", example = "kg") String unidadMedida,
        @Schema(description = "Stock total.", example = "100") int cantidadTotal,
        @Schema(description = "Unidades reservadas por compromisos vigentes.", example = "4") int cantidadComprometida,
        @Schema(description = "Total menos comprometido.", example = "96") int cantidadDisponible
) {
    public static RecursoResponse from(Recurso r) {
        return new RecursoResponse(
                r.getId(),
                r.getOng().getId(),
                r.getTipoRecurso(),
                r.getUnidadMedida(),
                r.getCantidadTotal(),
                r.getCantidadComprometida(),
                r.getDisponible());
    }
}
