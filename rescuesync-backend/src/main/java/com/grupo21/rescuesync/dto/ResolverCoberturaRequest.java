package com.grupo21.rescuesync.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResolverCoberturaRequest(
        @NotBlank(message = "Indicá cómo se atenderá la emergencia con la cobertura disponible")
        @Size(max = 2000, message = "La resolución admite hasta 2000 caracteres") String observacion
) {}
