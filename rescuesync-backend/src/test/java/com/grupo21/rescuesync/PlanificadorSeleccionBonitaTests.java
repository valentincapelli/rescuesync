package com.grupo21.rescuesync;

import com.grupo21.rescuesync.model.EstadoIntencionSeleccionBonita;
import com.grupo21.rescuesync.service.PlanificadorSeleccionBonita;
import com.grupo21.rescuesync.service.PlanificadorSeleccionBonita.OfertaEnCola;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanificadorSeleccionBonitaTests {
    private final PlanificadorSeleccionBonita planificador = new PlanificadorSeleccionBonita();

    @Test
    void eligePorIdAscendenteYContinuaSoloSiQuedaOtraAdjudicada() {
        assertThat(planificador.siguiente(List.of(pendiente(30), pendiente(10), pendiente(20))))
                .contains(new PlanificadorSeleccionBonita.ProximaSeleccion(10, true));
        assertThat(planificador.siguiente(List.of(liberada(10), pendiente(30), pendiente(20))))
                .contains(new PlanificadorSeleccionBonita.ProximaSeleccion(20, true));
        assertThat(planificador.siguiente(List.of(liberada(10), liberada(20), pendiente(30))))
                .contains(new PlanificadorSeleccionBonita.ProximaSeleccion(30, false));
    }

    @Test
    void noAvanzaDuranteCompromisoMonitoreoLiberacionOIncidente() {
        for (EstadoIntencionSeleccionBonita estado : List.of(
                EstadoIntencionSeleccionBonita.DESPACHANDO,
                EstadoIntencionSeleccionBonita.EN_CICLO,
                EstadoIntencionSeleccionBonita.INCIDENTE)) {
            assertThat(planificador.siguiente(List.of(new OfertaEnCola(10, estado), pendiente(20))))
                    .isEmpty();
        }
    }

    @Test
    void noIncluyeRechazadasNiVuelveAProcesarLiberadas() {
        // La cola contiene exclusivamente las intenciones de ofertas adjudicadas de E4-04.
        assertThat(planificador.siguiente(List.of(liberada(10), pendiente(20))))
                .contains(new PlanificadorSeleccionBonita.ProximaSeleccion(20, false));
        assertThat(planificador.siguiente(List.of(liberada(10), liberada(20)))).isEmpty();
    }

    @Test
    void detectaUnaColaCorruptaAntesDeDespachar() {
        assertThatThrownBy(() -> planificador.siguiente(List.of(pendiente(10), pendiente(10))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static OfertaEnCola pendiente(long id) {
        return new OfertaEnCola(id, EstadoIntencionSeleccionBonita.PENDIENTE);
    }

    private static OfertaEnCola liberada(long id) {
        return new OfertaEnCola(id, EstadoIntencionSeleccionBonita.LIBERADA);
    }
}
