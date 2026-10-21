package com.grupo21.sistemanacional.dto.compromisos;

import com.grupo21.sistemanacional.model.EstadoCompromiso;

import java.time.Instant;

public record CrearCompromisoResponse(
        Long id,
        Long recursoId,
        Long emergenciaRef,
        Long ofertaRef,
        Integer cantidad,
        EstadoCompromiso estado,
        Instant fechaLiberacion
) {}