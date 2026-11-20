package com.grupo21.rescuesync.dto.despliegues;

import com.grupo21.rescuesync.model.EstadoIntencionSeleccionBonita;
import java.time.Instant;

/** Estado de una adjudicación; las fechas nacionales solo existen tras confirmación. */
public record SeleccionOfertaEstadoResponse(Long ofertaId, String ong,
        EstadoIntencionSeleccionBonita estadoIntegracion, String detalleIncidente,
        Instant comprometidoEn, Instant finalizadoEn, Instant liberadoEn) {}
