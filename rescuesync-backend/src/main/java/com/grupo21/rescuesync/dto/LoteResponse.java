package com.grupo21.rescuesync.dto;
import com.grupo21.rescuesync.model.EstadoLote;
import com.grupo21.rescuesync.model.TipoRecurso;
public record LoteResponse(
        Long id,
        Long emergenciaId,
        TipoRecurso tipoRecurso,
        String descripcion,
        Integer cantidadRequerida,
        String unidadMedida,
        EstadoLote estado
) {}