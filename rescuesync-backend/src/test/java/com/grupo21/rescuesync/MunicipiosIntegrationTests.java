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
        "jwt.expiration=86400000",
        "bonita.base-url=http://localhost:1/bonita"
})
@AutoConfigureMockMvc
class MunicipiosIntegrationTests {

    private static final String DIRECTIVO = "directivo-municipios@test.local";
    private static final String OPERADOR = "operador-municipios@test.local";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtService jwtService;

    @BeforeEach
    void preparar() {
        jdbcTemplate.update("DELETE FROM municipios WHERE nombre LIKE 'ZZ Muni %'");
        // Se insertan desordenados para verificar el orden por nombre.
        for (String nombre : new String[] {"ZZ Muni Berisso", "ZZ Muni Ensenada", "ZZ Muni Arana"}) {
            jdbcTemplate.update("INSERT INTO municipios (nombre, created_at, updated_at) "
                    + "VALUES (?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", nombre);
        }
        crearUsuario(DIRECTIVO, Rol.DIRECTIVO);
        crearUsuario(OPERADOR, Rol.OPERADOR_MUNICIPAL);
    }

    @AfterEach
    void limpiar() {
        jdbcTemplate.update("DELETE FROM usuarios WHERE email IN (?, ?)", DIRECTIVO, OPERADOR);
        jdbcTemplate.update("DELETE FROM municipios WHERE nombre LIKE 'ZZ Muni %'");
    }

    @Test
    void listaIdYNombreOrdenadosPorNombre() throws Exception {
        mockMvc.perform(get("/api/municipios").cookie(cookie(DIRECTIVO, Rol.DIRECTIVO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == 'ZZ Muni Arana')].id").isNotEmpty())
                .andExpect(jsonPath("$[-3].nombre").value("ZZ Muni Arana"))
                .andExpect(jsonPath("$[-2].nombre").value("ZZ Muni Berisso"))
                .andExpect(jsonPath("$[-1].nombre").value("ZZ Muni Ensenada"));
    }

    @Test
    void exigeSesionYPermisoDeConsulta() throws Exception {
        mockMvc.perform(get("/api/municipios")).andExpect(status().isUnauthorized());
        // El operador municipal no consulta emergencias ni indicadores.
        mockMvc.perform(get("/api/municipios").cookie(cookie(OPERADOR, Rol.OPERADOR_MUNICIPAL)))
                .andExpect(status().isForbidden());
    }

    private void crearUsuario(String email, Rol rol) {
        jdbcTemplate.update("INSERT INTO usuarios (email, password_hash, rol, created_at, updated_at) "
                + "VALUES (?, 'test', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", email, rol.name());
    }

    private Cookie cookie(String email, Rol rol) {
        Usuario usuario = new Usuario();
        usuario.setEmail(email);
        usuario.setRol(rol);
        return new Cookie("access_token", jwtService.generarToken(usuario));
    }
}
