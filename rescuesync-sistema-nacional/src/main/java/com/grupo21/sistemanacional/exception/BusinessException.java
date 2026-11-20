package com.grupo21.sistemanacional.exception;

/**
 * Violación de una regla de negocio (ej. ONG no habilitada). Se traduce a HTTP 409.
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
