package com.grupo21.rescuesync.dto.indicadores;

import java.time.Instant;

/**
 * Si los datos que vienen de Bonita están disponibles. Si Bonita está caído, el dashboard
 * igual responde con los datos propios y {@code disponible = false}.
 *
 * @param disponible   true si la consulta a Bonita funcionó
 * @param mensaje      motivo cuando no está disponible; null si está disponible
 * @param consultadoEn momento en que se consultó Bonita (o en que se obtuvo el dato en caché)
 */
public record EstadoFuenteBonita(boolean disponible, String mensaje, Instant consultadoEn) {

    public static EstadoFuenteBonita ok(Instant consultadoEn) {
        return new EstadoFuenteBonita(true, null, consultadoEn);
    }

    public static EstadoFuenteBonita noDisponible(String mensaje) {
        return new EstadoFuenteBonita(false, mensaje, Instant.now());
    }
}
