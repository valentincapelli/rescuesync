package com.grupo21.rescuesync.service.indicadores;

import com.grupo21.rescuesync.dto.indicadores.CasoBonitaRef;
import com.grupo21.rescuesync.dto.indicadores.EstadoProyectosResponse;
import com.grupo21.rescuesync.dto.indicadores.FiltroIndicadores;
import com.grupo21.rescuesync.dto.indicadores.ReporteIndicadoresResponse;
import com.grupo21.rescuesync.dto.indicadores.ResolucionConsorciosResponse;
import com.grupo21.rescuesync.dto.indicadores.ResumenIndicadoresResponse;
import com.grupo21.rescuesync.dto.indicadores.SuministrosMovilizadosResponse;
import com.grupo21.rescuesync.dto.indicadores.TiemposEtapasResponse;
import com.grupo21.rescuesync.repository.EmergenciaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Fachada del dashboard: normaliza el filtro y combina datos propios con datos de Bonita.
 * El controller solo habla con esta clase.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IndicadorService {

    private final IndicadoresPropiosService propios;
    private final TiemposBonitaService tiemposBonita;
    private final EmergenciaRepository emergenciaRepository;

    public SuministrosMovilizadosResponse suministros(FiltroIndicadores filtro) {
        return propios.suministrosMovilizados(filtro.normalizado());
    }

    public ResolucionConsorciosResponse consorcios(FiltroIndicadores filtro) {
        return propios.resolucionConsorcios(filtro.normalizado());
    }

    public EstadoProyectosResponse proyectos(FiltroIndicadores filtro) {
        return propios.estadoProyectos(filtro.normalizado());
    }

    public TiemposEtapasResponse tiemposEtapas(FiltroIndicadores filtro) {
        return calcularTiempos(filtro.normalizado());
    }

    public ResumenIndicadoresResponse resumen(FiltroIndicadores filtro) {
        FiltroIndicadores f = filtro.normalizado();
        return armarResumen(f, propios.estadoProyectos(f), propios.resolucionConsorcios(f), calcularTiempos(f));
    }

    public ReporteIndicadoresResponse reporte(FiltroIndicadores filtro) {
        FiltroIndicadores f = filtro.normalizado();
        EstadoProyectosResponse proyectos = propios.estadoProyectos(f);
        ResolucionConsorciosResponse consorcios = propios.resolucionConsorcios(f);
        TiemposEtapasResponse tiempos = calcularTiempos(f);
        return new ReporteIndicadoresResponse(
                armarResumen(f, proyectos, consorcios, tiempos),
                propios.suministrosMovilizados(f),
                consorcios,
                proyectos,
                tiempos);
    }

    private TiemposEtapasResponse calcularTiempos(FiltroIndicadores f) {
        List<CasoBonitaRef> casos = emergenciaRepository.buscarCasosBonita(
                f.inicio(), f.finExclusivo(), f.municipioId(), f.tipoDesastre());
        return tiemposBonita.tiemposEtapas(casos);
    }

    private static ResumenIndicadoresResponse armarResumen(FiltroIndicadores f,
                                                           EstadoProyectosResponse proyectos,
                                                           ResolucionConsorciosResponse consorcios,
                                                           TiemposEtapasResponse tiempos) {
        return new ResumenIndicadoresResponse(
                Instant.now(),
                f,
                proyectos.total(),
                proyectos.enCurso(),
                proyectos.finalizados(),
                proyectos.porcentajeFinalizados(),
                consorcios.lotesResueltos(),
                consorcios.porcentajeConsorcio(),
                tiempos.duracionPromedioTotalHoras(),
                tiempos.bonita());
    }
}
