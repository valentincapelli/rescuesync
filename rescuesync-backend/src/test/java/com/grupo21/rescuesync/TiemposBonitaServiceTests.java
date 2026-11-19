package com.grupo21.rescuesync;

import com.grupo21.rescuesync.client.BonitaActividadArchivada;
import com.grupo21.rescuesync.client.BonitaCasoInfo;
import com.grupo21.rescuesync.client.BonitaClient;
import com.grupo21.rescuesync.client.FechasBonita;
import com.grupo21.rescuesync.dto.indicadores.CasoBonitaRef;
import com.grupo21.rescuesync.dto.indicadores.TiempoEtapaResponse;
import com.grupo21.rescuesync.dto.indicadores.TiemposEtapasResponse;
import com.grupo21.rescuesync.exception.BonitaIntegrationException;
import com.grupo21.rescuesync.model.EtapaEmergencia;
import com.grupo21.rescuesync.service.indicadores.TiemposBonitaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tiempos por etapa con Bonita (contrato, sección 3.4). Test unitario: {@link BonitaClient} mockeado,
 * sin levantar Spring.
 */
class TiemposBonitaServiceTests {

    private static final Duration TTL_EN_CURSO = Duration.ofMinutes(5);
    private static final Duration TTL_ARCHIVADOS = Duration.ofHours(12);

    private BonitaClient bonitaClient;
    private RelojMovil reloj;
    private TiemposBonitaService servicio;

    @BeforeEach
    void preparar() {
        bonitaClient = mock(BonitaClient.class);
        reloj = new RelojMovil(Instant.parse("2026-11-18T12:00:00Z"));
        servicio = new TiemposBonitaService(bonitaClient, TTL_EN_CURSO, TTL_ARCHIVADOS, reloj);
    }

    @Test
    void casoCompletoCalculaEtapasYDuracionTotal() {
        archivado("1", "2026-10-01 08:00:00.000", "2026-10-03 08:00:00.000");
        actividades("1",
                reg("10", "Registrar emergencia", "ready", "2026-10-01 08:00:00.000"),
                reg("10", "Registrar emergencia", "completed", "2026-10-01 10:00:00.000"),
                reg("11", "Revisar y desglosar emergencia", "ready", "2026-10-01 10:00:00.000"),
                reg("11", "Revisar y desglosar emergencia", "executing", "2026-10-01 10:30:00.000"),
                reg("11", "Revisar y desglosar emergencia", "completed", "2026-10-01 11:30:00.000"),
                reg("12", "Publicar convocatoria", "ready", "2026-10-01 12:00:00.000"),
                reg("12", "Publicar convocatoria", "completed", "2026-10-01 13:00:00.000"),
                reg("13", "Liberar recursos", "ready", "2026-10-03 07:00:00.000"),
                reg("13", "Liberar recursos", "completed", "2026-10-03 07:30:00.000"));

        TiemposEtapasResponse r = servicio.tiemposEtapas(List.of(caso(1)));

        assertThat(r.bonita().disponible()).isTrue();
        assertThat(r.bonita().mensaje()).isNull();
        assertThat(r.casosAnalizados()).isEqualTo(1);
        assertThat(r.casosFinalizados()).isEqualTo(1);
        assertThat(r.duracionPromedioTotalHoras()).isEqualTo(48.0);
        assertThat(r.etapas()).extracting(TiempoEtapaResponse::etapa)
                .containsExactly(EtapaEmergencia.values());
        assertEtapa(r, EtapaEmergencia.REGISTRO, 1, 3.5, 3.5, 3.5, 3.5);
        assertEtapa(r, EtapaEmergencia.CONVOCATORIA, 1, 1.0, 1.0, 1.0, 1.0);
        assertEtapa(r, EtapaEmergencia.CIERRE, 1, 0.5, 0.5, 0.5, 0.5);
        assertSinMuestras(r, EtapaEmergencia.EVALUACION);
        assertSinMuestras(r, EtapaEmergencia.COMPROMISO);
        assertSinMuestras(r, EtapaEmergencia.EJECUCION);
        verify(bonitaClient, never()).buscarCaso(anyString());
    }

