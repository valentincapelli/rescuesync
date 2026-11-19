package com.grupo21.rescuesync;

import com.grupo21.rescuesync.dto.indicadores.ConsorcioPorTipoRecursoResponse;
import com.grupo21.rescuesync.dto.indicadores.EstadoProyectosResponse;
import com.grupo21.rescuesync.dto.indicadores.FiltroIndicadores;
import com.grupo21.rescuesync.dto.indicadores.ProyectoResumenResponse;
import com.grupo21.rescuesync.dto.indicadores.ResolucionConsorciosResponse;
import com.grupo21.rescuesync.dto.indicadores.SuministroMensualResponse;
import com.grupo21.rescuesync.dto.indicadores.SuministrosMovilizadosResponse;
import com.grupo21.rescuesync.dto.indicadores.TotalSuministroResponse;
import com.grupo21.rescuesync.model.EstadoEmergencia;
import com.grupo21.rescuesync.model.TipoDesastre;
import com.grupo21.rescuesync.model.TipoRecurso;
import com.grupo21.rescuesync.service.indicadores.IndicadoresPropiosService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Valores de los indicadores propios (docs/e4-indicadores-contrato.md, 3.1 a 3.3).
 * Período de prueba: octubre y noviembre de 2026. Las fechas se ponen al mediodía de Argentina
 * para no depender de la zona horaria en los bordes de mes.
 */
@SpringBootTest(properties = {
        "jwt.secret=test-signing-key-for-integration-tests-32bytes",
        "jwt.expiration=86400000",
        "bonita.base-url=http://localhost:1/bonita"
})
class IndicadoresPropiosServiceTests {

    private static final FiltroIndicadores PERIODO = filtro(null, null, null);

    @Autowired private IndicadoresPropiosService service;
    @Autowired private JdbcTemplate jdbc;

    private long municipio1;
    private long municipio2;
    private long ong1;
    private long ong2;
    private long ong3;

    @BeforeEach
    void preparar() {
        municipio1 = insertarConId("INSERT INTO municipios (nombre, created_at, updated_at) "
                + "VALUES ('Muni indicadores 1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", "municipios");
        municipio2 = insertarConId("INSERT INTO municipios (nombre, created_at, updated_at) "
                + "VALUES ('Muni indicadores 2', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", "municipios");
        ong1 = ong("ONG ind 1");
        ong2 = ong("ONG ind 2");
        ong3 = ong("ONG ind 3");
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM historial_estados");
        jdbc.update("DELETE FROM ofertas");
        jdbc.update("DELETE FROM lotes");
        jdbc.update("DELETE FROM emergencias");
        jdbc.update("DELETE FROM ongs WHERE nombre LIKE 'ONG ind %'");
        jdbc.update("DELETE FROM municipios WHERE nombre LIKE 'Muni indicadores %'");
    }

    // ------------------------------------------------------------------ vacío

    @Test
    void sinDatosDevuelveCerosYNulls() {
        SuministrosMovilizadosResponse suministros = service.suministrosMovilizados(PERIODO);
        assertThat(suministros.series()).isEmpty();
        assertThat(suministros.totales()).isEmpty();

        ResolucionConsorciosResponse consorcios = service.resolucionConsorcios(PERIODO);
        assertThat(consorcios.lotesResueltos()).isZero();
        assertThat(consorcios.porcentajeConsorcio()).isNull();
        assertThat(consorcios.promedioOngsPorConsorcio()).isNull();
        assertThat(consorcios.porTipoRecurso()).isEmpty();

        EstadoProyectosResponse proyectos = service.estadoProyectos(PERIODO);
        assertThat(proyectos.total()).isZero();
        assertThat(proyectos.porcentajeFinalizados()).isNull();
        assertThat(proyectos.porEstado()).hasSize(EstadoEmergencia.values().length)
                .allSatisfy(c -> assertThat(c.cantidad()).isZero());
        assertThat(proyectos.proyectos()).isEmpty();
    }

    // ------------------------------------------------------------------ 3.1 suministros

