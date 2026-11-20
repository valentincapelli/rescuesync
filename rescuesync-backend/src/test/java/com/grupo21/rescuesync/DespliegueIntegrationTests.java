package com.grupo21.rescuesync;

import com.grupo21.rescuesync.client.DespliegueBonitaClient;
import com.grupo21.rescuesync.client.EstadoDespliegueBonita;
import com.grupo21.rescuesync.exception.BonitaIntegrationException;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:despliegues;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "bonita.despliegues.sincronizacion-habilitada=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DespliegueIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired JwtService jwt;
    @MockitoBean DespliegueBonitaClient bonita;
    private final AtomicReference<EstadoDespliegueBonita> remoto = new AtomicReference<>();
    private Long emergencia;
    private Long oferta;
    private Long segunda;
    private Cookie centro;
    private Cookie ong;
    private Cookie ajena;

    @BeforeEach
    void preparar() {
        for (String tabla : new String[]{"novedades_despliegue", "despliegues", "intenciones_seleccion_bonita", "historial_estados", "ofertas",
                "lotes", "emergencias", "usuarios", "ongs", "municipios"}) db.update("DELETE FROM " + tabla);
        long municipio = insertar("municipios", "nombre", "Municipio prueba");
        long ongId = insertar("ongs", "nombre", "ONG prueba");
        long otraOng = insertar("ongs", "nombre", "Otra ONG");
        centro = usuario("centro@test.local", Rol.CENTRO_COORDINADOR, null);
        ong = usuario("ong@test.local", Rol.REPRESENTANTE_ONG, ongId);
        ajena = usuario("ajena@test.local", Rol.REPRESENTANTE_ONG, otraOng);
        emergencia = insertar("emergencias", "tipo_desastre,nivel_gravedad,zona_afectada,descripcion,municipio_id,estado,bonita_case_id",
                "INUNDACION", "ALTO", "Zona", "Descripción", municipio, "ADJUDICADA", 700L);
        long lote = insertar("lotes", "emergencia_id,tipo_recurso,descripcion,cantidad_requerida,unidad_medida,estado",
                emergencia, "ALIMENTOS", "Asistencia", 100, "raciones", "CERRADO");
        oferta = insertar("ofertas", "lote_id,ong_id,cantidad_ofrecida,estado", lote, ongId, 60, "SELECCIONADA");
        segunda = insertar("ofertas", "lote_id,ong_id,cantidad_ofrecida,estado", lote, otraOng, 40, "EVALUADA");
        remoto.set(estado("m1", null, Set.of(), Set.of(), false));
        when(bonita.consultar(emergencia, 700L)).thenAnswer(i -> remoto.get());
        doAnswer(i -> {
            if (i.getArgument(0).equals("m1")) remoto.set(estado(null, "f1", Set.of("m1"), Set.of(), false));
            if (i.getArgument(0).equals("f1")) remoto.set(estado(null, null, Set.of("m1", "f1"), Set.of(), false));
            return null;
        }).when(bonita).completar(any());
    }

    @Test
    void dosSolicitudesConcurrentesGuardanUnaSolaAccion() throws Exception {
        doNothing().when(bonita).completar("m1");
        sincronizar();
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var inicio = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Integer> solicitud = () -> {
                inicio.await();
                return accion("monitoreo/completar", centro, "Supervisado").andReturn().getResponse().getStatus();
            };
            var a = executor.submit(solicitud);
            var b = executor.submit(solicitud);
            inicio.countDown();
            assertThat(a.get(10, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(202);
            assertThat(b.get(10, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(202);
        }
        assertThat(contar("novedades_despliegue", "tipo='MONITOREO_COMPLETADO'")).isEqualTo(1);
        assertThat(contar("historial_estados", "tipo_entidad='OFERTA' AND estado_nuevo='COMPROMETIDA'")).isEqualTo(1);
        verify(bonita, never()).completar("f1");
    }

    @Test
    void avanceExternoConfirmaPendientesAunqueElArchivoLlegueMasTarde() throws Exception {
        doNothing().when(bonita).completar("m1");
        accion("monitoreo/completar", centro, "Supervisado").andExpect(status().isAccepted());
        remoto.set(estado(null, null, Set.of(), Set.of(oferta), true));
        sincronizar();
        assertThat(contar("novedades_despliegue", "confirmada_en IS NULL")).isZero();
        assertThat(estadoLocal("emergencias", emergencia)).isEqualTo("CERRADA");
        when(bonita.consultar(emergencia, 700L)).thenThrow(new BonitaIntegrationException("Caída posterior", null));
        accion("monitoreo/completar", centro, "Reintento").andExpect(status().isOk());
    }

    @Test
    void circuitoCompletoConHistorialYLiberacionSeparada() throws Exception {
        sincronizar();
        assertThat(estadoLocal("ofertas", oferta)).isEqualTo("COMPROMETIDA");
        assertThat(estadoLocal("emergencias", emergencia)).isEqualTo("EN_EJECUCION");
        accion("seguimientos", centro, "Ayuda en camino").andExpect(status().isOk())
                .andExpect(jsonPath("$.novedades[0].autor").value("centro@test.local"));
        accion("monitoreo/completar", centro, "Despliegue supervisado").andExpect(status().isOk())
                .andExpect(jsonPath("$.monitoreadoEn").isNotEmpty());
        accion("finalizacion", ong, "Asistencia completada").andExpect(status().isOk())
                .andExpect(jsonPath("$.finalizadoEn").isNotEmpty())
                .andExpect(jsonPath("$.liberadoEn").isEmpty());
        assertThat(estadoLocal("ofertas", oferta)).isEqualTo("FINALIZADA");
        assertThat(estadoLocal("emergencias", emergencia)).isEqualTo("EN_EJECUCION");
        remoto.set(estado(null, null, Set.of("m1", "f1"), Set.of(oferta), true));
        sincronizar();
        sincronizar();
        assertThat(estadoLocal("emergencias", emergencia)).isEqualTo("CERRADA");
        assertThat(contar("historial_estados", "tipo_entidad='OFERTA' AND estado_nuevo='COMPROMETIDA'" )).isEqualTo(1);
        assertThat(contar("historial_estados", "tipo_entidad='EMERGENCIA' AND estado_nuevo='CERRADA'" )).isEqualTo(1);
        Cookie directivo = usuario("direccion@test.local", Rol.DIRECTIVO, null);
        mvc.perform(get("/api/indicadores/suministros").cookie(directivo)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totales[0].cantidad").value(60));
        mvc.perform(get("/api/indicadores/proyectos").cookie(directivo)).andExpect(status().isOk())
                .andExpect(jsonPath("$.finalizados").value(1))
                .andExpect(jsonPath("$.proyectos[0].fechaCierre").isNotEmpty());
        verify(bonita, times(1)).completar("m1");
        verify(bonita, times(1)).completar("f1");
    }

    @Test
    void permisosPertenenciaYValidacionDelFormulario() throws Exception {
        mvc.perform(get("/api/actividades/mias")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/actividades/mias").cookie(centro)).andExpect(status().isForbidden());
        accion("finalizacion", ajena, "Ajena").andExpect(status().isForbidden());
        verifyNoInteractions(bonita);
        accion("monitoreo/completar", ong, "Sin permiso").andExpect(status().isForbidden());
        accion("seguimientos", centro, "   ").andExpect(status().isBadRequest());
        accion("seguimientos", centro, "x".repeat(2001)).andExpect(status().isBadRequest());
        sincronizar();
        mvc.perform(get("/api/actividades/mias").cookie(ajena)).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/actividades/mias").cookie(ong)).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void noFinalizaAntesDelMonitoreoNiIniciaSinCompromiso() throws Exception {
        accion("finalizacion", ong, "Prematuro").andExpect(status().isConflict());
        remoto.set(new EstadoDespliegueBonita(700L, oferta, null, null, Set.of(), false, "m1", null, Set.of()));
        // Otra oferta de esta emergencia no tiene compromiso, aunque el caso tenga una tarea activa.
        mvc.perform(post("/api/ofertas/" + segunda + "/monitoreo/completar").cookie(centro)
                .contentType(MediaType.APPLICATION_JSON).content("{\"observacion\":\"Sin compromiso\"}"))
                .andExpect(status().isConflict());
        verify(bonita, never()).completar(any());
    }

    @Test
    void timeoutDespuesDeEjecutarSeReconciliaSinRepetirLaTarea() throws Exception {
        doAnswer(i -> {
            remoto.set(estado(null, "f1", Set.of("m1"), Set.of(), false));
            throw new BonitaIntegrationException("Respuesta perdida", null);
        }).when(bonita).completar("m1");
        accion("monitoreo/completar", centro, "Confirmar").andExpect(status().isOk());
        accion("monitoreo/completar", centro, "Reintento").andExpect(status().isOk());
        verify(bonita, times(1)).completar("m1");
        assertThat(contar("novedades_despliegue", "tipo='MONITOREO_COMPLETADO'")).isEqualTo(1);
    }

    @Test
    void caidaConservaIntencionYLaSiguienteSincronizacionLaRecupera() throws Exception {
        doThrow(new BonitaIntegrationException("Caída", null)).when(bonita).completar("m1");
        accion("monitoreo/completar", centro, "Supervisado").andExpect(status().isAccepted())
                .andExpect(jsonPath("$.pendienteSincronizacion").value(true))
                .andExpect(jsonPath("$.monitoreadoEn").isEmpty());
        assertThat(contar("novedades_despliegue", "confirmada_en IS NULL")).isEqualTo(1);
        doAnswer(i -> { remoto.set(estado(null, "f1", Set.of("m1"), Set.of(), false)); return null; })
                .when(bonita).completar("m1");
        sincronizar();
        assertThat(contar("novedades_despliegue", "confirmada_en IS NULL")).isZero();
    }

    @Test
    void terminarPrimeraOfertaNoCierraMientrasQuedeOtraAdjudicada() throws Exception {
        db.update("UPDATE ofertas SET estado='SELECCIONADA' WHERE id=?", segunda);
        remoto.set(estado(null, null, Set.of(), Set.of(oferta), true));
        sincronizar();
        assertThat(estadoLocal("emergencias", emergencia)).isEqualTo("EN_EJECUCION");
        assertThat(estadoLocal("ofertas", segunda)).isEqualTo("SELECCIONADA");
        remoto.set(new EstadoDespliegueBonita(700L, segunda, segunda, 40, Set.of(oferta), false, "m2", null, Set.of()));
        sincronizar();
        assertThat(estadoLocal("ofertas", segunda)).isEqualTo("COMPROMETIDA");
        remoto.set(new EstadoDespliegueBonita(700L, segunda, segunda, 40, Set.of(oferta, segunda), true, null, null, Set.of()));
        sincronizar();
        assertThat(estadoLocal("emergencias", emergencia)).isEqualTo("CERRADA");
    }

    @Test
    void noMezclaReferenciasNiCantidadesDeOtroCiclo() throws Exception {
        remoto.set(new EstadoDespliegueBonita(700L, oferta, oferta, 999, Set.of(), false, "m1", null, Set.of()));
        mvc.perform(post(rutaSync()).cookie(centro)).andExpect(status().isConflict());
        assertThat(contar("despliegues", "1=1")).isZero();
        remoto.set(estado(null, null, Set.of(), Set.of(999999L), false));
        mvc.perform(post(rutaSync()).cookie(centro)).andExpect(status().isConflict());
        assertThat(estadoLocal("ofertas", oferta)).isEqualTo("SELECCIONADA");
    }

    @Test
    void casoSinIntegracionNoInventaEstados() throws Exception {
        when(bonita.consultar(emergencia, 700L)).thenThrow(new BonitaIntegrationException("Falta E3-10", null));
        mvc.perform(post(rutaSync()).cookie(centro)).andExpect(status().isBadGateway());
        assertThat(contar("despliegues", "1=1")).isZero();
        assertThat(estadoLocal("emergencias", emergencia)).isEqualTo("ADJUDICADA");
    }

    @Test
    void estadoDeAdjudicacionEsVisibleSinAfirmarCompromisoYRespetaPermisos() throws Exception {
        insertar("intenciones_seleccion_bonita", "oferta_id,estado,version,intentos",
                oferta, "PENDIENTE", 0, 0);
        mvc.perform(get("/api/emergencias/" + emergencia + "/selecciones").cookie(centro))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ofertaId").value(oferta))
                .andExpect(jsonPath("$[0].estadoIntegracion").value("PENDIENTE"))
                .andExpect(jsonPath("$[0].comprometidoEn").doesNotExist());
        mvc.perform(get("/api/emergencias/" + emergencia + "/selecciones").cookie(ong))
                .andExpect(status().isForbidden());
    }

    private EstadoDespliegueBonita estado(String monitoreo, String finalizar, Set<String> completadas, Set<Long> liberadas, boolean terminado) {
        return new EstadoDespliegueBonita(700L, oferta, oferta, 60, liberadas, terminado, monitoreo, finalizar, completadas);
    }
    private String rutaSync() { return "/api/emergencias/" + emergencia + "/despliegues/sincronizar"; }
    private void sincronizar() throws Exception { mvc.perform(post(rutaSync()).cookie(centro)).andExpect(status().isOk()); }
    private org.springframework.test.web.servlet.ResultActions accion(String ruta, Cookie cookie, String texto) throws Exception {
        return mvc.perform(post("/api/ofertas/" + oferta + "/" + ruta).cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON).content("{\"observacion\":\"" + texto + "\"}"));
    }
    private String estadoLocal(String tabla, Long id) { return db.queryForObject("SELECT estado FROM " + tabla + " WHERE id=?", String.class, id); }
    private int contar(String tabla, String condicion) { return db.queryForObject("SELECT COUNT(*) FROM " + tabla + " WHERE " + condicion, Integer.class); }
    private long insertar(String tabla, String columnas, Object... valores) {
        String parametros = String.join(",", java.util.Collections.nCopies(valores.length, "?"));
        db.update("INSERT INTO " + tabla + " (" + columnas + ",created_at,updated_at) VALUES (" + parametros + ",CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", valores);
        return db.queryForObject("SELECT MAX(id) FROM " + tabla, Long.class);
    }
    private Cookie usuario(String email, Rol rol, Long ongId) {
        insertar("usuarios", "email,password_hash,rol,ong_id", email, "hash", rol.name(), ongId);
        Usuario u = new Usuario(); u.setEmail(email); u.setRol(rol);
        return new Cookie("access_token", jwt.generarToken(u));
    }
}
