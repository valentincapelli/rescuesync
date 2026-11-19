package com.grupo21.rescuesync.dto.ofertas;

import java.util.Set;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record SeleccionarOfertasRequest(

        @NotNull(message = "Debe indicar la emergencia")
        Long emergenciaId,

        @NotEmpty(message = "Debe seleccionar al menos una oferta")
        Set<@NotNull Long> ofertaIds

) {
}