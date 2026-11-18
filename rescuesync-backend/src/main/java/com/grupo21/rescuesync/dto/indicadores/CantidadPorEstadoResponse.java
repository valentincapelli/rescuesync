package com.grupo21.rescuesync.dto.indicadores;

import com.grupo21.rescuesync.model.EstadoEmergencia;

public record CantidadPorEstadoResponse(EstadoEmergencia estado, long cantidad) {}
