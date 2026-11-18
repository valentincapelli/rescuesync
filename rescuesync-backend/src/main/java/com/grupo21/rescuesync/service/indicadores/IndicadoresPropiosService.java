package com.grupo21.rescuesync.service.indicadores;

import com.grupo21.rescuesync.dto.indicadores.CantidadPorEstadoResponse;
import com.grupo21.rescuesync.dto.indicadores.ConsorcioPorTipoRecursoResponse;
import com.grupo21.rescuesync.dto.indicadores.EstadoProyectosResponse;
import com.grupo21.rescuesync.dto.indicadores.FiltroIndicadores;
import com.grupo21.rescuesync.dto.indicadores.ProyectoResumenResponse;
import com.grupo21.rescuesync.dto.indicadores.ResolucionConsorciosResponse;
import com.grupo21.rescuesync.dto.indicadores.SuministroMensualResponse;
import com.grupo21.rescuesync.dto.indicadores.SuministrosMovilizadosResponse;
import com.grupo21.rescuesync.dto.indicadores.TotalSuministroResponse;
import com.grupo21.rescuesync.model.Emergencia;
import com.grupo21.rescuesync.model.EstadoEmergencia;
import com.grupo21.rescuesync.model.EstadoLote;
import com.grupo21.rescuesync.model.EstadoOferta;
import com.grupo21.rescuesync.model.Lote;
import com.grupo21.rescuesync.model.Oferta;
import com.grupo21.rescuesync.model.TipoEntidadHistorial;
import com.grupo21.rescuesync.model.TipoRecurso;
import com.grupo21.rescuesync.repository.IndicadoresQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Indicadores calculados con datos propios de RescueSync (Postgres).
 * Definiciones exactas: docs/e4-indicadores-contrato.md, secciones 3.1 a 3.3.
 * Reciben siempre un filtro ya normalizado.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IndicadoresPropiosService {

    /** Ofertas que cuentan como adjudicadas a un lote. */
    static final Set<EstadoOferta> OFERTAS_ADJUDICADAS =
            Set.of(EstadoOferta.SELECCIONADA, EstadoOferta.COMPROMETIDA, EstadoOferta.FINALIZADA);

    private static final Set<EstadoOferta> OFERTAS_MOVILIZADAS =
            Set.of(EstadoOferta.COMPROMETIDA, EstadoOferta.FINALIZADA);

    private static final Set<EstadoLote> LOTES_RESUELTOS = Set.of(EstadoLote.CUBIERTO, EstadoLote.CERRADO);

    private final IndicadoresQueryRepository consultas;

    // ---------------------------------------------------------------- 3.1 Suministros movilizados

    public SuministrosMovilizadosResponse suministrosMovilizados(FiltroIndicadores filtro) {
        Map<Long, Instant> compromisoPorOferta = consultas.primerCambioEnPeriodo(
                TipoEntidadHistorial.OFERTA, EstadoOferta.COMPROMETIDA, filtro.inicio(), filtro.finExclusivo());

        List<Oferta> ofertas = consultas.ofertasConLote(compromisoPorOferta.keySet(), OFERTAS_MOVILIZADAS,
                filtro.municipioId(), filtro.tipoDesastre(), filtro.tipoRecurso());

        Map<ClaveMensual, Acumulado> porMes = new TreeMap<>(ClaveMensual.ORDEN);
        Map<ClaveTotal, Acumulado> porTotal = new TreeMap<>(ClaveTotal.ORDEN);

        for (Oferta oferta : ofertas) {
            Lote lote = oferta.getLote();
            String mes = YearMonth.from(compromisoPorOferta.get(oferta.getId()).atZone(FiltroIndicadores.ZONA)).toString();
            String unidad = normalizarUnidad(lote.getUnidadMedida());
            long cantidad = oferta.getCantidadOfrecida();

            porMes.computeIfAbsent(new ClaveMensual(mes, lote.getTipoRecurso(), unidad), k -> new Acumulado())
                    .sumar(cantidad);
            porTotal.computeIfAbsent(new ClaveTotal(lote.getTipoRecurso(), unidad), k -> new Acumulado())
                    .sumar(cantidad);
        }

        List<SuministroMensualResponse> series = porMes.entrySet().stream()
                .map(e -> new SuministroMensualResponse(e.getKey().mes(), e.getKey().tipoRecurso(),
                        e.getKey().unidad(), e.getValue().cantidad, e.getValue().ofertas))
                .toList();
        List<TotalSuministroResponse> totales = porTotal.entrySet().stream()
                .map(e -> new TotalSuministroResponse(e.getKey().tipoRecurso(), e.getKey().unidad(),
                        e.getValue().cantidad, e.getValue().ofertas))
                .toList();

        return new SuministrosMovilizadosResponse(series, totales);
    }

    // ---------------------------------------------------------------- 3.2 Resolución por consorcios

    public ResolucionConsorciosResponse resolucionConsorcios(FiltroIndicadores filtro) {
        List<Long> cubiertosEnPeriodo = consultas.conCambioEnPeriodo(
                TipoEntidadHistorial.LOTE, EstadoLote.CUBIERTO, filtro.inicio(), filtro.finExclusivo());

        List<Lote> candidatos = consultas.lotes(cubiertosEnPeriodo, LOTES_RESUELTOS,
                filtro.municipioId(), filtro.tipoDesastre(), filtro.tipoRecurso());

        Map<Long, Set<Long>> ongsPorLote = new HashMap<>();
        for (Object[] fila : consultas.ongsPorLote(candidatos.stream().map(Lote::getId).toList(), OFERTAS_ADJUDICADAS)) {
            ongsPorLote.computeIfAbsent((Long) fila[0], k -> new HashSet<>()).add((Long) fila[1]);
        }

        long resueltos = 0;
        long porConsorcio = 0;
        long ongsEnConsorcios = 0;
        Map<TipoRecurso, long[]> porTipo = new EnumMap<>(TipoRecurso.class); // [resueltos, porConsorcio]

        for (Lote lote : candidatos) {
            int ongs = ongsPorLote.getOrDefault(lote.getId(), Set.of()).size();
            if (ongs == 0) {
                continue; // cubierto sin oferta adjudicada: no cuenta como resuelto
            }
            resueltos++;
            long[] acumTipo = porTipo.computeIfAbsent(lote.getTipoRecurso(), k -> new long[2]);
            acumTipo[0]++;
            if (ongs >= 2) {
                porConsorcio++;
                ongsEnConsorcios += ongs;
                acumTipo[1]++;
            }
        }

        List<ConsorcioPorTipoRecursoResponse> detalle = porTipo.entrySet().stream()
                .map(e -> new ConsorcioPorTipoRecursoResponse(e.getKey(), e.getValue()[0], e.getValue()[1],
                        porcentaje(e.getValue()[1], e.getValue()[0])))
                .toList();

        return new ResolucionConsorciosResponse(
                resueltos,
                porConsorcio,
                resueltos - porConsorcio,
                porcentaje(porConsorcio, resueltos),
                porConsorcio == 0 ? null : redondear((double) ongsEnConsorcios / porConsorcio),
                detalle);
    }

    // ---------------------------------------------------------------- 3.3 Estado de proyectos

    public EstadoProyectosResponse estadoProyectos(FiltroIndicadores filtro) {
        List<Emergencia> emergencias = consultas.emergencias(
                filtro.inicio(), filtro.finExclusivo(), filtro.municipioId(), filtro.tipoDesastre());
        List<Long> emergenciaIds = emergencias.stream().map(Emergencia::getId).toList();

        // Lotes de cada emergencia: [cantidad de lotes, requerido, cubierto (con tope por lote)].
        List<Object[]> lotes = consultas.lotesDeEmergencias(emergenciaIds);
        Map<Long, Long> ofrecidoPorLote = consultas.cantidadOfrecidaPorLote(
                lotes.stream().map(f -> (Long) f[0]).toList(), OFERTAS_ADJUDICADAS);
        Map<Long, long[]> coberturaPorEmergencia = new HashMap<>();
        for (Object[] fila : lotes) {
            long requerido = ((Number) fila[2]).longValue();
            long cubierto = Math.min(ofrecidoPorLote.getOrDefault((Long) fila[0], 0L), requerido);
            long[] acum = coberturaPorEmergencia.computeIfAbsent((Long) fila[1], k -> new long[3]);
            acum[0]++;
            acum[1] += requerido;
            acum[2] += cubierto;
        }

        List<Long> cerradas = emergencias.stream()
                .filter(e -> e.getEstado() == EstadoEmergencia.CERRADA)
                .map(Emergencia::getId)
                .toList();
        Map<Long, Instant> fechaCierre = consultas.primerCambio(
                TipoEntidadHistorial.EMERGENCIA, EstadoEmergencia.CERRADA, cerradas);

        Map<EstadoEmergencia, Long> conteo = new EnumMap<>(EstadoEmergencia.class);
        List<ProyectoResumenResponse> proyectos = new ArrayList<>();
        for (Emergencia e : emergencias) {
            conteo.merge(e.getEstado(), 1L, Long::sum);
            long[] cobertura = coberturaPorEmergencia.getOrDefault(e.getId(), new long[3]);
            proyectos.add(new ProyectoResumenResponse(
                    e.getId(),
                    e.getTipoDesastre(),
                    e.getNivelGravedad(),
                    e.getMunicipio().getNombre(),
                    e.getZonaAfectada(),
                    e.getEstado(),
                    e.getCreatedAt(),
                    e.getEstado() == EstadoEmergencia.CERRADA ? fechaCierre.get(e.getId()) : null,
                    (int) cobertura[0],
                    cobertura[0] == 0 ? null : porcentaje(cobertura[2], cobertura[1]),
                    e.getBonitaCaseId()));
        }
        proyectos.sort(Comparator.comparing(ProyectoResumenResponse::fechaRegistro).reversed()
                .thenComparing(ProyectoResumenResponse::id, Comparator.reverseOrder()));

        List<CantidadPorEstadoResponse> porEstado = Arrays.stream(EstadoEmergencia.values())
                .map(estado -> new CantidadPorEstadoResponse(estado, conteo.getOrDefault(estado, 0L)))
                .toList();

        long total = emergencias.size();
        long finalizados = conteo.getOrDefault(EstadoEmergencia.CERRADA, 0L);
        return new EstadoProyectosResponse(total, finalizados, total - finalizados,
                porcentaje(finalizados, total), porEstado, proyectos);
    }

    // ---------------------------------------------------------------- auxiliares

    /** numerador / denominador * 100, a 1 decimal; null si el denominador es 0. */
    static Double porcentaje(long numerador, long denominador) {
        return denominador == 0 ? null : redondear(numerador * 100.0 / denominador);
    }

    static double redondear(double valor) {
        return Math.round(valor * 10.0) / 10.0;
    }

    static String normalizarUnidad(String unidad) {
        return unidad == null ? "" : unidad.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static final class Acumulado {
        long cantidad;
        long ofertas;

        void sumar(long valor) {
            cantidad += valor;
            ofertas++;
        }
    }

    private record ClaveMensual(String mes, TipoRecurso tipoRecurso, String unidad) {
        static final Comparator<ClaveMensual> ORDEN = Comparator.comparing(ClaveMensual::mes)
                .thenComparing(ClaveMensual::tipoRecurso)
                .thenComparing(ClaveMensual::unidad);
    }

    private record ClaveTotal(TipoRecurso tipoRecurso, String unidad) {
        static final Comparator<ClaveTotal> ORDEN = Comparator.comparing(ClaveTotal::tipoRecurso)
                .thenComparing(ClaveTotal::unidad);
    }
}
