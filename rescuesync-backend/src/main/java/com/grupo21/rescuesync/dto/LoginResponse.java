package com.grupo21.rescuesync.dto;

import com.grupo21.rescuesync.model.Rol;

public record LoginResponse(
        String email,
        Rol rol
) {}