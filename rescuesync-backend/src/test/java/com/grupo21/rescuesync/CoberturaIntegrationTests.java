package com.grupo21.rescuesync;

import com.grupo21.rescuesync.model.Rol;
import com.grupo21.rescuesync.model.Usuario;
import com.grupo21.rescuesync.security.JwtService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "jwt.secret=test-signing-key-for-integration-tests-32bytes",
        "jwt.expiration=86400000"
})
@AutoConfigureMockMvc
class CoberturaIntegrationTests {

    private static final String EMAIL_CENTRO = "centro-cobertura@test.local";
    private static final String EMAIL_ONG = "ong-cobertura@test.local";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtService jwtService;

    private String tokenCentro;
    private String tokenOng;
    private Long municipioId;
    private Long ongId;

    @BeforeEach
    void preparar() {
        jdbcTemplate.update("INSERT INTO municipios (nombre, created_at, updated_at) "
                + "VALUES ('Municipio cobertura', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        municipioId = ultimoId("municipios");
        jdbcTemplate.update("INSERT INTO ongs (nombre, created_at, updated_at) "
                + "VALUES ('ONG cobertura', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        ongId = ultimoId("ongs");

        crearUsuario(EMAIL_CENTRO, Rol.CENTRO_COORDINADOR);
        crearUsuario(EMAIL_ONG, Rol.REPRESENTANTE_ONG);
        tokenCentro = token(EMAIL_CENTRO, Rol.CENTRO_COORDINADOR);
        tokenOng = token(EMAIL_ONG, Rol.REPRESENTANTE_ONG);
    }

    @AfterEach
    void limpiar() {
        jdbcTemplate.update("DELETE FROM historial_estados");
        jdbcTemplate.update("DELETE FROM ofertas");
        jdbcTemplate.update("DELETE FROM lotes");
        jdbcTemplate.update("DELETE FROM emergencias");
        jdbcTemplate.update("DELETE FROM usuarios WHERE email IN (?, ?)", EMAIL_CENTRO, EMAIL_ONG);
        jdbcTemplate.update("DELETE FROM ongs WHERE nombre LIKE 'ONG cobertura%'");
        jdbcTemplate.update("DELETE FROM municipios WHERE nombre = ?", "Municipio cobertura");
    }

    @Test
    void completaMarcaLosLotesCubiertosYLaEmergenciaEvaluada() throws Exception {
        long emergenciaId = crearEmergencia("CONVOCATORIA_PUBLICADA");
        long primerLote = crearLote(emergenciaId, 100);
        long segundoLote = crearLote(emergenciaId, 50);
        crearOferta(primerLote, 110);
        crearOferta(segundoLote, 30);
        crearOferta(segundoLote, 20, crearOng("ONG cobertura 2"));

        decidir(emergenciaId, "COMPLETA", tokenCentro)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("COBERTURA_EVALUADA"));

        assertEquals("CUBIERTO", estadoLote(primerLote));
        assertEquals("CUBIERTO", estadoLote(segundoLote));
        assertEquals(3, cambiosRegistrados(emergenciaId));
    }

    @Test
    void completaSeRechazaSiHayUnLoteSinCubrir() throws Exception {
        long emergenciaId = crearEmergencia("CONVOCATORIA_PUBLICADA");
        long primerLote = crearLote(emergenciaId, 100);
        long segundoLote = crearLote(emergenciaId, 50);
        crearOferta(primerLote, 200);
        crearOferta(segundoLote, 10);

        decidir(emergenciaId, "COMPLETA", tokenCentro).andExpect(status().isConflict());

        assertEquals("CONVOCATORIA_PUBLICADA", estadoEmergencia(emergenciaId));
        assertEquals("PUBLICADO", estadoLote(primerLote));
    }

    @Test
    void parcialDistingueLotesCubiertosDeLosQueNo() throws Exception {
        long emergenciaId = crearEmergencia("CONVOCATORIA_PUBLICADA");
        long cubierto = crearLote(emergenciaId, 100);
        long incompleto = crearLote(emergenciaId, 50);
        long sinOfertas = crearLote(emergenciaId, 20);
        crearOferta(cubierto, 100);
        crearOferta(incompleto, 10);

        decidir(emergenciaId, "PARCIAL", tokenCentro)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("COBERTURA_PARCIAL"));

        assertEquals("CUBIERTO", estadoLote(cubierto));
        assertEquals("PARCIALMENTE_CUBIERTO", estadoLote(incompleto));
        assertEquals("PARCIALMENTE_CUBIERTO", estadoLote(sinOfertas));
    }

    @Test
    void parcialSeRechazaSiTodosLosLotesEstanCubiertos() throws Exception {
        long emergenciaId = crearEmergencia("CONVOCATORIA_PUBLICADA");
        crearOferta(crearLote(emergenciaId, 100), 100);

        decidir(emergenciaId, "PARCIAL", tokenCentro).andExpect(status().isConflict());

        assertEquals("CONVOCATORIA_PUBLICADA", estadoEmergencia(emergenciaId));
    }

