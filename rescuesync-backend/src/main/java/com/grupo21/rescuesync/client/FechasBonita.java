package com.grupo21.rescuesync.client;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.util.Optional;

/**
 * Único lugar donde se interpretan las fechas que devuelve la REST API de Bonita.
 *
 * <p>Formato: {@code yyyy-MM-dd HH:mm:ss.SSS} (por ejemplo {@code 2026-10-21 20:31:00.123}), según los
 * ejemplos de la documentación. Se aceptan también sin milisegundos, con menos dígitos de fracción y
 * con {@code T} como separador.
 *
 * <p><b>Zona horaria asumida:</b> Bonita no manda zona; formatea con la zona del JVM del servidor.
 * Se asume {@code America/Argentina/Buenos_Aires}, la misma del resto del sistema. Para los indicadores
 * solo importan las diferencias entre fechas, así que un error de zona no cambia las duraciones
 * (Argentina no tiene horario de verano), pero sí el {@link Instant} absoluto. No verificado contra un
 * Runtime real.
 *
 * <p>Tolerante: texto nulo, vacío o mal formado devuelve {@link Optional#empty()}, nunca lanza.
 */
public final class FechasBonita {

    public static final ZoneId ZONA_BONITA = ZoneId.of("America/Argentina/Buenos_Aires");

    private static final DateTimeFormatter FORMATO = new DateTimeFormatterBuilder()
            .appendPattern("uuuu-MM-dd")
            .optionalStart().appendLiteral(' ').optionalEnd()
            .optionalStart().appendLiteral('T').optionalEnd()
            .appendPattern("HH:mm:ss")
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
            .optionalEnd()
            .toFormatter()
            // Estricto: una fecha imposible (31 de febrero) se descarta en vez de "corregirse".
            .withResolverStyle(ResolverStyle.STRICT);

    private FechasBonita() {
    }

    public static Optional<Instant> parsear(String texto) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDateTime.parse(texto.trim(), FORMATO).atZone(ZONA_BONITA).toInstant());
        } catch (DateTimeParseException ex) {
            return Optional.empty();
        }
    }
}
