package com.grupo21.rescuesync.dto.indicadores;

import com.grupo21.rescuesync.model.EtapaEmergencia;

import java.util.List;

/**
 * Tiempo de resolución de una etapa, calculado con datos de Bonita.
 * Duración de la etapa en un caso = suma de las duraciones de sus tareas completadas en ese caso.
 *
 * @param nombre        EtapaEmergencia.getNombre()
 * @param tareas        EtapaEmergencia.getTareas()
 * @param muestras      casos que completaron al menos una tarea de la etapa
 * @param promedioHoras null si muestras = 0 (lo mismo para mediana, mínimo y máximo)
 */
public record TiempoEtapaResponse(
        EtapaEmergencia etapa,
        String nombre,
        List<String> tareas,
        long muestras,
        Double promedioHoras,
        Double medianaHoras,
        Double minimoHoras,
        Double maximoHoras
) {}
