package com.grupo21.rescuesync.dto.indicadores;

import com.grupo21.rescuesync.exception.ParametroInvalidoException;
import com.grupo21.rescuesync.model.TipoDesastre;
import com.grupo21.rescuesync.model.TipoRecurso;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Filtros comunes a todos los endpoints de {@code /api/indicadores} (query params, todos opcionales).
 *
 * <p>Las fechas son días calendario de Argentina, ambos extremos inclusive. Por defecto
 * {@code hasta} es hoy y {@code desde} el primer día del mes, 11 meses antes (12 meses en total).
 *
 * <p>{@code tipoRecurso} solo aplica a los indicadores basados en lotes y ofertas
 * (suministros y consorcios); proyectos y tiempos por etapa lo ignoran.
 *
 * <p>Los servicios siempre reciben el filtro ya {@link #normalizado()}: {@code desde} y
 * {@code hasta} nunca son null ahí.
 */
public record FiltroIndicadores(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
        Long municipioId,
        TipoDesastre tipoDesastre,
        TipoRecurso tipoRecurso
) {

    public static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    public static FiltroIndicadores vacio() {
        return new FiltroIndicadores(null, null, null, null, null);
    }

    /** Completa las fechas por defecto y valida el rango (400 si desde > hasta). */
    public FiltroIndicadores normalizado() {
        LocalDate h = hasta != null ? hasta : LocalDate.now(ZONA);
        LocalDate d = desde != null ? desde : h.minusMonths(11).withDayOfMonth(1);
        if (d.isAfter(h)) {
            throw new ParametroInvalidoException("'desde' no puede ser posterior a 'hasta'");
        }
        return new FiltroIndicadores(d, h, municipioId, tipoDesastre, tipoRecurso);
    }

    /** Inicio del período (inclusive). Solo sobre un filtro normalizado. */
    public Instant inicio() {
        return desde.atStartOfDay(ZONA).toInstant();
    }

    /** Fin del período (exclusivo: el día siguiente a {@code hasta}). Solo sobre un filtro normalizado. */
    public Instant finExclusivo() {
        return hasta.plusDays(1).atStartOfDay(ZONA).toInstant();
    }
}
