package com.grupo21.sistemanacional.exception;

import lombok.Getter;

/**
 * No hay disponibilidad suficiente para comprometer un recurso. Se traduce a HTTP 409
 * e informa cuánto hay disponible, como pide E3-01.
 */
@Getter
public class StockInsuficienteException extends RuntimeException {

    private final Long recursoId;
    private final int solicitado;
    private final int disponible;

    public StockInsuficienteException(Long recursoId, int solicitado, int disponible) {
        super("Stock insuficiente en el recurso " + recursoId
                + ": se pidieron " + solicitado + " y hay " + disponible + " disponibles");
        this.recursoId = recursoId;
        this.solicitado = solicitado;
        this.disponible = disponible;
    }
}
