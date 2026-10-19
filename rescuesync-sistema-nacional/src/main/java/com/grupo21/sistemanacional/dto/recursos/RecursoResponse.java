package com.grupo21.sistemanacional.dto.recursos;

import com.grupo21.sistemanacional.model.Recurso;
import com.grupo21.sistemanacional.model.TipoRecurso;

public record RecursoResponse(
        Long id,
        Long ongId,
        TipoRecurso tipoRecurso,
        String unidadMedida,
        int cantidadTotal,
        int cantidadComprometida,
        int cantidadDisponible
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
