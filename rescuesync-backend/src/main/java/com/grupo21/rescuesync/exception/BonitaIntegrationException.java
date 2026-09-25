package com.grupo21.rescuesync.exception;

/**
 * Error al comunicarse con la API de Bonita. Se traduce a HTTP 502.
 */
public class BonitaIntegrationException extends RuntimeException {

    public BonitaIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
