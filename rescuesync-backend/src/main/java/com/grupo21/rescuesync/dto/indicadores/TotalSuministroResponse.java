package com.grupo21.rescuesync.dto.indicadores;

import com.grupo21.rescuesync.model.TipoRecurso;

/** Total del período para un tipo de recurso y unidad. */
public record TotalSuministroResponse(
        TipoRecurso tipoRecurso,
        String unidadMedida,
        long cantidad,
        long ofertas
) {}
