package com.grupo21.sistemanacional.dto.certificaciones;

import com.grupo21.sistemanacional.model.NivelCompetencia;
import com.grupo21.sistemanacional.model.NivelGravedad;
import com.grupo21.sistemanacional.model.TipoRecurso;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CrearCertificacionRequest(

        @NotNull
        TipoRecurso tipoRecurso,

        @NotNull
        NivelCompetencia nivelCompetencia,

        /** Gravedad máxima de emergencia en la que la ONG puede actuar con este tipo de recurso. */
        @NotNull
        NivelGravedad nivelRiesgoMax,

        /** Opcional: sin fecha, la certificación no vence. */
        @FutureOrPresent
        LocalDate fechaVencimiento
) {}
