package com.grupo21.rescuesync.exception;

/**
 * Violación de una regla de negocio (ej. editar una oferta con la convocatoria cerrada).
 * Se traduce a HTTP 409.
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
