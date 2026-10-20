package com.grupo21.sistemanacional.dto.certificaciones;

import com.grupo21.sistemanacional.model.Certificacion;
import com.grupo21.sistemanacional.model.NivelCompetencia;
import com.grupo21.sistemanacional.model.NivelGravedad;
import com.grupo21.sistemanacional.model.TipoRecurso;

import java.time.LocalDate;

/** Capacidad vigente y riesgo máximo certificado, para apoyar la selección de ofertas. */
public record CapacidadHabilitadaResponse(
        TipoRecurso tipoRecurso,
        NivelCompetencia nivelCompetencia,
        NivelGravedad nivelRiesgoMax,
        LocalDate fechaVencimiento
) {
    public static CapacidadHabilitadaResponse from(Certificacion certificacion) {
        return new CapacidadHabilitadaResponse(
                certificacion.getTipoRecurso(),
                certificacion.getNivelCompetencia(),
                certificacion.getNivelRiesgoMax(),
                certificacion.getFechaVencimiento());
    }
}
