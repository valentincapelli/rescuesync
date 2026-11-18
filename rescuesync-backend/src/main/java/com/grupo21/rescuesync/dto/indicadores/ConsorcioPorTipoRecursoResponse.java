package com.grupo21.rescuesync.dto.indicadores;

import com.grupo21.rescuesync.model.TipoRecurso;

public record ConsorcioPorTipoRecursoResponse(
        TipoRecurso tipoRecurso,
        long lotesResueltos,
        long lotesPorConsorcio,
        Double porcentajeConsorcio
) {}
