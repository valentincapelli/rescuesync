package com.grupo21.sistemanacional.dto.certificaciones;

import io.swagger.v3.oas.annotations.media.Schema;
import com.grupo21.sistemanacional.model.NivelCompetencia;
import com.grupo21.sistemanacional.model.NivelGravedad;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** El tipo de recurso no se cambia: para otro tipo se crea otra certificación. */
@Schema(description = "Nuevos datos de una certificación. El tipo de recurso no cambia.")
public record ActualizarCertificacionRequest(

        @NotNull
        @Schema(description = "Nivel de competencia certificado, de menor a mayor.", example = "AVANZADO") NivelCompetencia nivelCompetencia,

        @NotNull
        @Schema(description = "Gravedad máxima de emergencia en la que la ONG puede actuar con este tipo de recurso (BAJO < MEDIO < ALTO < CRITICO).", example = "CRITICO") NivelGravedad nivelRiesgoMax,

        @FutureOrPresent
        @Schema(description = "Opcional. Si se omite, la certificación no vence. No puede ser una fecha pasada.", example = "2027-12-31") LocalDate fechaVencimiento
) {}
