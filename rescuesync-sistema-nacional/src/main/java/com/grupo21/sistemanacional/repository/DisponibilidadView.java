package com.grupo21.sistemanacional.repository;

import com.grupo21.sistemanacional.model.TipoRecurso;

/**
 * Fila de la consulta de disponibilidad global (suma de lo disponible de las ONGs habilitadas).
 */
public record DisponibilidadView(TipoRecurso tipoRecurso, String unidadMedida, Long cantidadDisponible) {
}
