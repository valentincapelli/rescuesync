package com.grupo21.rescuesync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grupo21.rescuesync.model.NivelGravedad;
import com.grupo21.rescuesync.model.TipoRecurso;
import com.grupo21.rescuesync.model.ValidacionOferta;
import com.grupo21.rescuesync.service.ValidacionOfertasService;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Stock que informa el Sistema Nacional en {@code POST /api/certificaciones/validar} ({@code disponibilidad}). */
class StockOfertaTests {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Instant AHORA = Instant.parse("2026-11-20T12:00:00Z");

    @Test
    void tomaElStockDeLaUnidadDelLoteSinDistinguirMayusculasNiEspacios() throws Exception {
        ValidacionOferta v = clasificar("raciones", perfil("""
                [{"unidadMedida":"Raciones ","cantidadDisponible":700},{"unidadMedida":"kg","cantidadDisponible":50}]"""));

        assertThat(v.getStockDisponible()).isEqualTo(700);
        assertThat(v.stockInsuficiente(700)).isFalse();
        assertThat(v.stockInsuficiente(701)).isTrue();
    }

    @Test
    void siLaOngNoTieneRecursosConEsaUnidadElStockEsCeroYLaOfertaNoSePuedeComprometer() throws Exception {
        ValidacionOferta v = clasificar("litros", perfil("""
                [{"unidadMedida":"raciones","cantidadDisponible":700}]"""));

        assertThat(v.getStockDisponible()).isZero();
        assertThat(v.stockInsuficiente(1)).isTrue();
    }

    @Test
    void conDisponibilidadVaciaElStockEsCero() throws Exception {
        assertThat(clasificar("raciones", perfil("[]")).getStockDisponible()).isZero();
    }

    @Test
    void sinElCampoDisponibilidadNoSeSabeElStockYNoSeBloquea() throws Exception {
        ValidacionOferta v = clasificar("raciones", JSON.readTree("""
                {"cuit":"30500000001","tipoRecurso":"ALIMENTOS","estadoHabilitacion":"HABILITADA",
                 "capacidadesHabilitadas":[{"tipoRecurso":"ALIMENTOS","nivelCompetencia":"INTERMEDIO",
                 "nivelRiesgoMax":"ALTO","fechaVencimiento":null}]}"""));

        assertThat(v.getStockDisponible()).isNull();
        assertThat(v.stockInsuficiente(1_000_000)).isFalse();
    }

    @Test
    void sinUnidadDeLoteNoSeCalculaElStock() throws Exception {
        ValidacionOferta v = ValidacionOfertasService.clasificar(NivelGravedad.ALTO, TipoRecurso.ALIMENTOS,
                perfil("[{\"unidadMedida\":\"raciones\",\"cantidadDisponible\":5}]"), AHORA);

        assertThat(v.getStockDisponible()).isNull();
    }

    @Test
    void unaOngSinHabilitacionNoTraeStock() throws Exception {
        ValidacionOferta v = clasificar("raciones", JSON.readTree("""
                {"cuit":"30500000001","tipoRecurso":"ALIMENTOS","estadoHabilitacion":"SUSPENDIDA",
                 "capacidadesHabilitadas":[],"disponibilidad":[]}"""));

        assertThat(v.getStockDisponible()).isNull();
    }

    private static ValidacionOferta clasificar(String unidad, JsonNode perfil) {
        return ValidacionOfertasService.clasificar(NivelGravedad.ALTO, TipoRecurso.ALIMENTOS, perfil, AHORA, unidad);
    }

    private static JsonNode perfil(String disponibilidad) throws Exception {
        return JSON.readTree("""
                {"cuit":"30500000001","tipoRecurso":"ALIMENTOS","estadoHabilitacion":"HABILITADA",
                 "capacidadesHabilitadas":[{"tipoRecurso":"ALIMENTOS","nivelCompetencia":"INTERMEDIO",
                 "nivelRiesgoMax":"ALTO","fechaVencimiento":null}],
                 "disponibilidad":%s}""".formatted(disponibilidad));
    }
}
