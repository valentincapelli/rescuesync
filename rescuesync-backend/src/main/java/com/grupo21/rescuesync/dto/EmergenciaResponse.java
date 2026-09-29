package com.grupo21.rescuesync.dto;

import com.grupo21.rescuesync.model.EstadoEmergencia;
import com.grupo21.rescuesync.model.NivelGravedad;
import com.grupo21.rescuesync.model.TipoDesastre;

import java.time.Instant;

public record EmergenciaResponse(
        Long id,
        TipoDesastre tipoDesastre,
        NivelGravedad nivelGravedad,
        String zonaAfectada,
        String descripcion,
        String municipio,
        EstadoEmergencia estado,
        Long bonitaCaseId,
        Instant createdAt
) {}
