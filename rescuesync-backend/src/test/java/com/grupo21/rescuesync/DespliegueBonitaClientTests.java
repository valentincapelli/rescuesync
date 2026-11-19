package com.grupo21.rescuesync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grupo21.rescuesync.client.*;
import com.grupo21.rescuesync.exception.BonitaIntegrationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DespliegueBonitaClientTests {
    private BonitaClient bonita;
    private DespliegueBonitaClient cliente;

    @BeforeEach
    void preparar() {
        bonita = mock(BonitaClient.class);
        cliente = new DespliegueBonitaClient(bonita, new ObjectMapper());
        when(bonita.buscarCaso("7")).thenReturn(Optional.of(new BonitaCasoInfo("7", null, "started", "", "")));
        variables(false);
        when(bonita.buscarTareaActiva("7", "Monitorear despliegue")).thenReturn(Optional.of("tarea-1"));
    }

    @Test
    void compromisoValidoYActividadActual() {
        var estado = cliente.consultar(10L, 7L);
        assertThat(estado.ofertaActualId()).isEqualTo(11L);
        assertThat(estado.ofertaComprometidaId()).isEqualTo(11L);
        assertThat(estado.cantidadComprometida()).isEqualTo(60);
        assertThat(estado.tareaMonitoreoId()).isEqualTo("tarea-1");
        assertThat(estado.casoTerminado()).isFalse();
    }

    @Test
    void casoArchivadoRecuperaVariablesPorIdOriginal() {
        when(bonita.buscarCaso("7")).thenReturn(Optional.empty());
        when(bonita.buscarCasoArchivado("7")).thenReturn(Optional.of(new BonitaCasoInfo("700", "7", "completed", "inicio", "fin")));
        variables(true);
        when(bonita.leerVariable("7", "ofertasProcesadasJson", true)).thenReturn(Optional.of("[11]"));
        var estado = cliente.consultar(10L, 7L);
        assertThat(estado.casoTerminado()).isTrue();
        assertThat(estado.ofertasLiberadas()).containsExactly(11L);
        verify(bonita, never()).buscarTareaActiva(any(), any());
    }

    @Test
    void ausenciaDelCasoNoEquivaleACierre() {
        when(bonita.buscarCaso("7")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> cliente.consultar(10L, 7L)).isInstanceOf(BonitaIntegrationException.class);
        when(bonita.buscarCasoArchivado("7")).thenReturn(Optional.of(new BonitaCasoInfo("700", "7", "aborted", "inicio", "fin")));
        assertThatThrownBy(() -> cliente.consultar(10L, 7L)).isInstanceOf(BonitaIntegrationException.class);
    }

    @Test
    void procesoAnteriorSinVariablesNoSimulaCompromisos() {
        when(bonita.leerVariable("7", "ofertaSeleccionadaId", false)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> cliente.consultar(10L, 7L)).isInstanceOf(BonitaIntegrationException.class)
                .hasMessageContaining("integración nacional");
    }

    @Test
    void rechazaReferenciaAjenaYDatosMalformados() {
        when(bonita.leerVariable("7", "compromisoNacionalJson", false))
                .thenReturn(Optional.of("{\"emergenciaRef\":99,\"ofertaRef\":11,\"cantidad\":60,\"estado\":\"COMPROMETIDO\"}"));
        assertThatThrownBy(() -> cliente.consultar(10L, 7L)).isInstanceOf(BonitaIntegrationException.class);
        when(bonita.leerVariable("7", "compromisoNacionalJson", false)).thenReturn(Optional.of("no es JSON"));
        assertThatThrownBy(() -> cliente.consultar(10L, 7L)).isInstanceOf(BonitaIntegrationException.class);
    }

    @Test
    void noMezclaDosCiclosDuranteLaConsulta() {
        when(bonita.leerVariable("7", "ofertaSeleccionadaId", false)).thenReturn(Optional.of("11"), Optional.of("12"));
        assertThatThrownBy(() -> cliente.consultar(10L, 7L)).isInstanceOf(BonitaIntegrationException.class)
                .hasMessageContaining("avanzó");
    }

    @Test
    void reconoceSoloTareasCompletadasYRechazaProcesadasDuplicadas() {
        when(bonita.listarActividadesArchivadas("7")).thenReturn(List.of(
                new BonitaActividadArchivada("1", "t1", "Finalizar actividad", "USER_TASK", "ready", "", ""),
                new BonitaActividadArchivada("2", "t2", "Monitorear despliegue", "USER_TASK", "completed", "", "")));
        assertThat(cliente.consultar(10L, 7L).tareasCompletadas()).containsExactly("t2");
        when(bonita.leerVariable("7", "ofertasProcesadasJson", false)).thenReturn(Optional.of("[11,11]"));
        assertThatThrownBy(() -> cliente.consultar(10L, 7L)).isInstanceOf(BonitaIntegrationException.class);
    }

    private void variables(boolean archivado) {
        when(bonita.leerVariable("7", "emergenciaId", archivado)).thenReturn(Optional.of("10"));
        when(bonita.leerVariable("7", "ofertaSeleccionadaId", archivado)).thenReturn(Optional.of("11"));
        when(bonita.leerVariable("7", "ofertasProcesadasJson", archivado)).thenReturn(Optional.of("[]"));
        when(bonita.leerVariable("7", "compromisoNacionalJson", archivado))
                .thenReturn(Optional.of("{\"emergenciaRef\":10,\"ofertaRef\":11,\"cantidad\":60,\"estado\":\"COMPROMETIDO\"}"));
    }
}
