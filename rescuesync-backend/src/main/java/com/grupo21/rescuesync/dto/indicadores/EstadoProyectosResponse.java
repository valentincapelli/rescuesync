package com.grupo21.rescuesync.dto.indicadores;

import java.util.List;

/**
 * Estado de los proyectos (emergencias registradas en el período). Finalizado = CERRADA.
 *
 * @param enCurso   total - finalizados
 * @param porEstado una entrada por cada valor de EstadoEmergencia, incluso con cantidad 0, en orden del enum
 * @param proyectos ordenados por fechaRegistro descendente
 */
public record EstadoProyectosResponse(
        long total,
        long finalizados,
        long enCurso,
        Double porcentajeFinalizados,
        List<CantidadPorEstadoResponse> porEstado,
        List<ProyectoResumenResponse> proyectos
) {}
