package com.grupo21.rescuesync;

import com.grupo21.rescuesync.client.DespliegueBonitaClient;
import com.grupo21.rescuesync.dto.despliegues.DespliegueResponse;
import com.grupo21.rescuesync.exception.BonitaIntegrationException;
import com.grupo21.rescuesync.service.SeleccionBonitaCoordinador;
import com.grupo21.rescuesync.service.SeleccionBonitaRegistro;
import com.grupo21.rescuesync.service.despliegues.DespliegueRegistroService;
import com.grupo21.rescuesync.service.despliegues.DespliegueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** Sincronización de las actividades propias de una ONG con casos de Bonita que pueden fallar. */
class DespliegueServiceSincronizacionTests {
    private static final String EMAIL = "ong@test.com";

    private DespliegueRegistroService registro;
    private DespliegueService servicio;

    @BeforeEach
    void preparar() {
        registro = mock(DespliegueRegistroService.class);
        servicio = spy(new DespliegueService(registro, mock(DespliegueBonitaClient.class),
                mock(SeleccionBonitaCoordinador.class), mock(SeleccionBonitaRegistro.class)));
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
    void sinActualizarNoConsultaBonita() {
        servicio.propias(EMAIL, false);

        verify(servicio, never()).sincronizarCaso(anyLong());
    }
}
