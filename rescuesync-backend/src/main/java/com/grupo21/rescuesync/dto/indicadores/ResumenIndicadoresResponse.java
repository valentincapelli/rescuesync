package com.grupo21.rescuesync.dto.indicadores;

import java.time.Instant;

/**
 * KPIs principales del dashboard, combinando datos propios y de Bonita.
 * Porcentajes en escala 0-100; null cuando el denominador es 0.
 *
 * @param emergenciasTotales            emergencias registradas en el período
 * @param emergenciasActivas            las que no están CERRADA
 * @param proyectosFinalizados          las que están CERRADA
 * @param porcentajeFinalizados         finalizados / totales * 100
 * @param lotesResueltos                ver {@link ResolucionConsorciosResponse}
 * @param porcentajeResolucionConsorcio lotes resueltos por 2+ ONGs / lotes resueltos * 100
 * @param tiempoPromedioResolucionHoras duración promedio de los casos finalizados en Bonita; null si no hay datos
 * @param bonita                        si los datos de Bonita están disponibles
 */
public record ResumenIndicadoresResponse(
        Instant generadoEn,
        FiltroIndicadores filtro,
        long emergenciasTotales,
        long emergenciasActivas,
        long proyectosFinalizados,
        Double porcentajeFinalizados,
        long lotesResueltos,
        Double porcentajeResolucionConsorcio,
        Double tiempoPromedioResolucionHoras,
        EstadoFuenteBonita bonita
) {}
