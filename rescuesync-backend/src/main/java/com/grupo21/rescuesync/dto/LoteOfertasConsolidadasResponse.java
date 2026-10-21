package com.grupo21.rescuesync.dto;

import com.grupo21.rescuesync.model.EstadoLote;
import com.grupo21.rescuesync.model.TipoRecurso;

import java.util.List;

public record LoteOfertasConsolidadasResponse(
        Long id,
        TipoRecurso tipoRecurso,
        String descripcion,
        Integer cantidadRequerida,
        String unidadMedida,
        EstadoLote estado,
        Long totalOfrecido,
        List<OfertaConsolidadaResponse> ofertas
) {}
