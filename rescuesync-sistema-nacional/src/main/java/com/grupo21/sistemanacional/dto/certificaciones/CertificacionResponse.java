package com.grupo21.sistemanacional.dto.certificaciones;

import io.swagger.v3.oas.annotations.media.Schema;
import com.grupo21.sistemanacional.model.Certificacion;
import com.grupo21.sistemanacional.model.NivelCompetencia;
import com.grupo21.sistemanacional.model.NivelGravedad;
import com.grupo21.sistemanacional.model.TipoRecurso;

import java.time.LocalDate;

/** {@code vigente} se calcula al responder: una certificación vencida se informa, no se borra. */
@Schema(description = "Certificación de una ONG para un tipo de recurso.")
public record CertificacionResponse(
        @Schema(description = "Id de la certificación.", example = "3") Long id,
        @Schema(description = "Id de la ONG certificada.", example = "1") Long ongId,
        @Schema(description = "Tipo de recurso. Valores: PERSONAL_VOLUNTARIO, ALIMENTOS, AGUA_POTABLE, MEDICAMENTOS, ABRIGO_Y_ROPA, TRANSPORTE, MAQUINARIA_PESADA, REFUGIO_TEMPORAL, OTRO.", example = "MEDICAMENTOS") TipoRecurso tipoRecurso,
        @Schema(description = "Nivel de competencia certificado, de menor a mayor.", example = "AVANZADO") NivelCompetencia nivelCompetencia,
        @Schema(description = "Gravedad máxima de emergencia en la que la ONG puede actuar con este tipo de recurso (BAJO < MEDIO < ALTO < CRITICO).", example = "ALTO") NivelGravedad nivelRiesgoMax,
        @Schema(description = "Último día de vigencia. null si no vence.", example = "2027-12-31") LocalDate fechaVencimiento,
        @Schema(description = "Se calcula al responder. false si ya venció: las vencidas se informan, no se borran.", example = "true") boolean vigente
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
