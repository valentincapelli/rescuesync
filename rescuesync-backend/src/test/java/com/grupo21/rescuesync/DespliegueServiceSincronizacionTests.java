package com.grupo21.rescuesync;

import com.grupo21.rescuesync.client.DespliegueBonitaClient;
import com.grupo21.rescuesync.client.EstadoDespliegueBonita;
import com.grupo21.rescuesync.dto.despliegues.DespliegueResponse;
import com.grupo21.rescuesync.exception.BonitaIntegrationException;
import com.grupo21.rescuesync.service.SeleccionBonitaCoordinador;
import com.grupo21.rescuesync.service.SeleccionBonitaRegistro;
import com.grupo21.rescuesync.service.despliegues.DespliegueRegistroService;
import com.grupo21.rescuesync.service.despliegues.DespliegueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** Sincronización de las actividades propias de una ONG con casos de Bonita que pueden fallar. */
class DespliegueServiceSincronizacionTests {
    private static final String EMAIL = "ong@test.com";

    private DespliegueRegistroService registro;
    private DespliegueBonitaClient bonita;
    private SeleccionBonitaRegistro selecciones;
    private DespliegueService servicio;
    /** Servicio sin spy, para ejercitar sincronizarCaso de verdad. */
    private DespliegueService real;

    @BeforeEach
    void preparar() {
        registro = mock(DespliegueRegistroService.class);
        bonita = mock(DespliegueBonitaClient.class);
        selecciones = mock(SeleccionBonitaRegistro.class);
        var coordinador = mock(SeleccionBonitaCoordinador.class);
        servicio = spy(new DespliegueService(registro, bonita, coordinador, selecciones));
        real = new DespliegueService(registro, bonita, coordinador, selecciones);
        when(registro.propias(EMAIL)).thenReturn(List.<DespliegueResponse>of());
    }

    @Test
    void unCasoQueFallaNoImpideSincronizarLosDemas() {
        when(registro.casosPropios(EMAIL)).thenReturn(List.of(9L, 16L, 15L));
        doThrow(new BonitaIntegrationException("caso inexistente", null)).when(servicio).sincronizarCaso(9L);
        doNothing().when(servicio).sincronizarCaso(16L);
        doNothing().when(servicio).sincronizarCaso(15L);

        assertThat(servicio.propias(EMAIL, true)).isEmpty();

        verify(servicio).sincronizarCaso(9L);
        verify(servicio).sincronizarCaso(16L);
        verify(servicio).sincronizarCaso(15L);
    }

    @Test
    void siFallanTodosLosCasosSePropagaElPrimerError() {
        when(registro.casosPropios(EMAIL)).thenReturn(List.of(9L, 10L));
        var primero = new BonitaIntegrationException("Bonita caído", null);
        doThrow(primero).when(servicio).sincronizarCaso(9L);
        doThrow(new BonitaIntegrationException("otro", null)).when(servicio).sincronizarCaso(10L);

        assertThatThrownBy(() -> servicio.propias(EMAIL, true)).isSameAs(primero);

        verify(servicio).sincronizarCaso(10L);
    }

    @Test
    void tareaFallidaEnBonitaMarcaLaSeleccionEnCursoComoIncidente() {
        prepararCaso(false);
        when(bonita.tareaFallida(7L)).thenReturn(Optional.of("Comprometer recursos"));

        real.sincronizarCaso(15L);

        verify(selecciones).incidenteEnCiclo(eq(15L), eq(7L), contains("Comprometer recursos"));
    }

    @Test
    void sinTareaFallidaNoSeMarcaIncidente() {
        prepararCaso(false);
        when(bonita.tareaFallida(7L)).thenReturn(Optional.empty());

        real.sincronizarCaso(15L);

        verify(selecciones, never()).incidenteEnCiclo(any(), any(), any());
    }

    @Test
    void casoTerminadoNoConsultaIncidentes() {
        prepararCaso(true);

        real.sincronizarCaso(15L);

        verify(bonita, never()).tareaFallida(any());
    }

    @Test
    void siFallaLaConsultaDeIncidentesElSeguimientoContinua() {
        prepararCaso(false);
        when(bonita.tareaFallida(7L)).thenThrow(new BonitaIntegrationException("sin respuesta", null));

        real.sincronizarCaso(15L);

        verify(registro).aplicar(eq(15L), any());
        verify(selecciones, never()).incidenteEnCiclo(any(), any(), any());
    }

    private void prepararCaso(boolean terminado) {
        var estado = new EstadoDespliegueBonita(7L, null, null, null, Set.of(), terminado, null, null, Set.of());
        when(registro.caso(15L)).thenReturn(new DespliegueRegistroService.Caso(15L, 7L));
        when(bonita.consultar(15L, 7L)).thenReturn(estado);
        when(registro.pendientes(15L)).thenReturn(List.of());
    }

    @Test
    void sinActualizarNoConsultaBonita() {
        servicio.propias(EMAIL, false);

        verify(servicio, never()).sincronizarCaso(anyLong());
    }
}
