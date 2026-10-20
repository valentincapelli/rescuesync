package com.grupo21.sistemanacional.dto.certificaciones;

import com.grupo21.sistemanacional.model.EstadoHabilitacion;
import com.grupo21.sistemanacional.model.TipoRecurso;

import java.util.List;

/**
 * Perfil de capacidades para el tipo consultado, sin aprobar ni rechazar una oferta.
 * Si la ONG no está registrada, estadoHabilitacion es null; si no hay capacidad habilitada, la lista está vacía.
 */
public record ValidacionCertificacionResponse(
        String cuit,
        TipoRecurso tipoRecurso,
        EstadoHabilitacion estadoHabilitacion,
        List<CapacidadHabilitadaResponse> capacidadesHabilitadas,
        String observaciones
) {}
