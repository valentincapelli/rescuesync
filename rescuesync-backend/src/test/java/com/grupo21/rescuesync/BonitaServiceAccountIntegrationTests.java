package com.grupo21.rescuesync;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:rescuesync-bonita-service;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "jwt.secret=test-signing-key-for-integration-tests-32bytes",
        "jwt.expiration=86400000"
})
@AutoConfigureMockMvc
class BonitaServiceAccountIntegrationTests {

    private static final String EMAIL = "servicio-bonita@test.local";
    private static final String PASSWORD = UUID.randomUUID().toString();

    @DynamicPropertySource
    static void credencialesTecnicas(DynamicPropertyRegistry registry) {
        registry.add("RESCUESYNC_BONITA_SERVICE_EMAIL", () -> EMAIL);
        registry.add("RESCUESYNC_BONITA_SERVICE_PASSWORD", () -> PASSWORD);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;

    private long emergenciaId;
    private long loteId;

    @BeforeEach
    void prepararEmergencia() {
        jdbcTemplate.update("INSERT INTO municipios (nombre, created_at, updated_at) "
                + "VALUES ('Municipio cuenta Bonita', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        Long municipioId = jdbcTemplate.queryForObject(
                "SELECT id FROM municipios WHERE nombre = 'Municipio cuenta Bonita'", Long.class);
        jdbcTemplate.update("INSERT INTO emergencias "
                        + "(tipo_desastre, nivel_gravedad, zona_afectada, descripcion, municipio_id, estado, created_at, updated_at) "
                        + "VALUES ('INUNDACION', 'ALTO', 'Zona Bonita', 'Prueba de permisos', ?, 'REGISTRADA', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                municipioId);
        emergenciaId = jdbcTemplate.queryForObject(
                "SELECT id FROM emergencias WHERE zona_afectada = 'Zona Bonita'", Long.class);
        jdbcTemplate.update("INSERT INTO lotes "
                        + "(emergencia_id, tipo_recurso, descripcion, cantidad_requerida, unidad_medida, estado, created_at, updated_at) "
                        + "VALUES (?, 'ALIMENTOS', 'Raciones', 20, 'unidades', 'PUBLICADO', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                emergenciaId);
        loteId = jdbcTemplate.queryForObject(
                "SELECT id FROM lotes WHERE emergencia_id = ?", Long.class, emergenciaId);
    }

    @AfterEach
    void limpiarEmergencia() {
        jdbcTemplate.update("DELETE FROM ofertas");
        jdbcTemplate.update("DELETE FROM lotes WHERE emergencia_id = ?", emergenciaId);
        jdbcTemplate.update("DELETE FROM emergencias WHERE id = ?", emergenciaId);
        jdbcTemplate.update("DELETE FROM municipios WHERE nombre = 'Municipio cuenta Bonita'");
    }

    @Test
    void creaCuentaSinMunicipioNiOngYAutenticaConLoginExistente() throws Exception {
        Map<String, Object> usuario = jdbcTemplate.queryForMap(
                "SELECT rol, municipio_id, ong_id, password_hash FROM usuarios WHERE email = ?", EMAIL);
        assertThat(usuario).containsEntry("ROL", "SERVICIO_BONITA")
                .containsEntry("MUNICIPIO_ID", null)
                .containsEntry("ONG_ID", null);
        assertThat(usuario.get("PASSWORD_HASH")).isNotEqualTo(PASSWORD);

        Cookie cookie = iniciarSesion();
        assertThat(cookie.isHttpOnly()).isTrue();
        mockMvc.perform(get("/api/auth/me").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("SERVICIO_BONITA"));
    }

    @Test
    void consultaOfertasConsolidadasConLaCookieDelLogin() throws Exception {
        mockMvc.perform(get("/api/emergencias/" + emergenciaId + "/ofertas-consolidadas")
                        .cookie(iniciarSesion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(loteId))
                .andExpect(jsonPath("$[0].totalOfrecido").value(0))
                .andExpect(jsonPath("$[0].ofertas.length()").value(0));
    }

    @Test
    void rechazaCredencialesIncorrectasYSolicitudSinCookie() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"incorrecta\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/emergencias/" + emergenciaId + "/ofertas-consolidadas"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rechazaOtrasConsultasYOperaciones() throws Exception {
        Cookie cookie = iniciarSesion();
        mockMvc.perform(get("/api/emergencias").cookie(cookie)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/emergencias/" + emergenciaId).cookie(cookie))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/emergencias/" + emergenciaId + "/lotes").cookie(cookie))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/emergencias/" + emergenciaId + "/convocatoria").cookie(cookie))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/emergencias/" + emergenciaId + "/lotes").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipoRecurso":"ALIMENTOS","descripcion":"Más raciones",
                                 "cantidadRequerida":10,"unidadMedida":"unidades"}
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/lotes/" + loteId + "/ofertas").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cantidadOfrecida\":5}"))
                .andExpect(status().isForbidden());
    }

    private Cookie iniciarSesion() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("SERVICIO_BONITA"))
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("access_token");
        assertThat(cookie).isNotNull();
        return cookie;
    }
}
