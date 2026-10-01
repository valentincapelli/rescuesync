package com.grupo21.rescuesync.model;

public record AuthResult(
        String token,
        String email,
        Rol rol
) {}