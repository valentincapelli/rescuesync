package com.grupo21.rescuesync.dto.indicadores;

import java.util.List;

/**
 * Tiempos por etapa de la emergencia (API de Bonita).
 *
 * @param casosAnalizados            casos de Bonita consultados (emergencias del filtro con bonitaCaseId)
 * @param casosFinalizados           de esos, los que ya terminaron en Bonita
 * @param duracionPromedioTotalHoras duración promedio de los casos finalizados; null si no hay
 * @param etapas                     siempre una entrada por EtapaEmergencia, en orden del enum (con 0
 *                                   muestras si no hay datos o Bonita no está disponible)
 */
public record TiemposEtapasResponse(
        EstadoFuenteBonita bonita,
        long casosAnalizados,
        long casosFinalizados,
        Double duracionPromedioTotalHoras,
        List<TiempoEtapaResponse> etapas
) {}
