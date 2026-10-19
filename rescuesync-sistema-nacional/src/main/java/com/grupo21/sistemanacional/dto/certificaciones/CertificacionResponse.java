package com.grupo21.sistemanacional.dto.certificaciones;

import com.grupo21.sistemanacional.model.Certificacion;
import com.grupo21.sistemanacional.model.NivelCompetencia;
import com.grupo21.sistemanacional.model.NivelGravedad;
import com.grupo21.sistemanacional.model.TipoRecurso;

import java.time.LocalDate;

/** {@code vigente} se calcula al responder: una certificación vencida se informa, no se borra. */
public record CertificacionResponse(
        Long id,
        Long ongId,
        TipoRecurso tipoRecurso,
        NivelCompetencia nivelCompetencia,
        NivelGravedad nivelRiesgoMax,
        LocalDate fechaVencimiento,
        boolean vigente
) {
    public static CertificacionResponse from(Certificacion c, LocalDate hoy) {
        return new CertificacionResponse(
                c.getId(),
                c.getOng().getId(),
                c.getTipoRecurso(),
                c.getNivelCompetencia(),
                c.getNivelRiesgoMax(),
                c.getFechaVencimiento(),
                c.isVigente(hoy));
    }
}