    @Test
    void casoEnCursoIgnoraTareasSinCompletar() {
        enCurso("2");
        actividades("2",
                reg("20", "Registrar emergencia", "ready", "2026-10-01 08:00:00.000"),
                reg("20", "Registrar emergencia", "completed", "2026-10-01 09:00:00.000"),
                // Disponible pero todavía no completada: no cuenta.
                reg("21", "Revisar y desglosar emergencia", "ready", "2026-10-01 09:00:00.000"),
                // Abortada: no cuenta.
                reg("22", "Publicar convocatoria", "ready", "2026-10-01 09:00:00.000"),
                reg("22", "Publicar convocatoria", "aborted", "2026-10-01 12:00:00.000"));

        TiemposEtapasResponse r = servicio.tiemposEtapas(List.of(caso(2)));

        assertThat(r.bonita().disponible()).isTrue();
        assertThat(r.casosAnalizados()).isEqualTo(1);
        assertThat(r.casosFinalizados()).isZero();
        assertThat(r.duracionPromedioTotalHoras()).isNull();
        assertEtapa(r, EtapaEmergencia.REGISTRO, 1, 1.0, 1.0, 1.0, 1.0);
        assertSinMuestras(r, EtapaEmergencia.CONVOCATORIA);
    }

    @Test
    void tareasFueraDeTodaEtapaSeIgnoran() {
        enCurso("3");
        actividades("3",
                reg("30", "Tarea que no está en el enum", "ready", "2026-10-01 08:00:00.000"),
                reg("30", "Tarea que no está en el enum", "completed", "2026-10-01 18:00:00.000"),
                reg("31", "registrar emergencia", "ready", "2026-10-01 08:00:00.000"),
                reg("31", "registrar emergencia", "completed", "2026-10-01 18:00:00.000"));

        TiemposEtapasResponse r = servicio.tiemposEtapas(List.of(caso(3)));

        assertThat(r.bonita().disponible()).isTrue();
        assertThat(r.etapas()).hasSize(6).allSatisfy(e -> {
            assertThat(e.muestras()).isZero();
            assertThat(e.promedioHoras()).isNull();
        });
    }

    @Test
    void variosCasosConCantidadImpar() {
        casoConRegistro("1", 1);
        casoConRegistro("2", 6);
        casoConRegistro("3", 2);

        TiemposEtapasResponse r = servicio.tiemposEtapas(List.of(caso(1), caso(2), caso(3)));

        assertEtapa(r, EtapaEmergencia.REGISTRO, 3, 3.0, 2.0, 1.0, 6.0);
    }

    @Test
    void variosCasosConCantidadParYRedondeo() {
        casoConRegistro("1", 1);
        casoConRegistro("2", 10);
        casoConRegistro("3", 2);
        casoConRegistro("4", 4);

        TiemposEtapasResponse r = servicio.tiemposEtapas(List.of(caso(1), caso(2), caso(3), caso(4)));

        // promedio 17 / 4 = 4.25 -> 4.3; mediana (2 + 4) / 2 = 3.0
        assertEtapa(r, EtapaEmergencia.REGISTRO, 4, 4.3, 3.0, 1.0, 10.0);
        assertThat(r.casosAnalizados()).isEqualTo(4);
    }

    @Test
    void promedioTotalSoloDeCasosFinalizados() {
        archivado("1", "2026-10-01 00:00:00.000", "2026-10-02 00:00:00.000");
        actividades("1");
        archivado("2", "2026-10-01 00:00:00.000", "2026-10-01 12:20:00.000");
        actividades("2");
        enCurso("3");
        actividades("3");

        TiemposEtapasResponse r = servicio.tiemposEtapas(List.of(caso(1), caso(2), caso(3)));

        assertThat(r.casosFinalizados()).isEqualTo(2);
        // (24 + 12.333) / 2 = 18.1666 -> 18.2
        assertThat(r.duracionPromedioTotalHoras()).isEqualTo(18.2);
    }

    @Test
    void bonitaCaidoDevuelveNoDisponibleSinLanzar() {
        when(bonitaClient.buscarCasoArchivado(anyString()))
                .thenThrow(new BonitaIntegrationException("No se pudo autenticar contra Bonita en http://x (usuario: y)",
                        null));

        TiemposEtapasResponse r = servicio.tiemposEtapas(List.of(caso(1), caso(2)));

        assertNoDisponible(r, 2);
        // Corta en el primer error: no sigue consultando el resto.
        verify(bonitaClient, times(1)).buscarCasoArchivado(anyString());
    }

