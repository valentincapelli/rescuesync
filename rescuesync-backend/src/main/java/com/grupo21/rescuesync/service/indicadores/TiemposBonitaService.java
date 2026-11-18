package com.grupo21.rescuesync.service.indicadores;

import com.grupo21.rescuesync.dto.indicadores.CasoBonitaRef;
import com.grupo21.rescuesync.dto.indicadores.EstadoFuenteBonita;
import com.grupo21.rescuesync.dto.indicadores.TiempoEtapaResponse;
import com.grupo21.rescuesync.dto.indicadores.TiemposEtapasResponse;
import com.grupo21.rescuesync.model.EtapaEmergencia;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * Tiempos por etapa de la emergencia, calculados con la API REST de Bonita.
 *
 * <p>Contrato: nunca lanza excepción por Bonita caído o con error; en ese caso devuelve
 * {@code bonita.disponible = false} y las etapas con 0 muestras (ver {@link #sinDatos}).
 *
 * <p>TODO(E4-B): implementar con BonitaClient + caché según docs/e4-indicadores-contrato.md.
 * La firma pública no se cambia.
 */
@Service
@RequiredArgsConstructor
public class TiemposBonitaService {

    public TiemposEtapasResponse tiemposEtapas(List<CasoBonitaRef> casos) {
        return sinDatos(EstadoFuenteBonita.noDisponible("Cálculo de tiempos de Bonita pendiente de implementación"),
                casos.size());
    }

    /** Respuesta con todas las etapas en 0 muestras. */
    public static TiemposEtapasResponse sinDatos(EstadoFuenteBonita estado, long casosAnalizados) {
        List<TiempoEtapaResponse> etapas = Arrays.stream(EtapaEmergencia.values())
                .map(etapa -> new TiempoEtapaResponse(etapa, etapa.getNombre(), etapa.getTareas(),
                        0, null, null, null, null))
                .toList();
        return new TiemposEtapasResponse(estado, casosAnalizados, 0, null, etapas);
    }
}