    @Test
    void suministrosAgrupaPorMesTipoYUnidadNormalizada() {
        long e1 = emergencia("2026-10-01", "CONVOCATORIA_PUBLICADA", "INUNDACION", municipio1);
        long e2 = emergencia("2026-10-01", "CONVOCATORIA_PUBLICADA", "INCENDIO", municipio2);
        long alimentosE1 = lote(e1, "ALIMENTOS", 100, " Kg ", "CUBIERTO");
        long agua = lote(e1, "AGUA_POTABLE", 500, "litros", "CUBIERTO");
        long alimentosE2 = lote(e2, "ALIMENTOS", 10, "kg", "CUBIERTO");

        comprometida(oferta(alimentosE1, ong1, 30, "COMPROMETIDA"), e1, "2026-10-10");
        comprometida(oferta(alimentosE1, ong2, 20, "FINALIZADA"), e1, "2026-10-20");
        comprometida(oferta(alimentosE2, ong1, 5, "COMPROMETIDA"), e2, "2026-10-05");
        comprometida(oferta(agua, ong1, 100, "COMPROMETIDA"), e1, "2026-11-03");
        // Hoy rechazada: no cuenta aunque se haya comprometido en el período.
        comprometida(oferta(agua, ong2, 50, "RECHAZADA"), e1, "2026-10-12");
        // Primer compromiso antes del período: no cuenta, aunque haya otro dentro.
        long reComprometida = oferta(agua, ong3, 70, "COMPROMETIDA");
        comprometida(reComprometida, e1, "2026-09-01");
        comprometida(reComprometida, e1, "2026-10-15");

        SuministrosMovilizadosResponse r = service.suministrosMovilizados(PERIODO);

        assertThat(r.series()).containsExactly(
                new SuministroMensualResponse("2026-10", TipoRecurso.ALIMENTOS, "kg", 55, 3),
                new SuministroMensualResponse("2026-11", TipoRecurso.AGUA_POTABLE, "litros", 100, 1));
        assertThat(r.totales()).containsExactly(
                new TotalSuministroResponse(TipoRecurso.ALIMENTOS, "kg", 55, 3),
                new TotalSuministroResponse(TipoRecurso.AGUA_POTABLE, "litros", 100, 1));

        assertThat(service.suministrosMovilizados(filtro(municipio2, null, null)).series())
                .containsExactly(new SuministroMensualResponse("2026-10", TipoRecurso.ALIMENTOS, "kg", 5, 1));
        assertThat(service.suministrosMovilizados(filtro(null, TipoDesastre.INCENDIO, null)).totales())
                .containsExactly(new TotalSuministroResponse(TipoRecurso.ALIMENTOS, "kg", 5, 1));
        assertThat(service.suministrosMovilizados(filtro(null, null, TipoRecurso.AGUA_POTABLE)).series())
                .containsExactly(new SuministroMensualResponse("2026-11", TipoRecurso.AGUA_POTABLE, "litros", 100, 1));
    }

    // ------------------------------------------------------------------ 3.2 consorcios

