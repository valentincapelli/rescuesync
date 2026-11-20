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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Regresiones de aislamiento municipal, pertenencia y recuperación de sesión (E4-09). */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auditoria_e4_09;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "bonita.seleccion.recuperacion-habilitada=false",
        "bonita.despliegues.sincronizacion-habilitada=false"
})
@AutoConfigureMockMvc
@Transactional
class PerfilesPermisosIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;
    @Autowired PasswordEncoder passwords;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @MockitoBean BonitaClient bonita;

    @BeforeEach
    void preparar() {
        jdbc.update("INSERT INTO municipios(id,nombre,created_at,updated_at) VALUES (91001,'Municipio A',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),(91002,'Municipio B',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO ongs(id,nombre,created_at,updated_at) VALUES (91001,'ONG A',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),(91002,'ONG B',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        for (Rol rol : Rol.values()) {
            jdbc.update("INSERT INTO usuarios(email,password_hash,rol,municipio_id,ong_id,created_at,updated_at) VALUES (?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                    rol.name()+"@audit.local", passwords.encode("audit-password"), rol.name(),
                    rol==Rol.OPERADOR_MUNICIPAL ? 91001 : null,
                    rol==Rol.REPRESENTANTE_ONG ? 91001 : null);
        }
        emergencia(91001,91001);
        emergencia(91002,91002);
        lote(91001,91001);
        lote(91002,91002);
        jdbc.update("INSERT INTO ofertas(id,lote_id,ong_id,cantidad_ofrecida,estado,created_at,updated_at) VALUES (91001,91001,91001,100,'PENDIENTE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),(91002,91002,91002,100,'PENDIENTE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
    }

    private void emergencia(long id,long municipio) {
        jdbc.update("INSERT INTO emergencias(id,municipio_id,tipo_desastre,nivel_gravedad,zona_afectada,descripcion,estado,bonita_case_id,fecha_cierre_convocatoria,created_at,updated_at) VALUES (?,?,'INUNDACION','ALTO','Zona auditada','Descripción de auditoría','CONVOCATORIA_PUBLICADA',700,DATEADD('DAY',1,CURRENT_TIMESTAMP),CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",id,municipio);
    }

    private void lote(long id,long emergencia) {
        jdbc.update("INSERT INTO lotes(id,emergencia_id,tipo_recurso,descripcion,cantidad_requerida,unidad_medida,estado,created_at,updated_at) VALUES (?,?,'ALIMENTOS','Lote de auditoría',100,'raciones','PUBLICADO',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",id,emergencia);
    }

    private Cookie cookie(Rol rol) {
        Usuario usuario=new Usuario();
        usuario.setEmail(rol.name()+"@audit.local");
        usuario.setRol(rol);
        return new Cookie("access_token",jwt.generarToken(usuario));
    }

    @Test
    void municipioNoDebeLeerLotesDeOtroMunicipio() throws Exception {
        mvc.perform(get("/api/emergencias/91001/lotes").cookie(cookie(Rol.OPERADOR_MUNICIPAL)))
                .andExpect(status().isOk());
        var result=mvc.perform(get("/api/emergencias/91002/lotes").cookie(cookie(Rol.OPERADOR_MUNICIPAL)))
                .andReturn().getResponse();
        assertThat(result.getStatus()).as("Acceso a lotes del otro municipio").isIn(403,404);
    }

    @Test
    void loginDebeRecuperarSesionConCookieInvalida() throws Exception {
        String payload="{\"email\":\"OPERADOR_MUNICIPAL@audit.local\",\"password\":\"audit-password\"}";
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").cookie(new Cookie("access_token","token-invalido"))
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk());
    }

    @Test
    void logoutDebeBorrarCookieInvalida() throws Exception {
        mvc.perform(post("/api/auth/logout").cookie(new Cookie("access_token","token-invalido")))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie",org.hamcrest.Matchers.containsString("Max-Age=0")));
        mvc.perform(get("/api/emergencias/91001/lotes").cookie(new Cookie("access_token", "token-invalido")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ongConsultaYEditaSoloSusOfertasMientrasLaConvocatoriaEsteAbierta() throws Exception {
        mvc.perform(get("/api/ofertas/mias").cookie(cookie(Rol.REPRESENTANTE_ONG)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].oferta.id").value(91001))
                .andExpect(jsonPath("$[0].editable").value(true));
        mvc.perform(get("/api/ofertas/mias").cookie(cookie(Rol.OPERADOR_MUNICIPAL)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/lotes/91001/ofertas/91001").cookie(cookie(Rol.REPRESENTANTE_ONG))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cantidadOfrecida\":80}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cantidadOfrecida").value(80));
        entityManager.flush();
        jdbc.update("UPDATE emergencias SET fecha_cierre_convocatoria=DATEADD('DAY',-1,CURRENT_TIMESTAMP) WHERE id=91001");
        entityManager.clear();
        mvc.perform(get("/api/ofertas/mias").cookie(cookie(Rol.REPRESENTANTE_ONG)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].editable").value(false));
        mvc.perform(put("/api/lotes/91001/ofertas/91001").cookie(cookie(Rol.REPRESENTANTE_ONG))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cantidadOfrecida\":1}"))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT cantidad_ofrecida FROM ofertas WHERE id=91001", Integer.class)).isEqualTo(80);
    }

    @Test
    void ofertaEvaluadaSinValidacionNoDebeSerAdjudicable() throws Exception {
        jdbc.update("UPDATE emergencias SET estado='COBERTURA_EVALUADA' WHERE id=91001");
        jdbc.update("UPDATE lotes SET estado='CUBIERTO' WHERE id=91001");
        jdbc.update("UPDATE ofertas SET estado='EVALUADA' WHERE id=91001");
        mvc.perform(put("/api/ofertas/seleccion").cookie(cookie(Rol.OPERADOR_MUNICIPAL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emergenciaId\":91001,\"ofertaIds\":[91001]}"))
                .andExpect(status().isConflict());
    }

    @Test
    void editarDebeValidarRelacionLoteOfertaDeLaRuta() throws Exception {
        mvc.perform(put("/api/lotes/91002/ofertas/91001").cookie(cookie(Rol.REPRESENTANTE_ONG))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cantidadOfrecida\":80,\"observaciones\":\"Edición con lote ajeno\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void otraOngNoPuedeEditarOfertaYNoCambiaLaCantidad() throws Exception {
        mvc.perform(put("/api/lotes/91002/ofertas/91002").cookie(cookie(Rol.REPRESENTANTE_ONG))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cantidadOfrecida\":1}"))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT cantidad_ofrecida FROM ofertas WHERE id=91002",Integer.class)).isEqualTo(100);
    }

    @Test
    void permisosDeCoberturaSeAplicanEnApi() throws Exception {
        jdbc.update("UPDATE emergencias SET fecha_cierre_convocatoria=DATEADD('DAY',-1,CURRENT_TIMESTAMP) WHERE id=91001");
        when(bonita.buscarCaso("700")).thenReturn(java.util.Optional.of(
                new com.grupo21.rescuesync.client.BonitaCasoInfo("700", null, "started", null, null)));
        when(bonita.leerVariable("700", "emergenciaId", false)).thenReturn(java.util.Optional.of("91001"));
        when(bonita.buscarTareasActivas("700", "Evaluar cobertura de lotes")).thenReturn(java.util.List.of("c1"));
        for(Rol rol:Rol.values()) {
            if(rol==Rol.CENTRO_COORDINADOR) continue;
            mvc.perform(post("/api/emergencias/91001/cobertura").cookie(cookie(rol))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"COMPLETA\"}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/emergencias/91001/cobertura")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"COMPLETA\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/emergencias/91001/cobertura").cookie(cookie(Rol.CENTRO_COORDINADOR))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"COMPLETA\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void otraOngNoPuedeLeerNiMarcarNotificacion() throws Exception {
        long usuario=jdbc.queryForObject("SELECT id FROM usuarios WHERE rol='REPRESENTANTE_ONG'",Long.class);
        jdbc.update("INSERT INTO notificaciones(id,ong_id,titulo,mensaje,tipo,created_at,updated_at) VALUES (91001,91001,'Propia','Aviso propio','OFERTA_SELECCIONADA',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),(91002,91002,'Ajena','Aviso ajeno','OFERTA_SELECCIONADA',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO notificaciones_usuarios(notificacion_id,usuario_id,created_at,updated_at) VALUES (91001,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",usuario);
        mvc.perform(get("/api/notificaciones").cookie(cookie(Rol.REPRESENTANTE_ONG)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(91001));
        mvc.perform(patch("/api/notificaciones/91002/leida").cookie(cookie(Rol.REPRESENTANTE_ONG)))
                .andExpect(status().isNotFound());
    }
}
