package com.grupo21.rescuesync.dto.despliegues;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistrarSeguimientoRequest(
        @NotBlank(message = "Ingresá una observación")
        @Size(max = 2000, message = "La observación admite hasta 2000 caracteres") String observacion) {
}
