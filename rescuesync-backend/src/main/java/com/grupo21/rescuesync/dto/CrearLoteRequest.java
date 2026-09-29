package com.grupo21.rescuesync.dto;
import com.grupo21.rescuesync.model.TipoRecurso;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CrearLoteRequest(
        @NotNull
        TipoRecurso tipoRecurso,

        @NotBlank
        @Size(max = 300)
        String descripcion,

        @NotNull
        @Positive
        Integer cantidadRequerida,

        @NotBlank
        @Size(max = 30)
        String unidadMedida
) {}