    @Test
    void consorciosCuentaLotesResueltosPorDosOMasOngs() {
        long e = emergencia("2026-10-01", "EN_EJECUCION", "INUNDACION", municipio1);

        long consorcioDe2 = lote(e, "ALIMENTOS", 100, "kg", "CUBIERTO");
        cubierto(consorcioDe2, e, "2026-10-10");
        oferta(consorcioDe2, ong1, 50, "SELECCIONADA");
        oferta(consorcioDe2, ong2, 50, "COMPROMETIDA");
        oferta(consorcioDe2, ong3, 50, "RECHAZADA"); // no adjudicada: no suma ONG

        long ongUnica = lote(e, "AGUA_POTABLE", 100, "litros", "CERRADO");
        cubierto(ongUnica, e, "2026-11-01");
        oferta(ongUnica, ong1, 100, "FINALIZADA");

        long consorcioDe3 = lote(e, "ALIMENTOS", 90, "kg", "CUBIERTO");
        cubierto(consorcioDe3, e, "2026-10-25");
        oferta(consorcioDe3, ong1, 30, "COMPROMETIDA");
        oferta(consorcioDe3, ong2, 30, "FINALIZADA");
        oferta(consorcioDe3, ong3, 30, "SELECCIONADA");

        long cubiertoAntes = lote(e, "ALIMENTOS", 10, "kg", "CUBIERTO");
        cubierto(cubiertoAntes, e, "2026-08-01");
        oferta(cubiertoAntes, ong1, 10, "COMPROMETIDA");

        long yaNoCubierto = lote(e, "ALIMENTOS", 10, "kg", "REFORMULADO");
        cubierto(yaNoCubierto, e, "2026-10-05");
        oferta(yaNoCubierto, ong1, 10, "SELECCIONADA");

        long sinAdjudicadas = lote(e, "MEDICAMENTOS", 10, "unidades", "CUBIERTO");
        cubierto(sinAdjudicadas, e, "2026-10-05");
        oferta(sinAdjudicadas, ong1, 10, "PENDIENTE");

        ResolucionConsorciosResponse r = service.resolucionConsorcios(PERIODO);

        assertThat(r.lotesResueltos()).isEqualTo(3);
        assertThat(r.lotesPorConsorcio()).isEqualTo(2);
        assertThat(r.lotesPorOngUnica()).isEqualTo(1);
        assertThat(r.porcentajeConsorcio()).isEqualTo(66.7);
        assertThat(r.promedioOngsPorConsorcio()).isEqualTo(2.5);
        assertThat(r.porTipoRecurso()).containsExactly(
                new ConsorcioPorTipoRecursoResponse(TipoRecurso.ALIMENTOS, 2, 2, 100.0),
                new ConsorcioPorTipoRecursoResponse(TipoRecurso.AGUA_POTABLE, 1, 0, 0.0));

        ResolucionConsorciosResponse soloAgua = service.resolucionConsorcios(filtro(null, null, TipoRecurso.AGUA_POTABLE));
        assertThat(soloAgua.lotesResueltos()).isEqualTo(1);
        assertThat(soloAgua.porcentajeConsorcio()).isEqualTo(0.0);
        assertThat(soloAgua.promedioOngsPorConsorcio()).isNull();

        assertThat(service.resolucionConsorcios(filtro(municipio2, null, null)).lotesResueltos()).isZero();
    }

    // ------------------------------------------------------------------ 3.3 proyectos

    @Test
    void proyectosCuentaEstadosCoberturaYFechaDeCierre() {
        long cerrada = emergencia("2026-10-05", "CERRADA", "INUNDACION", municipio1);
        historial("EMERGENCIA", cerrada, cerrada, "EN_EJECUCION", "CERRADA", "2026-11-10");
        long loteParcial = lote(cerrada, "ALIMENTOS", 100, "kg", "CERRADO");
        oferta(loteParcial, ong1, 30, "SELECCIONADA");
        oferta(loteParcial, ong2, 20, "COMPROMETIDA");
        oferta(loteParcial, ong3, 40, "RECHAZADA");
        long loteSobrecubierto = lote(cerrada, "AGUA_POTABLE", 50, "litros", "CERRADO");
        oferta(loteSobrecubierto, ong1, 80, "FINALIZADA"); // se toma como 50: tope por lote

        long registrada = emergencia("2026-11-01", "REGISTRADA", "INCENDIO", municipio2);
        emergencia("2026-09-01", "CERRADA", "INUNDACION", municipio1); // fuera del período

        EstadoProyectosResponse r = service.estadoProyectos(PERIODO);

        assertThat(r.total()).isEqualTo(2);
        assertThat(r.finalizados()).isEqualTo(1);
        assertThat(r.enCurso()).isEqualTo(1);
        assertThat(r.porcentajeFinalizados()).isEqualTo(50.0);
        assertThat(r.porEstado()).hasSize(EstadoEmergencia.values().length);
        assertThat(r.porEstado().get(0).estado()).isEqualTo(EstadoEmergencia.REGISTRADA);
        assertThat(r.porEstado()).filteredOn(c -> c.cantidad() > 0)
                .extracting(c -> c.estado() + "=" + c.cantidad())
                .containsExactly("REGISTRADA=1", "CERRADA=1");

        assertThat(r.proyectos()).extracting(ProyectoResumenResponse::id).containsExactly(registrada, cerrada);
        ProyectoResumenResponse pRegistrada = r.proyectos().get(0);
        assertThat(pRegistrada.lotes()).isZero();
        assertThat(pRegistrada.porcentajeCobertura()).isNull();
        assertThat(pRegistrada.fechaCierre()).isNull();
        assertThat(pRegistrada.municipio()).isEqualTo("Muni indicadores 2");

        ProyectoResumenResponse pCerrada = r.proyectos().get(1);
        assertThat(pCerrada.lotes()).isEqualTo(2);
        assertThat(pCerrada.porcentajeCobertura()).isEqualTo(66.7); // (50 + 50) / 150
        assertThat(pCerrada.fechaCierre()).isEqualTo(mediodia("2026-11-10"));
        assertThat(pCerrada.fechaRegistro()).isEqualTo(mediodia("2026-10-05"));

        assertThat(service.estadoProyectos(filtro(null, TipoDesastre.INCENDIO, null)).total()).isEqualTo(1);
        assertThat(service.estadoProyectos(filtro(municipio1, null, null)).proyectos())
                .extracting(ProyectoResumenResponse::id).containsExactly(cerrada);
        // tipoRecurso no aplica a proyectos.
        assertThat(service.estadoProyectos(filtro(null, null, TipoRecurso.MEDICAMENTOS)).total()).isEqualTo(2);
    }

