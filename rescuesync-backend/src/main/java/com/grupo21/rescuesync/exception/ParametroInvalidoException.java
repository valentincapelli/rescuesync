package com.grupo21.rescuesync.exception;

/** Parámetro de consulta válido en su tipo pero incoherente (ej. desde > hasta). Se mapea a 400. */
public class ParametroInvalidoException extends RuntimeException {

    public ParametroInvalidoException(String message) {
        super(message);
    }
}
