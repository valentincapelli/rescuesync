package com.grupo21.sistemanacional.exception;

/**
 * El usuario autenticado no puede operar sobre ese dato (ej. una ONG que consulta el inventario de otra).
 * Se traduce a HTTP 403.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