    @Test
    void timeoutDelRestClientDevuelveNoDisponibleSinLanzar() {
        casoConRegistro("1", 1);
        when(bonitaClient.buscarCasoArchivado("2")).thenThrow(new ResourceAccessException("Read timed out"));

        TiemposEtapasResponse r = servicio.tiemposEtapas(List.of(caso(1), caso(2)));

        assertNoDisponible(r, 2);
    }

    @Test
    void errorNoSeCacheaYLaSiguienteLlamadaReintenta() {
        when(bonitaClient.buscarCasoArchivado("1"))
                .thenThrow(new ResourceAccessException("Connection refused"))
                .thenReturn(Optional.of(casoInfo("1", "2026-10-01 00:00:00.000", "2026-10-01 10:00:00.000")));
        actividades("1");

        assertThat(servicio.tiemposEtapas(List.of(caso(1))).bonita().disponible()).isFalse();
        TiemposEtapasResponse r = servicio.tiemposEtapas(List.of(caso(1)));

        assertThat(r.bonita().disponible()).isTrue();
        assertThat(r.duracionPromedioTotalHoras()).isEqualTo(10.0);
    }

    @Test
    void casoInexistenteSeDescartaYElRestoSigue() {
        when(bonitaClient.buscarCasoArchivado("9")).thenReturn(Optional.empty());
        when(bonitaClient.buscarCaso("9")).thenReturn(Optional.empty());
        casoConRegistro("1", 2);

        TiemposEtapasResponse r = servicio.tiemposEtapas(List.of(caso(9), caso(1)));

        assertThat(r.bonita().disponible()).isTrue();
        assertThat(r.casosAnalizados()).isEqualTo(2);
        assertEtapa(r, EtapaEmergencia.REGISTRO, 1, 2.0, 2.0, 2.0, 2.0);
        verify(bonitaClient, never()).listarActividadesArchivadas("9");
    }

    @Test
    void listaVaciaNoLlamaABonita() {
        TiemposEtapasResponse r = servicio.tiemposEtapas(List.of());

        verifyNoInteractions(bonitaClient);
        assertThat(r.bonita().disponible()).isTrue();
        assertThat(r.bonita().consultadoEn()).isNotNull();
        assertThat(r.casosAnalizados()).isZero();
        assertThat(r.casosFinalizados()).isZero();
        assertThat(r.duracionPromedioTotalHoras()).isNull();
        assertThat(r.etapas()).hasSize(6).allSatisfy(e -> assertThat(e.muestras()).isZero());
    }

    @Test
    void segundaLlamadaUsaLaCache() {
        casoConRegistro("1", 2);
        Instant primeraConsulta = reloj.instant();

        servicio.tiemposEtapas(List.of(caso(1)));
        reloj.avanzar(Duration.ofMinutes(2));
        TiemposEtapasResponse r = servicio.tiemposEtapas(List.of(caso(1)));

        verify(bonitaClient, times(1)).buscarCasoArchivado("1");
        verify(bonitaClient, times(1)).buscarCaso("1");
        verify(bonitaClient, times(1)).listarActividadesArchivadas("1");
        assertEtapa(r, EtapaEmergencia.REGISTRO, 1, 2.0, 2.0, 2.0, 2.0);
        // consultadoEn informa cuándo se obtuvo el dato que está en caché.
        assertThat(r.bonita().consultadoEn()).isEqualTo(primeraConsulta);
    }

    @Test
    void casoEnCursoVenceConElTtlYElArchivadoDuraMas() {
        enCurso("1");
        actividades("1");
        archivado("2", "2026-10-01 00:00:00.000", "2026-10-02 00:00:00.000");
        actividades("2");

        servicio.tiemposEtapas(List.of(caso(1), caso(2)));
        reloj.avanzar(TTL_EN_CURSO.plusSeconds(1));
        servicio.tiemposEtapas(List.of(caso(1), caso(2)));

        verify(bonitaClient, times(2)).listarActividadesArchivadas("1");
        verify(bonitaClient, times(1)).listarActividadesArchivadas("2");

        reloj.avanzar(TTL_ARCHIVADOS);
        servicio.tiemposEtapas(List.of(caso(2)));
        verify(bonitaClient, times(2)).listarActividadesArchivadas("2");
    }

