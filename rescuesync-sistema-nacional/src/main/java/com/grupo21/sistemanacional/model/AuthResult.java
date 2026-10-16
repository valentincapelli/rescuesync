package com.grupo21.sistemanacional.model;

public record AuthResult(
        String token,
        String username,
        Rol rol
) {}