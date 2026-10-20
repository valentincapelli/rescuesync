package com.grupo21.sistemanacional.dto.compromisos;

import com.grupo21.sistemanacional.model.Compromiso;
import com.grupo21.sistemanacional.model.EstadoCompromiso;
import com.grupo21.sistemanacional.model.TipoRecurso;

import java.time.Instant;

public record CompromisoLiberadoResponse(
        Long compromisoId,
        Long ofertaRef,
        Long ongId,
        Long recursoId,
        TipoRecurso tipoRecurso,
        String unidadMedida,
        int cantidad,
        EstadoCompromiso estado,
        Instant fechaLiberacion
) {
    public static CompromisoLiberadoResponse from(Compromiso c) {
        return new CompromisoLiberadoResponse(
                c.getId(),
                c.getOfertaRef(),
                c.getRecurso().getOng().getId(),
                c.getRecurso().getId(),
                c.getRecurso().getTipoRecurso(),
                c.getRecurso().getUnidadMedida(),
                c.getCantidad(),
                c.getEstado(),
                c.getFechaLiberacion());
    }
}
