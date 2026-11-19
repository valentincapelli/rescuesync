package com.grupo21.rescuesync.dto.despliegues;

import com.grupo21.rescuesync.model.EstadoEmergencia;
import com.grupo21.rescuesync.model.TipoRecurso;
import java.time.Instant;
import java.util.List;

public record DespliegueResponse(Long ofertaId, Long emergenciaId, String zonaAfectada, String municipio,
        EstadoEmergencia estadoEmergencia, Long loteId, TipoRecurso tipoRecurso, String descripcion,
        String ong, Integer cantidad, String unidadMedida, Instant comprometidoEn, Instant monitoreadoEn,
        Instant finalizadoEn, Instant liberadoEn, Instant sincronizadoEn, boolean puedeMonitorear,
        boolean puedeFinalizar, boolean pendienteSincronizacion, List<NovedadDespliegueResponse> novedades) {
}
