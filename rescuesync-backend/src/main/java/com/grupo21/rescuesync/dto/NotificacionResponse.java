package com.grupo21.rescuesync.dto;

import com.grupo21.rescuesync.model.TipoNotificacion;

import java.time.Instant;

public record NotificacionResponse(
        Long id,
        String titulo,
        String mensaje,
        TipoNotificacion tipo,
        Instant fechaCreacion,
        boolean leida,
        EstadoRecepcion recepcion
) {
    public enum EstadoRecepcion { NO_REQUIERE, SIN_LEER, PENDIENTE, CONFIRMADA }
}
