package com.grupo21.rescuesync.dto.indicadores;

import java.util.List;

/**
 * Porcentaje de lotes resueltos mediante consorcios.
 *
 * <p>Lote resuelto: pasó a CUBIERTO dentro del período (según historial_estados) y tiene al menos
 * una oferta SELECCIONADA, COMPROMETIDA o FINALIZADA. Consorcio: esas ofertas son de 2 o más ONGs.
 *
 * @param porcentajeConsorcio      lotesPorConsorcio / lotesResueltos * 100; null si no hay resueltos
 * @param promedioOngsPorConsorcio ONGs promedio en los lotes resueltos por consorcio; null si no hay
 * @param porTipoRecurso           solo tipos con al menos un lote resuelto, en orden del enum
 */
public record ResolucionConsorciosResponse(
        long lotesResueltos,
        long lotesPorConsorcio,
        long lotesPorOngUnica,
        Double porcentajeConsorcio,
        Double promedioOngsPorConsorcio,
        List<ConsorcioPorTipoRecursoResponse> porTipoRecurso
) {}
