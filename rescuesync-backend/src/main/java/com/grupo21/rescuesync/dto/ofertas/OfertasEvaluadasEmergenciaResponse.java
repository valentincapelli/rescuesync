package com.grupo21.rescuesync.dto.ofertas;

import com.grupo21.rescuesync.model.EstadoEmergencia;
import com.grupo21.rescuesync.model.EstadoLote;
import com.grupo21.rescuesync.model.NivelGravedad;
import com.grupo21.rescuesync.model.TipoDesastre;
import com.grupo21.rescuesync.model.TipoRecurso;

import java.util.List;

public record OfertasEvaluadasEmergenciaResponse(
        Long id,
        TipoDesastre tipoDesastre,
        NivelGravedad nivelGravedad,
        String zonaAfectada,
        String descripcion,
        String municipio,
        EstadoEmergencia estado,
        List<LoteEvaluado> lotes
) {
    public record LoteEvaluado(
            Long id,
            TipoRecurso tipoRecurso,
            String descripcion,
            Integer cantidadRequerida,
            String unidadMedida,
            EstadoLote estado,
            /** Ofertas validadas (principal o apoyo), las que el municipio puede seleccionar. */
            List<OfertaEvaluadaResponse> ofertas,
            /** Evaluadas sin certificación vigente: se informan, pero no se pueden seleccionar. */
            List<OfertaEvaluadaResponse> ofertasSinHabilitacion
    ) {}
}
