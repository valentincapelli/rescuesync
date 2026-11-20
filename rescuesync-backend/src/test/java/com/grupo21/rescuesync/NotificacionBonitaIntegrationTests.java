package com.grupo21.rescuesync;

import com.grupo21.rescuesync.client.BonitaActividadArchivada;
import com.grupo21.rescuesync.client.BonitaCasoInfo;
import com.grupo21.rescuesync.client.BonitaClient;
import com.grupo21.rescuesync.exception.BonitaIntegrationException;
import com.grupo21.rescuesync.model.Rol;
import com.grupo21.rescuesync.model.Usuario;
import com.grupo21.rescuesync.security.JwtService;
import com.grupo21.rescuesync.service.NotificacionBonitaCoordinador;
import com.grupo21.rescuesync.service.SeleccionBonitaRegistro;
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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Transacciones reales: verifica la persistencia de los listeners AFTER_COMMIT. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:notificaciones_e4_09;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "bonita.seleccion.recuperacion-habilitada=false",
        "bonita.despliegues.sincronizacion-habilitada=false"
})
@AutoConfigureMockMvc
class NotificacionBonitaIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;
    @Autowired NotificacionBonitaCoordinador coordinador;
    @Autowired SeleccionBonitaRegistro selecciones;
    @MockitoBean BonitaClient bonita;

    private final AtomicLong activa = new AtomicLong();
    private final AtomicReference<String> tarea = new AtomicReference<>();
    private final List<BonitaActividadArchivada> archivo = new CopyOnWriteArrayList<>();

    @BeforeEach
    void preparar() {
        for (String tabla : List.of("notificaciones_usuarios", "notificaciones", "intenciones_seleccion_bonita",
                "historial_estados", "despliegues", "ofertas", "lotes", "emergencias", "usuarios", "ongs", "municipios")) {
            jdbc.update("DELETE FROM " + tabla);
        }
        jdbc.update("INSERT INTO municipios(id,nombre,created_at,updated_at) VALUES (92001,'Municipio',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO ongs(id,nombre,created_at,updated_at) VALUES (92001,'ONG A',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),(92002,'ONG B',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO usuarios(id,email,password_hash,rol,municipio_id,ong_id,created_at,updated_at) VALUES (92001,'municipio@notificaciones.test','test','OPERADOR_MUNICIPAL',92001,NULL,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),(92002,'a@notificaciones.test','test','REPRESENTANTE_ONG',NULL,92001,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),(92003,'b@notificaciones.test','test','REPRESENTANTE_ONG',NULL,92002,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),(92004,'a2@notificaciones.test','test','REPRESENTANTE_ONG',NULL,92001,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO emergencias(id,municipio_id,tipo_desastre,nivel_gravedad,zona_afectada,descripcion,estado,bonita_case_id,created_at,updated_at) VALUES (92001,92001,'INUNDACION','ALTO','Zona','Emergencia','COBERTURA_EVALUADA',700,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO lotes(id,emergencia_id,tipo_recurso,descripcion,cantidad_requerida,unidad_medida,estado,created_at,updated_at) VALUES (92001,92001,'ALIMENTOS','Alimentos',100,'raciones','CUBIERTO',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO ofertas(id,lote_id,ong_id,cantidad_ofrecida,estado,validacion_clasificacion,validacion_evaluada_en,created_at,updated_at) VALUES (92001,92001,92001,50,'EVALUADA','PRINCIPAL',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),(92002,92001,92002,50,'EVALUADA','PRINCIPAL',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        activa.set(0);
        tarea.set("Seleccionar oferta");
        archivo.clear();
        when(bonita.buscarCaso("700")).thenReturn(Optional.of(new BonitaCasoInfo("700", null, "started", null, null)));
        when(bonita.leerVariable("700", "emergenciaId", false)).thenReturn(Optional.of("92001"));
        when(bonita.leerVariable("700", "ofertaSeleccionadaId", false)).thenAnswer(i -> Optional.of(Long.toString(activa.get())));
        when(bonita.listarActividadesArchivadas("700")).thenAnswer(i -> List.copyOf(archivo));
        when(bonita.buscarTareasActivas(eq("700"), anyString())).thenAnswer(i ->
                i.getArgument(1).equals(tarea.get()) ? List.of(taskId()) : List.of());
        doAnswer(i -> {
            activa.set(92001);
            tarea.set("Notificar a ONG");
            return null;
        }).when(bonita).completarTareaPorId(anyString(), anyMap());
        doAnswer(i -> {
            completar(i.getArgument(0));
            return null;
        }).when(bonita).completarTareaPorId(anyString());
    }

    @Test
    void adjudicacionEnviaAvisoYRecepcionPropiaContinuaUnaSolaVez() throws Exception {
        adjudicar();
        long id = notificacion(92001);
        assertThat(jdbc.queryForObject("SELECT notificada_en IS NOT NULL FROM notificaciones WHERE id=?", Boolean.class, id)).isTrue();
        mvc.perform(patch("/api/notificaciones/{id}/leida", id).cookie(cookie("b", Rol.REPRESENTANTE_ONG)))
                .andExpect(status().isNotFound());
        leer(id, "a");
        leer(id, "a");
        mvc.perform(get("/api/notificaciones").cookie(cookie("a", Rol.REPRESENTANTE_ONG)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].recepcion").value("CONFIRMADA"));
        verify(bonita, times(1)).completarTareaPorId("92001-Notificar a ONG");
        verify(bonita, times(1)).completarTareaPorId("92001-Recibir notificación");
        assertThat(jdbc.queryForObject("SELECT recibida_en IS NOT NULL FROM notificaciones WHERE id=?", Boolean.class, id)).isTrue();
    }

    @Test
    void lecturaAnticipadaDeLaSegundaOfertaEsperaSuPropioCiclo() throws Exception {
        adjudicar();
        leer(notificacion(92002), "b");
        assertThat(tarea.get()).isEqualTo("Recibir notificación");
        verify(bonita, never()).completarTareaPorId("92001-Recibir notificación");
        leer(notificacion(92001), "a");
        activa.set(92002);
        tarea.set("Notificar a ONG");
        coordinador.sincronizar(92001L);
        verify(bonita).completarTareaPorId("92002-Notificar a ONG");
        verify(bonita).completarTareaPorId("92002-Recibir notificación");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notificaciones WHERE recibida_en IS NOT NULL", Integer.class)).isEqualTo(2);
    }

    @Test
    void respuestaPerdidaSeReconciliaPorIdAunqueElCasoYaEsteArchivado() throws Exception {
        adjudicar();
        doAnswer(i -> {
            completar(i.getArgument(0));
            throw new BonitaIntegrationException("Respuesta perdida", new java.io.IOException("timeout"));
        }).when(bonita).completarTareaPorId("92001-Recibir notificación");
        long id = notificacion(92001);
        leer(id, "a");
        assertThat(jdbc.queryForObject("SELECT bonita_pendiente FROM notificaciones WHERE id=?", Boolean.class, id)).isTrue();
        jdbc.update("UPDATE intenciones_seleccion_bonita SET estado='LIBERADA'");
        assertThat(selecciones.recuperables(10)).containsExactly(92001L);
        when(bonita.buscarCaso("700")).thenReturn(Optional.empty());
        when(bonita.buscarCasoArchivado("700")).thenReturn(Optional.of(new BonitaCasoInfo("700", null, "completed", null, null)));
        when(bonita.leerVariable("700", "emergenciaId", true)).thenReturn(Optional.of("92001"));
        coordinador.sincronizar(92001L);
        assertThat(jdbc.queryForObject("SELECT recibida_en IS NOT NULL AND NOT bonita_pendiente FROM notificaciones WHERE id=?", Boolean.class, id)).isTrue();
        assertThat(selecciones.recuperables(10)).isEmpty();
        verify(bonita, times(1)).completarTareaPorId("92001-Recibir notificación");
    }

    @Test
    void reintentoNoEjecutaOtraTareaConElMismoNombre() throws Exception {
        adjudicar();
        doThrow(new BonitaIntegrationException("timeout", new java.io.IOException()))
                .when(bonita).completarTareaPorId("92001-Recibir notificación");
        leer(notificacion(92001), "a");
        jdbc.update("UPDATE notificaciones SET bonita_ultimo_intento=DATEADD('MINUTE',-1,CURRENT_TIMESTAMP) WHERE oferta_id=92001");
        when(bonita.buscarTareasActivas("700", "Recibir notificación")).thenReturn(List.of("otro-ciclo"));
        coordinador.sincronizar(92001L);
        verify(bonita, never()).completarTareaPorId("otro-ciclo");
        verify(bonita, times(1)).completarTareaPorId("92001-Recibir notificación");
    }

    @Test
    void recuperacionNoOcupaElLoteDeTrabajoMientrasEsperaLecturaDeLaOng() throws Exception {
        adjudicar();
        jdbc.update("UPDATE intenciones_seleccion_bonita SET estado='EN_CICLO' WHERE oferta_id=92001");
        assertThat(selecciones.recuperables(10)).isEmpty();
        jdbc.update("UPDATE notificaciones_usuarios SET fecha_lectura=CURRENT_TIMESTAMP WHERE notificacion_id=?", notificacion(92001));
        assertThat(selecciones.recuperables(10)).containsExactly(92001L);
        coordinador.sincronizar(92001L);
        assertThat(selecciones.recuperables(10)).isEmpty();
    }

    @Test
    void dosRepresentantesConfirmanConcurrentementeSinDuplicarRecepcion() throws Exception {
        adjudicar();
        long id = notificacion(92001);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { leer(id, "a"); return null; });
            var b = executor.submit(() -> { leer(id, "a2"); return null; });
            a.get();
            b.get();
        }
        assertThat(jdbc.queryForObject("SELECT recibida_en IS NOT NULL FROM notificaciones WHERE id=?", Boolean.class, id)).isTrue();
        verify(bonita, times(1)).completarTareaPorId("92001-Recibir notificación");
    }

    private void adjudicar() throws Exception {
        mvc.perform(put("/api/ofertas/seleccion").cookie(cookie("municipio", Rol.OPERADOR_MUNICIPAL))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"emergenciaId\":92001,\"ofertaIds\":[92001,92002]}"))
                .andExpect(status().isOk());
    }

    private void leer(long id, String usuario) throws Exception {
        mvc.perform(patch("/api/notificaciones/{id}/leida", id).cookie(cookie(usuario, Rol.REPRESENTANTE_ONG)))
                .andExpect(status().isNoContent());
    }

    private Cookie cookie(String nombre, Rol rol) {
        Usuario usuario = new Usuario();
        usuario.setEmail(nombre + "@notificaciones.test");
        usuario.setRol(rol);
        return new Cookie("access_token", jwt.generarToken(usuario));
    }

    private long notificacion(long ofertaId) {
        return jdbc.queryForObject("SELECT id FROM notificaciones WHERE oferta_id=?", Long.class, ofertaId);
    }

    private String taskId() { return activa.get() + "-" + tarea.get(); }

    private void completar(String id) {
        assertThat(id).isEqualTo(taskId());
        archivo.add(new BonitaActividadArchivada("arch-" + id, id, tarea.get(), "USER_TASK", "completed", null, null));
        tarea.set(tarea.get().equals("Notificar a ONG") ? "Recibir notificación" : "Comprometer recursos");
    }
}
