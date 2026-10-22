package com.grupo21.sistemanacional.dto.certificaciones;

import io.swagger.v3.oas.annotations.media.Schema;
import com.grupo21.sistemanacional.model.Certificacion;
import com.grupo21.sistemanacional.model.NivelCompetencia;
import com.grupo21.sistemanacional.model.NivelGravedad;
import com.grupo21.sistemanacional.model.TipoRecurso;

import java.time.LocalDate;

/** Capacidad vigente y riesgo máximo certificado, para apoyar la selección de ofertas. */
@Schema(description = "Capacidad vigente de la ONG para un tipo de recurso.")
public record CapacidadHabilitadaResponse(
        @Schema(description = "Tipo de recurso. Valores: PERSONAL_VOLUNTARIO, ALIMENTOS, AGUA_POTABLE, MEDICAMENTOS, ABRIGO_Y_ROPA, TRANSPORTE, MAQUINARIA_PESADA, REFUGIO_TEMPORAL, OTRO.", example = "ALIMENTOS") TipoRecurso tipoRecurso,
        @Schema(description = "Nivel de competencia certificado, de menor a mayor.", example = "INTERMEDIO") NivelCompetencia nivelCompetencia,
        @Schema(description = "Gravedad máxima de emergencia en la que la ONG puede actuar con este tipo de recurso (BAJO < MEDIO < ALTO < CRITICO).", example = "MEDIO") NivelGravedad nivelRiesgoMax,
        @Schema(description = "Último día de vigencia. null si no vence.", example = "2027-12-31") LocalDate fechaVencimiento
) {
    public static CapacidadHabilitadaResponse from(Certificacion certificacion) {
        return new CapacidadHabilitadaResponse(
                certificacion.getTipoRecurso(),
                certificacion.getNivelCompetencia(),
                certificacion.getNivelRiesgoMax(),
                certificacion.getFechaVencimiento());
    }
}
