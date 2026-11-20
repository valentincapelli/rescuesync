package com.grupo21.rescuesync.dto;

import com.grupo21.rescuesync.model.DecisionCobertura;
import com.grupo21.rescuesync.model.TipoRecurso;
import java.time.LocalDateTime;
import java.util.List;

public record CoberturaResponse(
        EmergenciaResponse emergencia,
        LocalDateTime fechaCierreConvocatoria,
        boolean plazoVencido,
        DecisionCobertura decision,
        boolean republicacionPendiente,
        boolean resolucionPendiente,
        boolean sincronizacionPendiente,
        String resolucion,
        List<Lote> lotes
) {
    public record Lote(Long id, TipoRecurso tipoRecurso, String descripcion, int cantidadRequerida,
                       String unidadMedida, long totalOfrecido) {}
}