    @Test
    void sinCoberturaReabreLaConvocatoriaSinCambiarEstados() throws Exception {
        long emergenciaId = crearEmergencia("CONVOCATORIA_PUBLICADA");
        long lote = crearLote(emergenciaId, 100);

        decidir(emergenciaId, "SIN_COBERTURA", tokenCentro)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONVOCATORIA_PUBLICADA"));

        assertEquals("PUBLICADO", estadoLote(lote));
        assertEquals(0, cambiosRegistrados(emergenciaId));
        // Puede volver a decidirse tras reabrir la convocatoria.
        decidir(emergenciaId, "SIN_COBERTURA", tokenCentro).andExpect(status().isOk());
    }

    @Test
    void soloSeEvaluaConLaConvocatoriaPublicada() throws Exception {
        long emergenciaId = crearEmergencia("EN_REVISION");
        crearLote(emergenciaId, 100);

        decidir(emergenciaId, "SIN_COBERTURA", tokenCentro).andExpect(status().isConflict());
    }

    @Test
    void devuelve404ConEmergenciaInexistenteYValidaElCuerpo() throws Exception {
        decidir(Long.MAX_VALUE, "COMPLETA", tokenCentro).andExpect(status().isNotFound());

        long emergenciaId = crearEmergencia("CONVOCATORIA_PUBLICADA");
        decidir(emergenciaId, "NO_EXISTE", tokenCentro).andExpect(status().isBadRequest());
        mockMvc.perform(post(ruta(emergenciaId))
                        .cookie(new Cookie("access_token", tokenCentro))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exigeJwtYPermisoDeEvaluarCobertura() throws Exception {
        long emergenciaId = crearEmergencia("CONVOCATORIA_PUBLICADA");
        crearLote(emergenciaId, 100);

        mockMvc.perform(post(ruta(emergenciaId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"SIN_COBERTURA\"}"))
                .andExpect(status().isUnauthorized());
        decidir(emergenciaId, "SIN_COBERTURA", tokenOng).andExpect(status().isForbidden());
    }

    private ResultActions decidir(long emergenciaId, String decision, String token) throws Exception {
        return mockMvc.perform(post(ruta(emergenciaId))
                .cookie(new Cookie("access_token", token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"" + decision + "\"}"));
    }

    private static String ruta(long emergenciaId) {
        return "/api/emergencias/" + emergenciaId + "/cobertura";
    }

    private long crearEmergencia(String estado) {
        jdbcTemplate.update("INSERT INTO emergencias "
                        + "(tipo_desastre, nivel_gravedad, zona_afectada, descripcion, municipio_id, estado, created_at, updated_at) "
                        + "VALUES ('INUNDACION', 'ALTO', 'Zona de prueba', 'Emergencia de prueba', ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                municipioId, estado);
        return ultimoId("emergencias");
    }

    private long crearLote(long emergenciaId, int cantidad) {
        jdbcTemplate.update("INSERT INTO lotes "
                        + "(emergencia_id, tipo_recurso, descripcion, cantidad_requerida, unidad_medida, estado, created_at, updated_at) "
                        + "VALUES (?, 'ALIMENTOS', 'Raciones', ?, 'raciones', 'PUBLICADO', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                emergenciaId, cantidad);
        return ultimoId("lotes");
    }

    private void crearOferta(long loteId, int cantidad) {
        crearOferta(loteId, cantidad, ongId);
    }

    private void crearOferta(long loteId, int cantidad, long ong) {
        jdbcTemplate.update("INSERT INTO ofertas "
                        + "(lote_id, ong_id, cantidad_ofrecida, estado, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'PENDIENTE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                loteId, ong, cantidad);
    }

    private long crearOng(String nombre) {
        jdbcTemplate.update("INSERT INTO ongs (nombre, created_at, updated_at) "
                + "VALUES (?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", nombre);
        return ultimoId("ongs");
    }

    private String estadoLote(long loteId) {
        return jdbcTemplate.queryForObject("SELECT estado FROM lotes WHERE id = ?", String.class, loteId);
    }

    private String estadoEmergencia(long emergenciaId) {
        return jdbcTemplate.queryForObject("SELECT estado FROM emergencias WHERE id = ?", String.class, emergenciaId);
    }

    private int cambiosRegistrados(long emergenciaId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM historial_estados WHERE emergencia_id = ?", Integer.class, emergenciaId);
    }

    private void crearUsuario(String email, Rol rol) {
        jdbcTemplate.update("INSERT INTO usuarios (email, password_hash, rol, created_at, updated_at) "
                + "VALUES (?, 'test', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", email, rol.name());
    }

    private String token(String email, Rol rol) {
        Usuario usuario = new Usuario();
        usuario.setEmail(email);
        usuario.setRol(rol);
        return jwtService.generarToken(usuario);
    }

    private long ultimoId(String tabla) {
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM " + tabla, Long.class);
    }
}
