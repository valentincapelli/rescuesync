package com.grupo21.rescuesync.exception;

/**
 * Parte del contrato todavía no implementada. Se mapea a 501.
 * Temporal: hoy la usa la exportación CSV/XLSX de indicadores hasta la etapa de integración.
 */
public class FuncionalidadPendienteException extends RuntimeException {

    public FuncionalidadPendienteException(String message) {
        super(message);
    }
}