    @Test
    void tareasConFechasInvalidasSeDescartanSinRomperElCalculo() {
        enCurso("1");
        actividades("1",
                // Sin fechas.
                reg("10", "Registrar emergencia", "completed", ""),
                // Fecha mal formada, sin archivedDate.
                reg("11", "Revisar y desglosar emergencia", "ready", "basura"),
                reg("11", "Revisar y desglosar emergencia", "completed", "2026-13-45 99:00:00"),
                // Fin anterior al inicio.
                reg("12", "Publicar convocatoria", "ready", "2026-10-02 10:00:00.000"),
                reg("12", "Publicar convocatoria", "completed", "2026-10-01 10:00:00.000"),
                // Válida.
                reg("13", "Evaluar ofertas", "ready", "2026-10-01 10:00:00.000"),
                reg("13", "Evaluar ofertas", "completed", "2026-10-01 14:00:00.000"));

        TiemposEtapasResponse r = servicio.tiemposEtapas(List.of(caso(1)));

        assertThat(r.bonita().disponible()).isTrue();
        assertSinMuestras(r, EtapaEmergencia.REGISTRO);
        assertSinMuestras(r, EtapaEmergencia.CONVOCATORIA);
        assertEtapa(r, EtapaEmergencia.EVALUACION, 1, 4.0, 4.0, 4.0, 4.0);
    }

    @Test
    void actividadConUnSoloRegistroCompletadoSeDescartaPorqueNoSeSabeCuandoEmpezo() {
        enCurso("1");
        actividades("1", reg("10", "Registrar emergencia", "completed", "2026-10-01 10:00:00.000"));

        TiemposEtapasResponse r = servicio.tiemposEtapas(List.of(caso(1)));

        assertThat(r.bonita().disponible()).isTrue();
        assertSinMuestras(r, EtapaEmergencia.REGISTRO);
    }

    @Test
    void usaArchivedDateSiFaltaReachedStateDateYSumaRepeticionesDeUnaTarea() {
        enCurso("1");
        actividades("1",
                new BonitaActividadArchivada("a", "40", "Evaluar ofertas", "USER_TASK", "ready", null,
                        "2026-10-01 10:00:00.000"),
                reg("40", "Evaluar ofertas", "completed", "2026-10-01 11:00:00.000"),
                // Misma tarea ejecutada otra vez (otro sourceObjectId), por ejemplo en un ciclo.
                reg("41", "Evaluar ofertas", "ready", "2026-10-02 10:00:00.000"),
                reg("41", "Evaluar ofertas", "completed", "2026-10-02 12:00:00.000"),
                reg("42", "Seleccionar oferta", "ready", "2026-10-02 12:00:00.000"),
                reg("42", "Seleccionar oferta", "completed", "2026-10-02 12:30:00.000"));

        TiemposEtapasResponse r = servicio.tiemposEtapas(List.of(caso(1)));

        assertEtapa(r, EtapaEmergencia.EVALUACION, 1, 3.5, 3.5, 3.5, 3.5);
    }

    @Test
    void fechasBonitaSeParseanConToleranciaEnHoraArgentina() {
        Instant esperado = LocalDateTime.of(2026, 10, 21, 20, 31, 0, 123_000_000)
                .atZone(ZoneId.of("America/Argentina/Buenos_Aires")).toInstant();
        assertThat(FechasBonita.parsear("2026-10-21 20:31:00.123")).contains(esperado);
        assertThat(FechasBonita.parsear("2026-10-21T20:31:00.123")).contains(esperado);
        assertThat(FechasBonita.parsear(" 2026-10-21 20:31:00.1 ")).contains(esperado.minusMillis(23));
        assertThat(FechasBonita.parsear("2026-10-21 20:31:00")).contains(esperado.minusMillis(123));
        assertThat(esperado).isEqualTo(Instant.parse("2026-10-21T23:31:00.123Z"));
        assertThat(FechasBonita.parsear(null)).isEmpty();
        assertThat(FechasBonita.parsear("")).isEmpty();
        assertThat(FechasBonita.parsear("21/10/2026")).isEmpty();
        assertThat(FechasBonita.parsear("2026-02-30 10:00:00.000")).isEmpty();
    }

