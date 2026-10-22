package com.grupo21.sistemanacional;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Documentación Swagger/OpenAPI (E3-11): el contrato se publica sin sesión, describe la seguridad
 * por cookie, marca como públicos solo los endpoints públicos y cubre todos los servicios de /api.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocsTests {

    @Autowired private MockMvc mockMvc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void contratoOpenApiSePublicaSinSesionConSeguridadPorCookie() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi", startsWith("3.")))
                .andExpect(jsonPath("$.info.title", containsString("Sistema Nacional")))
                .andExpect(jsonPath("$.components.securitySchemes.cookieAuth.type").value("apiKey"))
                .andExpect(jsonPath("$.components.securitySchemes.cookieAuth.in").value("cookie"))
                .andExpect(jsonPath("$.components.securitySchemes.cookieAuth.name").value("access_token"))
                .andExpect(jsonPath("$.security[0].cookieAuth").exists())
                .andExpect(jsonPath("$.components.schemas.ApiError").exists());
    }

    @Test
    void swaggerUiYElContratoYamlSonAccesiblesSinSesion() throws Exception {
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk());
    }

    @Test
    void soloLosEndpointsPublicosQuitanLaSeguridad() throws Exception {
        for (String ruta : List.of("/api/auth/login", "/api/auth/logout", "/api/ongs/registro")) {
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(jsonPath("$.paths['" + ruta + "'].post.security").isEmpty());
        }
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/info'].get.security").isEmpty())
                // El resto hereda la seguridad global: no declara nada propio.
                .andExpect(jsonPath("$.paths['/api/compromisos'].post.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/auth/me'].get.security").doesNotExist());
    }

    @Test
    void cadaOperacionIndicaSusRolesYSusErroresComunes() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                // comprometer: solo SERVICIO; 409 por stock insuficiente documentado en el controller
                .andExpect(jsonPath("$.paths['/api/compromisos'].post.description", containsString("SERVICIO")))
                .andExpect(jsonPath("$.paths['/api/compromisos'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/compromisos'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/compromisos'].post.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/compromisos'].post.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/api/compromisos'].post.responses['409']").exists())
                // liberar y validar: ADMIN o SERVICIO
                .andExpect(jsonPath("$.paths['/api/compromisos/liberar'].post.description",
                        containsString("ADMIN o SERVICIO")))
                .andExpect(jsonPath("$.paths['/api/certificaciones/validar'].post.description",
                        containsString("ADMIN o SERVICIO")))
                // ruta con id: 404 y 403 (una ONG no accede a datos de otra)
                .andExpect(jsonPath("$.paths['/api/ongs/{id}'].get.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/ongs/{id}'].get.responses['403']").exists())
                // los endpoints públicos no declaran 401 ni 403
                .andExpect(jsonPath("$.paths['/api/ongs/registro'].post.responses['401']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/ongs/registro'].post.responses['403']").doesNotExist());
    }

    /** Si alguien agrega un endpoint a /api y no lo documenta (@Operation con summary), este test falla. */
    @Test
    void todosLosEndpointsDeApiEstanEnElContratoConResumen() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        DocumentContext docs = JsonPath.parse(json);

        List<String> sinDocumentar = new ArrayList<>();
        int revisados = 0;
        for (var entrada : handlerMapping.getHandlerMethods().entrySet()) {
            Set<String> rutas = entrada.getKey().getPathPatternsCondition() == null
                    ? Set.of()
                    : entrada.getKey().getPathPatternsCondition().getPatternValues();
            for (String ruta : rutas) {
                if (!ruta.startsWith("/api/")) {
                    continue;
                }
                for (RequestMethod metodo : entrada.getKey().getMethodsCondition().getMethods()) {
                    revisados++;
                    String expresion = "$.paths['" + ruta + "']." + metodo.name().toLowerCase() + ".summary";
                    try {
                        String resumen = docs.read(expresion, String.class);
                        if (resumen == null || resumen.isBlank()) {
                            sinDocumentar.add(metodo + " " + ruta + " (sin summary)");
                        }
                    } catch (PathNotFoundException e) {
                        sinDocumentar.add(metodo + " " + ruta + " (no está en el contrato)");
                    }
                }
            }
        }

        assertThat(revisados).isEqualTo(19);
        assertThat(sinDocumentar).isEmpty();
    }
}
