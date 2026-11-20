package com.grupo21.rescuesync;

import com.grupo21.rescuesync.client.BonitaClient;
import com.grupo21.rescuesync.model.Rol;
import com.grupo21.rescuesync.model.Usuario;
import com.grupo21.rescuesync.security.JwtService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Gestión de versiones de ofertas: cada edición deja trazabilidad en el backend. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:versiones_oferta;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "bonita.seleccion.recuperacion-habilitada=false",
        "bonita.despliegues.sincronizacion-habilitada=false"
})
@AutoConfigureMockMvc
@Transactional
class VersionesOfertaIntegrationTests {
    private static final String ONG_A = "ong-a@versiones.local";
    private static final String ONG_B = "ong-b@versiones.local";
    private static final String COORDINADOR = "coordinador@versiones.local";
    private static final String MUNICIPIO = "municipio@versiones.local";
    private static final String EDITAR = "/api/lotes/92001/ofertas/92001";
    private static final String VERSIONES = EDITAR + "/versiones";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;
    @MockitoBean BonitaClient bonita;

    @BeforeEach
    void preparar() {
        jdbc.update("INSERT INTO municipios(id,nombre,created_at,updated_at) VALUES (92001,'Municipio V',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO ongs(id,nombre,created_at,updated_at) VALUES (92001,'ONG A',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),(92002,'ONG B',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        usuario(ONG_A, Rol.REPRESENTANTE_ONG, null, 92001L);
        usuario(ONG_B, Rol.REPRESENTANTE_ONG, null, 92002L);
        usuario(COORDINADOR, Rol.CENTRO_COORDINADOR, null, null);
        usuario(MUNICIPIO, Rol.OPERADOR_MUNICIPAL, 92001L, null);
        jdbc.update("INSERT INTO emergencias(id,municipio_id,tipo_desastre,nivel_gravedad,zona_afectada,descripcion,estado,bonita_case_id,fecha_cierre_convocatoria,created_at,updated_at) VALUES (92001,92001,'INUNDACION','ALTO','Zona','Descripción','CONVOCATORIA_PUBLICADA',800,DATEADD('DAY',1,CURRENT_TIMESTAMP),CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO lotes(id,emergencia_id,tipo_recurso,descripcion,cantidad_requerida,unidad_medida,estado,created_at,updated_at) VALUES (92001,92001,'ALIMENTOS','Lote',100,'raciones','PUBLICADO',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO ofertas(id,lote_id,ong_id,cantidad_ofrecida,observaciones,estado,created_at,updated_at) VALUES (92001,92001,92001,100,'Original','PENDIENTE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
    }

    private void usuario(String email, Rol rol, Long municipio, Long ong) {
        jdbc.update("INSERT INTO usuarios(email,password_hash,rol,municipio_id,ong_id,created_at,updated_at) VALUES (?,'x',?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                email, rol.name(), municipio, ong);
    }

    private Cookie cookie(String email, Rol rol) {
        Usuario usuario = new Usuario();
        usuario.setEmail(email);
        usuario.setRol(rol);
        return new Cookie("access_token", jwt.generarToken(usuario));
    }

    private void editar(String json) throws Exception {
        mvc.perform(put(EDITAR).cookie(cookie(ONG_A, Rol.REPRESENTANTE_ONG))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
    }

    private int revisiones() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM revisiones_oferta WHERE oferta_id=92001", Integer.class);
    }

    @Test
    void cadaEdicionAgregaUnaVersionConAutorYValoresAnteriores() throws Exception {
        editar("{\"cantidadOfrecida\":80,\"observaciones\":\"Menos stock\"}");
        editar("{\"cantidadOfrecida\":90,\"observaciones\":\"Menos stock\"}");

        assertThat(revisiones()).isEqualTo(2);
        mvc.perform(get(VERSIONES).cookie(cookie(ONG_A, Rol.REPRESENTANTE_ONG)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].numero").value(1))
                .andExpect(jsonPath("$[0].cantidadOfrecida").value(100))
                .andExpect(jsonPath("$[0].observaciones").value("Original"))
                .andExpect(jsonPath("$[0].autorEmail").doesNotExist())
                .andExpect(jsonPath("$[1].cantidadOfrecida").value(80))
                .andExpect(jsonPath("$[1].cantidadAnterior").value(100))
                .andExpect(jsonPath("$[1].observacionesAnteriores").value("Original"))
                .andExpect(jsonPath("$[1].autorEmail").value(ONG_A))
                .andExpect(jsonPath("$[1].fecha").exists())
                .andExpect(jsonPath("$[2].numero").value(3))
                .andExpect(jsonPath("$[2].cantidadOfrecida").value(90))
                .andExpect(jsonPath("$[2].cantidadAnterior").value(80));
    }

    @Test
    void ofertaSinEdicionesTieneSoloLaVersionDeAlta() throws Exception {
        mvc.perform(get(VERSIONES).cookie(cookie(ONG_A, Rol.REPRESENTANTE_ONG)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].cantidadOfrecida").value(100));
    }

    @Test
    void guardarSinCambiosNoCreaVersion() throws Exception {
        editar("{\"cantidadOfrecida\":100,\"observaciones\":\"Original\"}");
        assertThat(revisiones()).isZero();
    }

    @Test
    void edicionRechazadaFueraDeTerminoNoCreaVersion() throws Exception {
        jdbc.update("UPDATE emergencias SET fecha_cierre_convocatoria=DATEADD('DAY',-1,CURRENT_TIMESTAMP) WHERE id=92001");
        mvc.perform(put(EDITAR).cookie(cookie(ONG_A, Rol.REPRESENTANTE_ONG))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cantidadOfrecida\":1}"))
                .andExpect(status().isConflict());
        assertThat(revisiones()).isZero();
    }

    @Test
    void soloLaOngDuenaYElCoordinadorVenLasVersiones() throws Exception {
        mvc.perform(get(VERSIONES).cookie(cookie(ONG_B, Rol.REPRESENTANTE_ONG)))
                .andExpect(status().isConflict());
        mvc.perform(get(VERSIONES).cookie(cookie(COORDINADOR, Rol.CENTRO_COORDINADOR)))
                .andExpect(status().isOk());
        mvc.perform(get(VERSIONES).cookie(cookie(MUNICIPIO, Rol.OPERADOR_MUNICIPAL)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/lotes/99999/ofertas/92001/versiones").cookie(cookie(ONG_A, Rol.REPRESENTANTE_ONG)))
                .andExpect(status().isNotFound());
    }
}
