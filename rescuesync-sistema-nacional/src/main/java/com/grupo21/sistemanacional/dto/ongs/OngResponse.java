package com.grupo21.sistemanacional.dto.ongs;

import com.grupo21.sistemanacional.model.EstadoHabilitacion;
import com.grupo21.sistemanacional.model.Ong;

public record OngResponse(
        Long id,
        String cuit,
        String razonSocial,
        EstadoHabilitacion estadoHabilitacion
) {
    public static OngResponse from(Ong ong) {
        return new OngResponse(ong.getId(), ong.getCuit(), ong.getRazonSocial(), ong.getEstadoHabilitacion());
    }
}
