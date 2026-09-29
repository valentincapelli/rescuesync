package com.grupo21.rescuesync.dto.ofertas;

import com.grupo21.rescuesync.model.EstadoOferta;

public record OfertaResponse(
        Long id,
        Long loteId,
        String ongNombre,
        Integer cantidadOfrecida,
        String observaciones,
        EstadoOferta estado
) {}