    // ------------------------------------------------------------------ helpers

    private static FiltroIndicadores filtro(Long municipioId, TipoDesastre tipoDesastre, TipoRecurso tipoRecurso) {
        return new FiltroIndicadores(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-11-30"),
                municipioId, tipoDesastre, tipoRecurso).normalizado();
    }

    private static Instant mediodia(String fecha) {
        return LocalDate.parse(fecha).atTime(LocalTime.NOON).atZone(FiltroIndicadores.ZONA).toInstant();
    }

    private long emergencia(String fecha, String estado, String tipoDesastre, long municipioId) {
        jdbc.update("INSERT INTO emergencias "
                        + "(tipo_desastre, nivel_gravedad, zona_afectada, descripcion, municipio_id, estado, created_at, updated_at) "
                        + "VALUES (?, 'ALTO', 'Zona', 'Descripción', ?, ?, ?, ?)",
                tipoDesastre, municipioId, estado, ts(fecha), ts(fecha));
        return ultimoId("emergencias");
    }

    private long lote(long emergenciaId, String tipo, int cantidad, String unidad, String estado) {
        jdbc.update("INSERT INTO lotes "
                        + "(emergencia_id, tipo_recurso, descripcion, cantidad_requerida, unidad_medida, estado, created_at, updated_at) "
                        + "VALUES (?, ?, 'Lote', ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                emergenciaId, tipo, cantidad, unidad, estado);
        return ultimoId("lotes");
    }

    private long oferta(long loteId, long ongId, int cantidad, String estado) {
        jdbc.update("INSERT INTO ofertas (lote_id, ong_id, cantidad_ofrecida, estado, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", loteId, ongId, cantidad, estado);
        return ultimoId("ofertas");
    }

    private void comprometida(long ofertaId, long emergenciaId, String fecha) {
        historial("OFERTA", ofertaId, emergenciaId, "SELECCIONADA", "COMPROMETIDA", fecha);
    }

    private void cubierto(long loteId, long emergenciaId, String fecha) {
        historial("LOTE", loteId, emergenciaId, "PUBLICADO", "CUBIERTO", fecha);
    }

    private void historial(String tipo, long entidadId, long emergenciaId, String anterior, String nuevo, String fecha) {
        jdbc.update("INSERT INTO historial_estados "
                        + "(tipo_entidad, entidad_id, emergencia_id, estado_anterior, estado_nuevo, fecha_cambio, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                tipo, entidadId, emergenciaId, anterior, nuevo, ts(fecha));
    }

    private long ong(String nombre) {
        return insertarConId("INSERT INTO ongs (nombre, created_at, updated_at) "
                + "VALUES ('" + nombre + "', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", "ongs");
    }

    private long insertarConId(String sql, String tabla) {
        jdbc.update(sql);
        return ultimoId(tabla);
    }

    private long ultimoId(String tabla) {
        return jdbc.queryForObject("SELECT MAX(id) FROM " + tabla, Long.class);
    }

    private static Timestamp ts(String fecha) {
        return Timestamp.from(mediodia(fecha));
    }
}
