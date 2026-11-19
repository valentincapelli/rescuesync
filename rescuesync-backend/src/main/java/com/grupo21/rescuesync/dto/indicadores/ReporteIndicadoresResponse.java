package com.grupo21.rescuesync.dto.indicadores;

/** Reporte completo: es el JSON de exportación y la fuente de los formatos CSV y XLSX. */
public record ReporteIndicadoresResponse(
        ResumenIndicadoresResponse resumen,
        SuministrosMovilizadosResponse suministros,
        ResolucionConsorciosResponse consorcios,
        EstadoProyectosResponse proyectos,
        TiemposEtapasResponse tiemposEtapas
) {}
