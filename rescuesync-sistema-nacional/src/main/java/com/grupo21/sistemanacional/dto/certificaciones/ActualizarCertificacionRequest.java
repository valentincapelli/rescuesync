package com.grupo21.sistemanacional.dto.certificaciones;

import com.grupo21.sistemanacional.model.NivelCompetencia;
import com.grupo21.sistemanacional.model.NivelGravedad;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** El tipo de recurso no se cambia: para otro tipo se crea otra certificación. */
public record ActualizarCertificacionRequest(

        @NotNull
        NivelCompetencia nivelCompetencia,

        @NotNull
        NivelGravedad nivelRiesgoMax,

        @FutureOrPresent
        LocalDate fechaVencimiento
) {}
