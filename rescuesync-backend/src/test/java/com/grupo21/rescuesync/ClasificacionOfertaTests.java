package com.grupo21.rescuesync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grupo21.rescuesync.model.ClasificacionOferta;
import com.grupo21.rescuesync.model.NivelCompetencia;
import com.grupo21.rescuesync.model.NivelGravedad;
import com.grupo21.rescuesync.model.TipoRecurso;
import com.grupo21.rescuesync.model.ValidacionOferta;
import com.grupo21.rescuesync.service.ValidacionOfertasService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Regla principal/apoyo con perfiles reales de {@code POST /api/certificaciones/validar}. */
class ClasificacionOfertaTests {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Instant AHORA = Instant.parse("2026-11-19T12:00:00Z");

    @Test
    void riesgoMaximoIgualOMayorALaGravedadEsPrincipal() throws Exception {
        ValidacionOferta igual = clasificar(NivelGravedad.ALTO, habilitada(capacidad("INTERMEDIO", "ALTO", null)));
        ValidacionOferta mayor = clasificar(NivelGravedad.MEDIO, habilitada(capacidad("BASICO", "CRITICO", null)));

        assertThat(igual.getClasificacion()).isEqualTo(ClasificacionOferta.PRINCIPAL);
        assertThat(igual.getNivelCompetencia()).isEqualTo(NivelCompetencia.INTERMEDIO);
        assertThat(igual.getNivelRiesgoMax()).isEqualTo(NivelGravedad.ALTO);
        assertThat(mayor.getClasificacion()).isEqualTo(ClasificacionOferta.PRINCIPAL);
    }

    @Test
    void riesgoMaximoMenorALaGravedadEsApoyo() throws Exception {
        ValidacionOferta validacion = clasificar(NivelGravedad.CRITICO,
                habilitada(capacidad("AVANZADO", "ALTO", "2027-03-31")));

        assertThat(validacion.getClasificacion()).isEqualTo(ClasificacionOferta.APOYO);
        assertThat(validacion.getNivelCompetencia()).isEqualTo(NivelCompetencia.AVANZADO);
        assertThat(validacion.getCertificacionVence()).isEqualTo(LocalDate.of(2027, 3, 31));
        assertThat(validacion.getEvaluadaEn()).isEqualTo(AHORA);
    }

    @Test
    void sinCapacidadesNoTieneHabilitacionYConservaElMotivo() throws Exception {
        JsonNode perfil = JSON.readTree("""
                {"cuit":"30500000002","tipoRecurso":"ALIMENTOS","estadoHabilitacion":"HABILITADA",
                 "capacidadesHabilitadas":[],
                 "observaciones":"La ONG no tiene una certificación vigente para el tipo de recurso solicitado"}""");

        ValidacionOferta validacion = clasificar(NivelGravedad.BAJO, perfil);

        assertThat(validacion.getClasificacion()).isEqualTo(ClasificacionOferta.SIN_HABILITACION);
        assertThat(validacion.getNivelCompetencia()).isNull();
        assertThat(validacion.getNivelRiesgoMax()).isNull();
        assertThat(validacion.getObservaciones()).contains("no tiene una certificación vigente");
    }

    @Test
    void ongNoHabilitadaOCuitNoRegistradoNoTieneHabilitacion() throws Exception {
        JsonNode suspendida = JSON.readTree("""
                {"tipoRecurso":"ALIMENTOS","estadoHabilitacion":"SUSPENDIDA",
                 "capacidadesHabilitadas":[{"tipoRecurso":"ALIMENTOS","nivelCompetencia":"AVANZADO",
                 "nivelRiesgoMax":"CRITICO","fechaVencimiento":null}]}""");
        JsonNode inexistente = JSON.readTree("""
                {"tipoRecurso":"ALIMENTOS","estadoHabilitacion":null,"capacidadesHabilitadas":[]}""");

        assertThat(clasificar(NivelGravedad.BAJO, suspendida).getClasificacion())
                .isEqualTo(ClasificacionOferta.SIN_HABILITACION);
        assertThat(clasificar(NivelGravedad.BAJO, inexistente).getClasificacion())
                .isEqualTo(ClasificacionOferta.SIN_HABILITACION);
    }

    @Test
    void conVariasCapacidadesUsaLaDeMayorRiesgoYDespuesLaDeMayorCompetencia() throws Exception {
        JsonNode perfil = habilitada(
                capacidad("AVANZADO", "MEDIO", null),
                capacidad("BASICO", "ALTO", null),
                capacidad("INTERMEDIO", "ALTO", null));

        ValidacionOferta validacion = clasificar(NivelGravedad.ALTO, perfil);

        assertThat(validacion.getClasificacion()).isEqualTo(ClasificacionOferta.PRINCIPAL);
        assertThat(validacion.getNivelRiesgoMax()).isEqualTo(NivelGravedad.ALTO);
        assertThat(validacion.getNivelCompetencia()).isEqualTo(NivelCompetencia.INTERMEDIO);
    }

    @Test
    void ignoraCapacidadesDeOtroTipoOConRiesgoDesconocido() throws Exception {
        JsonNode perfil = JSON.readTree("""
                {"tipoRecurso":"ALIMENTOS","estadoHabilitacion":"HABILITADA","capacidadesHabilitadas":[
                  {"tipoRecurso":"MEDICAMENTOS","nivelCompetencia":"AVANZADO","nivelRiesgoMax":"CRITICO"},
                  {"tipoRecurso":"ALIMENTOS","nivelCompetencia":"AVANZADO","nivelRiesgoMax":"EXTREMO"}]}""");

        assertThat(clasificar(NivelGravedad.BAJO, perfil).getClasificacion())
                .isEqualTo(ClasificacionOferta.SIN_HABILITACION);
    }

    private static ValidacionOferta clasificar(NivelGravedad gravedad, JsonNode perfil) {
        return ValidacionOfertasService.clasificar(gravedad, TipoRecurso.ALIMENTOS, perfil, AHORA);
    }

    private static JsonNode habilitada(String... capacidades) throws Exception {
        return JSON.readTree("{\"cuit\":\"30500000001\",\"tipoRecurso\":\"ALIMENTOS\","
                + "\"estadoHabilitacion\":\"HABILITADA\",\"capacidadesHabilitadas\":["
                + String.join(",", capacidades) + "],\"observaciones\":\"Se informan el nivel de competencia\"}");
    }

    private static String capacidad(String competencia, String riesgo, String vence) {
        return "{\"tipoRecurso\":\"ALIMENTOS\",\"nivelCompetencia\":\"" + competencia + "\",\"nivelRiesgoMax\":\""
                + riesgo + "\",\"fechaVencimiento\":" + (vence == null ? "null" : "\"" + vence + "\"") + "}";
    }
}
