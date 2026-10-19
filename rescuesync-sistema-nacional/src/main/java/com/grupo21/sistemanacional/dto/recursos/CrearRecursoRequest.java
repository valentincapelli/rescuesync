package com.grupo21.sistemanacional.dto.recursos;

import com.grupo21.sistemanacional.model.TipoRecurso;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CrearRecursoRequest(

        @NotNull
        TipoRecurso tipoRecurso,

        /** Texto libre; se guarda en minúsculas y sin espacios de más ("KG " y "kg" son la misma unidad). */
        @NotBlank
        @Size(max = 30)
        String unidadMedida,

        @NotNull
        @Positive
        Integer cantidadTotal
) {}
