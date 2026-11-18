package com.grupo21.rescuesync.service.indicadores;

import com.grupo21.rescuesync.dto.indicadores.CantidadPorEstadoResponse;
import com.grupo21.rescuesync.dto.indicadores.EstadoProyectosResponse;
import com.grupo21.rescuesync.dto.indicadores.FiltroIndicadores;
import com.grupo21.rescuesync.dto.indicadores.ResolucionConsorciosResponse;
import com.grupo21.rescuesync.dto.indicadores.SuministrosMovilizadosResponse;
import com.grupo21.rescuesync.model.EstadoEmergencia;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/**
 * Indicadores calculados con datos propios de RescueSync (Postgres).
 *
 * <p>TODO(E4-A): hoy devuelve estructuras vacías que respetan el contrato. Implementar las
 * consultas según docs/e4-indicadores-contrato.md. Las firmas públicas no se cambian.
 * Reciben siempre un filtro ya normalizado.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IndicadoresPropiosService {

    public SuministrosMovilizadosResponse suministrosMovilizados(FiltroIndicadores filtro) {
        return new SuministrosMovilizadosResponse(List.of(), List.of());
    }

    public ResolucionConsorciosResponse resolucionConsorcios(FiltroIndicadores filtro) {
        return new ResolucionConsorciosResponse(0, 0, 0, null, null, List.of());
    }

    public EstadoProyectosResponse estadoProyectos(FiltroIndicadores filtro) {
        List<CantidadPorEstadoResponse> porEstado = Arrays.stream(EstadoEmergencia.values())
                .map(estado -> new CantidadPorEstadoResponse(estado, 0))
                .toList();
        return new EstadoProyectosResponse(0, 0, 0, null, porEstado, List.of());
    }
}
