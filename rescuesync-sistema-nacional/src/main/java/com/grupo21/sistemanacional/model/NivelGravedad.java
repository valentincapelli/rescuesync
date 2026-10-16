package com.grupo21.sistemanacional.model;

/**
 * Nivel de gravedad / riesgo. Mismos valores que NivelGravedad de rescuesync-backend,
 * así la gravedad de una emergencia se compara directo con el riesgo máximo certificado de una ONG.
 * El orden de declaración importa: de menor a mayor.
 */
public enum NivelGravedad {
    BAJO,
    MEDIO,
    ALTO,
    CRITICO;

    /** true si este nivel cubre (es mayor o igual a) el nivel pedido. */
    public boolean cubre(NivelGravedad requerido) {
        return this.compareTo(requerido) >= 0;
    }
}
