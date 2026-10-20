package com.grupo21.sistemanacional.dto.compromisos;

import java.util.List;

/**
 * Resultado de la liberación. {@code liberados} son los que se liberaron en esta llamada y
 * {@code yaLiberados} los que ya lo estaban (un reintento de Bonita no devuelve stock dos veces).
 */
public record LiberacionResponse(
        Long emergenciaRef,
        Long ofertaRef,
        int liberados,
        int yaLiberados,
        List<CompromisoLiberadoResponse> compromisos
) {}
