package com.grupo21.rescuesync.dto;

import com.grupo21.rescuesync.model.EstadoOferta;

public record OfertaConsolidadaResponse(
        Long id,
        OngResumenResponse ong,
        Integer cantidadOfrecida,
        String observaciones,
        EstadoOferta estado
) {}
