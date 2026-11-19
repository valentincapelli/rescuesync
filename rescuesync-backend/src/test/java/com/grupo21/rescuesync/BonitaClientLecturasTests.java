package com.grupo21.rescuesync;

import com.grupo21.rescuesync.client.BonitaActividadArchivada;
import com.grupo21.rescuesync.client.BonitaCasoInfo;
import com.grupo21.rescuesync.client.BonitaClient;
import com.grupo21.rescuesync.config.BonitaProperties;
import com.grupo21.rescuesync.exception.BonitaIntegrationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Consultas de solo lectura de {@link BonitaClient} para los indicadores (E4), contra un servidor
 * simulado. Las respuestas imitan el formato de la referencia de la REST API de Bonita.
 */
class BonitaClientLecturasTests {

    private static final String BASE = "http://bonita.test/bonita";

    private MockRestServiceServer server;
    private BonitaClient client;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new BonitaClient(builder.build(), new BonitaProperties(BASE, "walter.bates", "bpm", "RescueSync"));
    }

    @Test
    void variablesActivasYArchivadasUsanElIdOriginalDelCaso() {
        esperarLogin();
        server.expect(requestTo(BASE + "/API/bpm/caseVariable/7/ofertaSeleccionadaId"))
                .andRespond(withSuccess("{\"name\":\"ofertaSeleccionadaId\",\"value\":11}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/API/bpm/archivedCaseVariable/7/ofertasProcesadasJson"))
                .andRespond(withSuccess("{\"name\":\"ofertasProcesadasJson\",\"value\":\"[11]\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/API/bpm/caseVariable/7/inexistente"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThat(client.leerVariable("7", "ofertaSeleccionadaId", false)).contains("11");
        assertThat(client.leerVariable("7", "ofertasProcesadasJson", true)).contains("[11]");
        assertThat(client.leerVariable("7", "inexistente", false)).isEmpty();
        server.verify();
    }

    @Test
    void completarPorIdAsignaYEjecutaElContratoVacioSinBuscarOtraTarea() {
        esperarLogin();
        server.expect(requestTo(BASE + "/API/system/session/unusedId"))
                .andRespond(withSuccess("{\"user_id\":\"4\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/API/bpm/humanTask/17"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content().json("{\"assigned_id\":\"4\"}"))
                .andRespond(withStatus(HttpStatus.OK));
        server.expect(requestTo(BASE + "/API/bpm/userTask/17/execution"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content().json("{}"))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, "application/json"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));
        client.completarTareaPorId("17");
        server.verify();
    }

    @Test
    void casoActivoYCasoInexistente() {
        esperarLogin();
        server.expect(requestTo(BASE + "/API/bpm/case/7"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.COOKIE, "JSESSIONID=sesion; X-Bonita-API-Token=token"))
                .andRespond(withSuccess("""
                        {"id":"7","state":"started","start":"2026-10-01 08:00:00.000","end_date":"",
                         "processDefinitionId":"123","rootCaseId":"7","started_by":"4"}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/API/bpm/case/8"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        Optional<BonitaCasoInfo> activo = client.buscarCaso("7");
        Optional<BonitaCasoInfo> inexistente = client.buscarCaso("8");

        assertThat(activo).hasValueSatisfying(caso -> {
            assertThat(caso.id()).isEqualTo("7");
            assertThat(caso.start()).isEqualTo("2026-10-01 08:00:00.000");
            assertThat(caso.end_date()).isEmpty();
        });
        assertThat(inexistente).isEmpty();
        server.verify();
    }

    @Test
    void casoArchivadoFiltraPorSourceObjectIdYPrefiereElQueTieneEndDate() {
        esperarLogin();
        server.expect(conQuery("/API/bpm/archivedCase", "f=sourceObjectId=7"))
                .andRespond(withSuccess("""
                        [{"id":"50","sourceObjectId":"7","state":"started","start":"2026-10-01 08:00:00.000",
                          "end_date":"","archivedDate":"2026-10-01 08:00:00.000"},
                         {"id":"51","sourceObjectId":"7","state":"completed","start":"2026-10-01 08:00:00.000",
                          "end_date":"2026-10-03 08:00:00.000","archivedDate":"2026-10-03 08:00:00.001"}]
                        """, MediaType.APPLICATION_JSON));
        server.expect(conQuery("/API/bpm/archivedCase", "f=sourceObjectId=8"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.buscarCasoArchivado("7")).hasValueSatisfying(caso -> {
            assertThat(caso.id()).isEqualTo("51");
            assertThat(caso.end_date()).isEqualTo("2026-10-03 08:00:00.000");
        });
        assertThat(client.buscarCasoArchivado("8")).isEmpty();
        server.verify();
    }

    @Test
    void actividadesArchivadasRecorrenTodasLasPaginas() {
        esperarLogin();
        server.expect(conQuery("/API/bpm/archivedFlowNode", "p=0", "c=100", "f=caseId=7"))
                .andRespond(withSuccess(pagina(0, 100), MediaType.APPLICATION_JSON));
        server.expect(conQuery("/API/bpm/archivedFlowNode", "p=1", "c=100", "f=caseId=7"))
                .andRespond(withSuccess(pagina(100, 3), MediaType.APPLICATION_JSON));

        List<BonitaActividadArchivada> actividades = client.listarActividadesArchivadas("7");

        assertThat(actividades).hasSize(103);
        BonitaActividadArchivada primera = actividades.get(0);
        assertThat(primera.sourceObjectId()).isEqualTo("1000");
        assertThat(primera.name()).isEqualTo("Registrar emergencia");
        assertThat(primera.type()).isEqualTo("USER_TASK");
        assertThat(primera.state()).isEqualTo("completed");
        assertThat(primera.reached_state_date()).isEqualTo("2026-10-01 10:00:00.000");
        assertThat(primera.archivedDate()).isEqualTo("2026-10-01 10:00:00.005");
        server.verify();
    }

    @Test
    void sesionVencidaSeReautenticaYReintenta() {
        esperarLogin();
        server.expect(requestTo(BASE + "/API/bpm/case/7")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        esperarLogin();
        server.expect(requestTo(BASE + "/API/bpm/case/7"))
                .andRespond(withSuccess("{\"id\":\"7\",\"start\":\"2026-10-01 08:00:00.000\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.buscarCaso("7")).isPresent();
        server.verify();
    }

    @Test
    void errorDelServidorSeTraduceABonitaIntegrationException() {
        esperarLogin();
        server.expect(requestTo(BASE + "/API/bpm/case/7")).andRespond(withServerError());

        assertThatThrownBy(() -> client.buscarCaso("7")).isInstanceOf(BonitaIntegrationException.class);
    }

    // ---------------------------------------------------------------------------------------------

    private void esperarLogin() {
        HttpHeaders cookies = new HttpHeaders();
        cookies.add(HttpHeaders.SET_COOKIE, "JSESSIONID=sesion; Path=/bonita; HttpOnly");
        cookies.add(HttpHeaders.SET_COOKIE, "X-Bonita-API-Token=token; Path=/bonita");
        server.expect(requestTo(BASE + "/loginservice"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.NO_CONTENT).headers(cookies));
    }

    /** Ruta exacta y parámetros de query (ya decodificados) incluidos. */
    private static RequestMatcher conQuery(String ruta, String... parametros) {
        return request -> {
            assertThat(request.getURI().getPath()).isEqualTo("/bonita" + ruta);
            assertThat(request.getMethod()).isEqualTo(HttpMethod.GET);
            List<String> query = List.of(request.getURI().getQuery().split("&"));
            assertThat(query).contains(parametros);
        };
    }

    private static String pagina(int desde, int cantidad) {
        return IntStream.range(desde, desde + cantidad)
                .mapToObj(i -> """
                        {"id":"%d","sourceObjectId":"%d","name":"Registrar emergencia","type":"USER_TASK",
                         "state":"completed","reached_state_date":"2026-10-01 10:00:00.000",
                         "archivedDate":"2026-10-01 10:00:00.005","rootCaseId":"7","parentCaseId":"7",
                         "executedBy":"4","displayName":"Registrar emergencia"}
                        """.formatted(5000 + i, 1000 + i))
                .collect(Collectors.joining(",", "[", "]"));
    }
}
