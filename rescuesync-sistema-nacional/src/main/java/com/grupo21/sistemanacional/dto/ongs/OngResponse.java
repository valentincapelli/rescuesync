package com.grupo21.sistemanacional.dto.ongs;

import io.swagger.v3.oas.annotations.media.Schema;
import com.grupo21.sistemanacional.model.EstadoHabilitacion;
import com.grupo21.sistemanacional.model.Ong;

@Schema(description = "ONG registrada en el Sistema Nacional.")
public record OngResponse(
        @Schema(description = "Id de la ONG.", example = "1") Long id,
        @Schema(description = "CUIT de la ONG: 11 dígitos, sin guiones.", example = "30711111111") String cuit,
        @Schema(description = "Razón social.", example = "Ayuda Sur") String razonSocial,
        @Schema(description = "Estado de la ONG en el Sistema Nacional. Solo las HABILITADA pueden comprometer recursos.", example = "HABILITADA") EstadoHabilitacion estadoHabilitacion
) {
    public static OngResponse from(Ong ong) {
        return new OngResponse(ong.getId(), ong.getCuit(), ong.getRazonSocial(), ong.getEstadoHabilitacion());
    }
}