    // ---------------------------------------------------------------------------------------------

    private static CasoBonitaRef caso(long id) {
        return new CasoBonitaRef(100 + id, id);
    }

    private static BonitaActividadArchivada reg(String sourceObjectId, String nombre, String estado, String fecha) {
        return new BonitaActividadArchivada(sourceObjectId + "-" + estado, sourceObjectId, nombre, "USER_TASK",
                estado, fecha, fecha);
    }

    private static BonitaCasoInfo casoInfo(String id, String start, String end) {
        return new BonitaCasoInfo("arch-" + id, id, "completed", start, end);
    }

    private void archivado(String caseId, String start, String end) {
        when(bonitaClient.buscarCasoArchivado(caseId)).thenReturn(Optional.of(casoInfo(caseId, start, end)));
    }

    private void enCurso(String caseId) {
        when(bonitaClient.buscarCasoArchivado(caseId)).thenReturn(Optional.empty());
        when(bonitaClient.buscarCaso(caseId)).thenReturn(Optional.of(
                new BonitaCasoInfo(caseId, null, "started", "2026-10-01 08:00:00.000", "")));
    }

    private void actividades(String caseId, BonitaActividadArchivada... registros) {
        when(bonitaClient.listarActividadesArchivadas(caseId)).thenReturn(new ArrayList<>(Arrays.asList(registros)));
    }

    /** Caso en curso con una sola tarea de REGISTRO que duró {@code horas}. */
    private void casoConRegistro(String caseId, int horas) {
        enCurso(caseId);
        actividades(caseId,
                reg(caseId + "0", "Registrar emergencia", "ready", "2026-10-01 00:00:00.000"),
                reg(caseId + "0", "Registrar emergencia", "completed",
                        "2026-10-01 %02d:00:00.000".formatted(horas)));
    }

    private static TiempoEtapaResponse etapa(TiemposEtapasResponse r, EtapaEmergencia etapa) {
        return r.etapas().stream().filter(e -> e.etapa() == etapa).findFirst().orElseThrow();
    }

    private static void assertEtapa(TiemposEtapasResponse r, EtapaEmergencia etapa, long muestras,
                                    double promedio, double mediana, double minimo, double maximo) {
        TiempoEtapaResponse e = etapa(r, etapa);
        assertThat(e.nombre()).isEqualTo(etapa.getNombre());
        assertThat(e.tareas()).isEqualTo(etapa.getTareas());
        assertThat(e.muestras()).isEqualTo(muestras);
        assertThat(e.promedioHoras()).isEqualTo(promedio);
        assertThat(e.medianaHoras()).isEqualTo(mediana);
        assertThat(e.minimoHoras()).isEqualTo(minimo);
        assertThat(e.maximoHoras()).isEqualTo(maximo);
    }

    private static void assertSinMuestras(TiemposEtapasResponse r, EtapaEmergencia etapa) {
        TiempoEtapaResponse e = etapa(r, etapa);
        assertThat(e.muestras()).isZero();
        assertThat(e.promedioHoras()).isNull();
        assertThat(e.medianaHoras()).isNull();
        assertThat(e.minimoHoras()).isNull();
        assertThat(e.maximoHoras()).isNull();
    }

    private static void assertNoDisponible(TiemposEtapasResponse r, long casos) {
        assertThat(r.bonita().disponible()).isFalse();
        assertThat(r.bonita().mensaje()).isNotBlank()
                .doesNotContain("http", "usuario", "Exception", "timed out", "refused");
        assertThat(r.casosAnalizados()).isEqualTo(casos);
        assertThat(r.casosFinalizados()).isZero();
        assertThat(r.duracionPromedioTotalHoras()).isNull();
        assertThat(r.etapas()).hasSize(6).allSatisfy(e -> assertThat(e.muestras()).isZero());
    }

    /** Reloj que se puede adelantar a mano. */
    private static final class RelojMovil extends Clock {
        private Instant ahora;

        RelojMovil(Instant inicio) {
            this.ahora = inicio;
        }

        void avanzar(Duration duracion) {
            ahora = ahora.plus(duracion);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }
}
