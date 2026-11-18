package com.grupo21.rescuesync.dto.indicadores;

import com.grupo21.rescuesync.model.TipoRecurso;

/**
 * Suministros movilizados en un mes para un tipo de recurso y unidad.
 * Las unidades son texto libre, por eso no se suman entre sí.
 *
 * @param mes      formato "yyyy-MM" (zona America/Argentina/Buenos_Aires)
 * @param cantidad suma de cantidadOfrecida de las ofertas movilizadas
 * @param ofertas  cantidad de ofertas movilizadas
 */
public record SuministroMensualResponse(
        String mes,
        TipoRecurso tipoRecurso,
        String unidadMedida,
        long cantidad,
        long ofertas
) {}
