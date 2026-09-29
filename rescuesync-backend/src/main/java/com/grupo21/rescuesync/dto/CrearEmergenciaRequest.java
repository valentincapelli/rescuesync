package com.grupo21.rescuesync.dto;

import com.grupo21.rescuesync.model.NivelGravedad;
import com.grupo21.rescuesync.model.TipoDesastre;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CrearEmergenciaRequest(
        @NotNull
        TipoDesastre tipoDesastre,

        @NotNull
        NivelGravedad nivelGravedad,

        @NotBlank
        @Size(max = 200)
        String zonaAfectada,

        @NotBlank
        String descripcion,

        @NotBlank
        @Size(max = 150)
        String municipio
) {}
