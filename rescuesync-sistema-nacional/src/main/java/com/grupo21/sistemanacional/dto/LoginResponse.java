package com.grupo21.sistemanacional.dto;

import com.grupo21.sistemanacional.model.Rol;

public record LoginResponse(
        String username,
        Rol rol
) {}