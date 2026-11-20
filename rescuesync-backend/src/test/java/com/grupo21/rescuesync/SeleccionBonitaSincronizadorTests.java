package com.grupo21.rescuesync;

import com.grupo21.rescuesync.service.SeleccionBonitaCoordinador;
import com.grupo21.rescuesync.service.SeleccionBonitaRegistro;
import com.grupo21.rescuesync.service.SeleccionBonitaSincronizador;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.*;

class SeleccionBonitaSincronizadorTests {
    private final SeleccionBonitaRegistro registro = mock(SeleccionBonitaRegistro.class);
    private final SeleccionBonitaCoordinador coordinador = mock(SeleccionBonitaCoordinador.class);
    private final SeleccionBonitaSincronizador sincronizador = new SeleccionBonitaSincronizador(registro, coordinador);

    @Test
    void recuperaUnaIntencionPendienteEnUnaEjecucionPosterior() {
        when(registro.recuperables(1)).thenReturn(List.of(7L));
        sincronizador.recuperar(); // Bonita aún no tiene la tarea ready: el coordinador conserva PENDIENTE.
        sincronizador.recuperar(); // La próxima pasada vuelve a consultar el mismo caso.
        verify(coordinador, times(2)).despachar(7L);
    }

    @Test
    void dosPasadasConcurrentesNoInvocanDosDespachosLocales() throws Exception {
        when(registro.recuperables(1)).thenReturn(List.of(7L));
        var dentro = new CountDownLatch(1);
        var salida = new CountDownLatch(1);
        doAnswer(i -> { dentro.countDown(); salida.await(5, TimeUnit.SECONDS); return null; })
                .when(coordinador).despachar(7L);
        try (var ejecutor = Executors.newSingleThreadExecutor()) {
            var primera = ejecutor.submit(sincronizador::recuperar);
            org.assertj.core.api.Assertions.assertThat(dentro.await(5, TimeUnit.SECONDS)).isTrue();
            sincronizador.recuperar();
            salida.countDown();
            primera.get(5, TimeUnit.SECONDS);
        }
        verify(coordinador, times(1)).despachar(7L);
    }
}
