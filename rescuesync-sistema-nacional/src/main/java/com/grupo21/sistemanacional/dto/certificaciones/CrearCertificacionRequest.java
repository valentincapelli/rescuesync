package com.grupo21.sistemanacional.dto.certificaciones;

import io.swagger.v3.oas.annotations.media.Schema;
import com.grupo21.sistemanacional.model.NivelCompetencia;
import com.grupo21.sistemanacional.model.NivelGravedad;
import com.grupo21.sistemanacional.model.TipoRecurso;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Schema(description = "Certificación de una ONG para un tipo de recurso. Una ONG tiene a lo sumo una por tipo.")
public record CrearCertificacionRequest(

        @NotNull
        @Schema(description = "Tipo de recurso. Valores: PERSONAL_VOLUNTARIO, ALIMENTOS, AGUA_POTABLE, MEDICAMENTOS, ABRIGO_Y_ROPA, TRANSPORTE, MAQUINARIA_PESADA, REFUGIO_TEMPORAL, OTRO.", example = "MEDICAMENTOS") TipoRecurso tipoRecurso,

        @NotNull
        @Schema(description = "Nivel de competencia certificado, de menor a mayor.", example = "AVANZADO") NivelCompetencia nivelCompetencia,

        /** Gravedad máxima de emergencia en la que la ONG puede actuar con este tipo de recurso. */
        @NotNull
        @Schema(description = "Gravedad máxima de emergencia en la que la ONG puede actuar con este tipo de recurso (BAJO < MEDIO < ALTO < CRITICO).", example = "ALTO") NivelGravedad nivelRiesgoMax,

        /** Opcional: sin fecha, la certificación no vence. */
        @FutureOrPresent
        @Schema(description = "Opcional. Si se omite, la certificación no vence. No puede ser una fecha pasada.", example = "2027-12-31") LocalDate fechaVencimiento
) {}
