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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "jwt.secret=test-signing-key-for-integration-tests-32bytes",
        "jwt.expiration=86400000"
})
@AutoConfigureMockMvc
class OfertasConsolidadasIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtService jwtService;

    private String tokenCentro;
    private String tokenOperador;
    private Long municipioId;
    private Long primeraOngId;
    private Long segundaOngId;

    @BeforeEach
    void preparar() {
        jdbcTemplate.update("INSERT INTO municipios (nombre, created_at, updated_at) "
                + "VALUES ('Municipio consolidado', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        municipioId = ultimoId("municipios");
        primeraOngId = crearOng("ONG Norte");
        segundaOngId = crearOng("ONG Sur");
        jdbcTemplate.update("UPDATE ongs SET cuit = ? WHERE id = ?", "30500000001", primeraOngId);
        jdbcTemplate.update("UPDATE ongs SET cuit = ? WHERE id = ?", "30500000002", segundaOngId);

        crearUsuario("centro-consolidado@test.local", Rol.CENTRO_COORDINADOR);
        crearUsuario("operador-consolidado@test.local", Rol.OPERADOR_MUNICIPAL);
        tokenCentro = token("centro-consolidado@test.local", Rol.CENTRO_COORDINADOR);
        tokenOperador = token("operador-consolidado@test.local", Rol.OPERADOR_MUNICIPAL);
    }

    @AfterEach
    void limpiar() {
        jdbcTemplate.update("DELETE FROM ofertas");
        jdbcTemplate.update("DELETE FROM lotes");
        jdbcTemplate.update("DELETE FROM emergencias");
        jdbcTemplate.update("DELETE FROM usuarios WHERE email IN (?, ?)",
                "centro-consolidado@test.local", "operador-consolidado@test.local");
        jdbcTemplate.update("DELETE FROM ongs WHERE nombre IN (?, ?)", "ONG Norte", "ONG Sur");
        jdbcTemplate.update("DELETE FROM municipios WHERE nombre = ?", "Municipio consolidado");
    }

    @Test
    void devuelve404SiLaEmergenciaNoExiste() throws Exception {
        mockMvc.perform(get(ruta(Long.MAX_VALUE)).cookie(new Cookie("access_token", tokenCentro)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value(ruta(Long.MAX_VALUE)));
    }

    @Test
    void incluyeMultiplesLotesOfertasYUnLoteSinOfertas() throws Exception {
        long emergenciaId = crearEmergencia();
        long primerLote = crearLote(emergenciaId, "ALIMENTOS", "Raciones", 100, "raciones");
        long segundoLote = crearLote(emergenciaId, "AGUA_POTABLE", "Agua", 50, "litros");
        long tercerLote = crearLote(emergenciaId, "MEDICAMENTOS", "Botiquines", 20, "unidades");
        long primeraOferta = crearOferta(primerLote, primeraOngId, 30, "Entrega hoy", "PENDIENTE");
        long segundaOferta = crearOferta(primerLote, segundaOngId, 25, null, "EVALUADA");
        crearOferta(segundoLote, primeraOngId, 10, "En depósito", "RECHAZADA");

        mockMvc.perform(get(ruta(emergenciaId)).cookie(new Cookie("access_token", tokenCentro)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(primerLote))
                .andExpect(jsonPath("$[0].tipoRecurso").value("ALIMENTOS"))
                .andExpect(jsonPath("$[0].descripcion").value("Raciones"))
                .andExpect(jsonPath("$[0].cantidadRequerida").value(100))
                .andExpect(jsonPath("$[0].unidadMedida").value("raciones"))
                .andExpect(jsonPath("$[0].estado").value("PUBLICADO"))
                .andExpect(jsonPath("$[0].totalOfrecido").value(55))
                .andExpect(jsonPath("$[0].ofertas.length()").value(2))
                .andExpect(jsonPath("$[0].ofertas[0].id").value(primeraOferta))
                .andExpect(jsonPath("$[0].ofertas[0].ong.id").value(primeraOngId))
                .andExpect(jsonPath("$[0].ofertas[0].ong.nombre").value("ONG Norte"))
                .andExpect(jsonPath("$[0].ofertas[0].ong.cuit").value("30500000001"))
                .andExpect(jsonPath("$[0].ofertas[0].cantidadOfrecida").value(30))
                .andExpect(jsonPath("$[0].ofertas[0].observaciones").value("Entrega hoy"))
                .andExpect(jsonPath("$[0].ofertas[0].estado").value("PENDIENTE"))
                .andExpect(jsonPath("$[0].ofertas[1].id").value(segundaOferta))
                .andExpect(jsonPath("$[0].ofertas[1].ong.nombre").value("ONG Sur"))
                .andExpect(jsonPath("$[0].ofertas[1].ong.cuit").value("30500000002"))
                .andExpect(jsonPath("$[0].ofertas[1].estado").value("EVALUADA"))
                .andExpect(jsonPath("$[1].id").value(segundoLote))
                .andExpect(jsonPath("$[1].totalOfrecido").value(10))
                .andExpect(jsonPath("$[1].ofertas[0].estado").value("RECHAZADA"))
                .andExpect(jsonPath("$[2].id").value(tercerLote))
                .andExpect(jsonPath("$[2].totalOfrecido").value(0))
                .andExpect(jsonPath("$[2].ofertas.length()").value(0));
    }

    @Test
    void unaOngAnteriorSinCuitNoSeIdentificaPorNombreNiPorId() throws Exception {
        jdbcTemplate.update("UPDATE ongs SET cuit = NULL WHERE id = ?", primeraOngId);
        long emergenciaId = crearEmergencia();
        long loteId = crearLote(emergenciaId, "ALIMENTOS", "Alimentos", 10, "kg");
        crearOferta(loteId, primeraOngId, 5, null, "PENDIENTE");

        mockMvc.perform(get(ruta(emergenciaId)).cookie(new Cookie("access_token", tokenCentro)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ofertas[0].ong.cuit").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void devuelveListaVaciaSiLaEmergenciaNoTieneLotes() throws Exception {
        long emergenciaId = crearEmergencia();
        mockMvc.perform(get(ruta(emergenciaId)).cookie(new Cookie("access_token", tokenCentro)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void exigeJwtYPermisoDeConsultaDeEmergencias() throws Exception {
        long emergenciaId = crearEmergencia();
        mockMvc.perform(get(ruta(emergenciaId))).andExpect(status().isUnauthorized());
        mockMvc.perform(get(ruta(emergenciaId)).cookie(new Cookie("access_token", tokenOperador)))
                .andExpect(status().isForbidden());
    }

    private static String ruta(long emergenciaId) {
        return "/api/emergencias/" + emergenciaId + "/ofertas-consolidadas";
    }

    private long crearEmergencia() {
        jdbcTemplate.update("INSERT INTO emergencias "
                        + "(tipo_desastre, nivel_gravedad, zona_afectada, descripcion, municipio_id, estado, created_at, updated_at) "
                        + "VALUES ('INUNDACION', 'ALTO', 'Zona de prueba', 'Emergencia de prueba', ?, 'REGISTRADA', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                municipioId);
        return ultimoId("emergencias");
    }

    private long crearLote(long emergenciaId, String tipo, String descripcion, int cantidad, String unidad) {
        jdbcTemplate.update("INSERT INTO lotes "
                        + "(emergencia_id, tipo_recurso, descripcion, cantidad_requerida, unidad_medida, estado, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, 'PUBLICADO', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                emergenciaId, tipo, descripcion, cantidad, unidad);
        return ultimoId("lotes");
    }

    private long crearOferta(long loteId, long ongId, int cantidad, String observaciones, String estado) {
        jdbcTemplate.update("INSERT INTO ofertas "
                        + "(lote_id, ong_id, cantidad_ofrecida, observaciones, estado, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                loteId, ongId, cantidad, observaciones, estado);
        return ultimoId("ofertas");
    }

    private long crearOng(String nombre) {
        jdbcTemplate.update("INSERT INTO ongs (nombre, created_at, updated_at) "
                + "VALUES (?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", nombre);
        return ultimoId("ongs");
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
