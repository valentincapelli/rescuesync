package com.grupo21.rescuesync;

import com.grupo21.rescuesync.client.BonitaCasoInfo;
import com.grupo21.rescuesync.client.BonitaActividadArchivada;
import com.grupo21.rescuesync.client.BonitaClient;
import com.grupo21.rescuesync.client.EstadoDespliegueBonita;
import com.grupo21.rescuesync.exception.BonitaIntegrationException;
import com.grupo21.rescuesync.service.SeleccionBonitaCoordinador;
import com.grupo21.rescuesync.service.SeleccionBonitaRegistro;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SeleccionBonitaCoordinadorTests {
    private final SeleccionBonitaRegistro registro = mock(SeleccionBonitaRegistro.class);
    private final BonitaClient bonita = mock(BonitaClient.class);
    private final SeleccionBonitaCoordinador coordinador = new SeleccionBonitaCoordinador(registro, bonita,
            mock(com.grupo21.rescuesync.service.NotificacionBonitaCoordinador.class));
    private final SeleccionBonitaRegistro.Envio envio =
            new SeleccionBonitaRegistro.Envio(10, 7, "task-10", true, Instant.now());

    @BeforeEach
    void casoValido() {
        when(registro.caso(1L)).thenReturn(7L);
        when(registro.puedeSeleccionar(1L)).thenReturn(true);
        when(bonita.buscarCaso("7")).thenReturn(Optional.of(new BonitaCasoInfo("7", null, "started", null, null)));
        when(bonita.leerVariable("7", "emergenciaId", false)).thenReturn(Optional.of("1"));
    }

    @Test
    void ausenciaYDuplicacionDeTareasReadyNoDespachan() {
        when(registro.puedeSeleccionar(1L)).thenReturn(true);
        when(bonita.buscarTareasActivas("7", "Seleccionar oferta"))
                .thenReturn(List.of()).thenReturn(List.of("task-10", "task-11"));
        coordinador.despachar(1L);
        coordinador.despachar(1L);
        verify(registro, never()).reservar(anyLong(), anyLong(), anyString());
        verify(registro).incidentePendiente(1L, 7L, "Hay más de una tarea Seleccionar oferta ready");
    }

    @Test
    void timeoutAntesDeReservarNoDejaUnEnvioYDespuesConservaElTaskId() {
        when(bonita.buscarCaso("7")).thenThrow(new BonitaIntegrationException("timeout", new SocketTimeoutException()));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> coordinador.despachar(1L))
                .isInstanceOf(BonitaIntegrationException.class);
        verify(registro, never()).reservar(anyLong(), anyLong(), anyString());
        reset(bonita);
        casoValido();
        prepararReserva();
        doThrow(new BonitaIntegrationException("timeout", new SocketTimeoutException()))
                .when(bonita).completarTareaPorId(eq("task-10"), anyMap());
        coordinador.despachar(1L);
        verify(registro, never()).incidente(eq(1L), any(), anyString());
        verify(bonita).completarTareaPorId(eq("task-10"), anyMap());
    }

    @Test
    void respuestaPerdidaSeReconciliaPorElMismoTaskIdSinReejecutar() {
        when(registro.enCurso(1L)).thenReturn(envio);
        when(bonita.listarActividadesArchivadas("7")).thenReturn(List.of(
                new BonitaActividadArchivada("99", "task-10", "Seleccionar oferta", "USER_TASK",
                        "completed", null, null)));
        coordinador.despachar(1L);
        verify(registro).confirmar(1L, envio);
        verify(bonita, never()).completarTareaPorId(anyString(), anyMap());
        verify(registro, never()).reservar(anyLong(), anyLong(), anyString());
    }

    @Test
    void timeoutAntiguoSoloReintentaLaMismaTareaSiSigueReady() {
        var antiguo = new SeleccionBonitaRegistro.Envio(10, 7, "task-10", true,
                Instant.now().minusSeconds(60));
        when(registro.enCurso(1L)).thenReturn(antiguo);
        when(bonita.listarActividadesArchivadas("7")).thenReturn(List.of());
        when(bonita.buscarTareasActivas("7", "Seleccionar oferta")).thenReturn(List.of("task-10"));
        when(registro.renovar(1L, antiguo)).thenReturn(envio);
        coordinador.despachar(1L);
        verify(registro).renovar(1L, antiguo);
        verify(bonita).completarTareaPorId(eq("task-10"), anyMap());
    }

    @Test
    void reservaConcurrenteSoloPermiteUnEnvio() throws Exception {
        prepararReserva();
        AtomicInteger reservas = new AtomicInteger();
        when(registro.reservar(1L, 7L, "task-10"))
                .thenAnswer(i -> reservas.getAndIncrement() == 0 ? envio : null);
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            var inicio = new CountDownLatch(1);
            var a = ejecutor.submit(() -> { inicio.await(); coordinador.despachar(1L); return null; });
            var b = ejecutor.submit(() -> { inicio.await(); coordinador.despachar(1L); return null; });
            inicio.countDown();
            a.get(5, TimeUnit.SECONDS);
            b.get(5, TimeUnit.SECONDS);
        }
        verify(bonita, times(1)).completarTareaPorId(eq("task-10"), anyMap());
    }

    @Test
    void compromisoEnCursoYLiberacionPendienteBloqueanSiguienteOferta() {
        when(registro.enCurso(1L)).thenReturn(envio);
        when(bonita.listarActividadesArchivadas("7")).thenReturn(List.of());
        coordinador.despachar(1L);
        verify(registro, never()).reservar(anyLong(), anyLong(), anyString());
        reset(registro);
        when(registro.tieneIntenciones(1L)).thenReturn(true);
        when(registro.caso(1L)).thenReturn(7L);
        coordinador.liberacionConfirmada(1L, estado(Set.of()));
        verify(registro).liberacionesConfirmadas(1L, 7L, Set.of());
        verify(registro, never()).reservar(anyLong(), anyLong(), anyString());
    }

    @Test
    void liberacionConfirmadaHabilitaElSiguienteCiclo() {
        when(registro.tieneIntenciones(1L)).thenReturn(true);
        prepararReserva();
        coordinador.liberacionConfirmada(1L, estado(Set.of(9L)));
        var orden = inOrder(registro, bonita);
        orden.verify(registro).liberacionesConfirmadas(1L, 7L, Set.of(9L));
        orden.verify(registro).reservar(1L, 7L, "task-10");
        orden.verify(bonita).completarTareaPorId(eq("task-10"), argThat(m ->
                Long.valueOf(10L).equals(m.get("ofertaSeleccionadaIdInput"))
                        && Boolean.TRUE.equals(m.get("continuarSeleccionandoInput"))));
    }

    @Test
    void conflicto409ConservaIncidenteYSuspendeElDespacho() {
        prepararReserva();
        doThrow(new BonitaIntegrationException("conflicto",
                HttpClientErrorException.create(HttpStatus.CONFLICT, "Conflict", null, null, null)))
                .when(bonita).completarTareaPorId(eq("task-10"), anyMap());
        coordinador.despachar(1L);
        verify(registro).incidente(1L, envio, "Bonita rechazó la ejecución: HTTP 409");
        verify(bonita, times(1)).completarTareaPorId(eq("task-10"), anyMap());
    }

    @Test
    void autenticacionRechazadaAntesDeReservarDejaIncidenteSinEnviar() {
        when(bonita.buscarCaso("7")).thenThrow(new BonitaIntegrationException("sin acceso",
                HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized", null, null, null)));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> coordinador.despachar(1L))
                .isInstanceOf(BonitaIntegrationException.class);
        verify(registro).incidentePendiente(1L, 7L,
                "El caso o la autenticación de Bonita requiere revisión");
        verify(registro, never()).reservar(anyLong(), anyLong(), anyString());
    }

    private void prepararReserva() {
        when(registro.puedeSeleccionar(1L)).thenReturn(true);
        when(bonita.buscarTareasActivas("7", "Seleccionar oferta"))
                .thenReturn(List.of("task-10"));
        when(registro.reservar(1L, 7L, "task-10")).thenReturn(envio);
    }

    private EstadoDespliegueBonita estado(Set<Long> liberadas) {
        return new EstadoDespliegueBonita(7L, null, null, null,
                liberadas, false, null, null, Set.of());
    }
}
