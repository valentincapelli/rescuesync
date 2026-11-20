package com.grupo21.rescuesync.dto.ofertas;

import java.time.Instant;

/**
 * Una versión de una oferta. La versión 1 es el alta ({@code autorEmail} null, porque la oferta no guarda quién
 * la cargó); cada edición posterior agrega una versión con su autor y los valores que reemplazó.
 */
public record VersionOfertaResponse(
        int numero,
        Instant fecha,
        String autorEmail,
        Integer cantidadOfrecida,
        String observaciones,
        Integer cantidadAnterior,
        String observacionesAnteriores
) {}
