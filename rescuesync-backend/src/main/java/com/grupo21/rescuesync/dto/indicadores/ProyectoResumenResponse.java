package com.grupo21.rescuesync.dto.indicadores;

import com.grupo21.rescuesync.model.EstadoEmergencia;
import com.grupo21.rescuesync.model.NivelGravedad;
import com.grupo21.rescuesync.model.TipoDesastre;

import java.time.Instant;

/**
 * Fila de la tabla de proyectos (una emergencia).
 *
 * @param fechaRegistro       createdAt de la emergencia
 * @param fechaCierre         cuándo pasó a CERRADA según historial_estados; null si no está cerrada
 * @param porcentajeCobertura cantidad adjudicada (ofertas SELECCIONADA/COMPROMETIDA/FINALIZADA) /
 *                            cantidad requerida, sumando sus lotes, * 100; null si no tiene lotes
 */
public record ProyectoResumenResponse(
        Long id,
        TipoDesastre tipoDesastre,
        NivelGravedad nivelGravedad,
        String municipio,
        String zonaAfectada,
        EstadoEmergencia estado,
        Instant fechaRegistro,
        Instant fechaCierre,
        int lotes,
        Double porcentajeCobertura,
        Long bonitaCaseId
) {}
