package com.grupo21.rescuesync.dto.indicadores;

import java.util.List;

/**
 * Suministros movilizados: ofertas que pasaron a COMPROMETIDA dentro del período
 * (según historial_estados) y que hoy están COMPROMETIDA o FINALIZADA.
 *
 * @param series  una fila por (mes, tipoRecurso, unidadMedida), ordenada por mes y tipo; sin filas en cero
 * @param totales una fila por (tipoRecurso, unidadMedida) para todo el período
 */
public record SuministrosMovilizadosResponse(
        List<SuministroMensualResponse> series,
        List<TotalSuministroResponse> totales
) {}
