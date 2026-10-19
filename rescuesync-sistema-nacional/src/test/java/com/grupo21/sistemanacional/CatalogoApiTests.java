package com.grupo21.sistemanacional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grupo21.sistemanacional.model.Certificacion;
import com.grupo21.sistemanacional.model.EstadoHabilitacion;
import com.grupo21.sistemanacional.model.NivelCompetencia;
import com.grupo21.sistemanacional.model.NivelGravedad;
import com.grupo21.sistemanacional.model.Ong;
import com.grupo21.sistemanacional.model.Recurso;
import com.grupo21.sistemanacional.model.Rol;
import com.grupo21.sistemanacional.model.TipoRecurso;
import com.grupo21.sistemanacional.model.Usuario;
import com.grupo21.sistemanacional.repository.CertificacionRepository;
import com.grupo21.sistemanacional.repository.CompromisoRepository;
import com.grupo21.sistemanacional.repository.OngRepository;
import com.grupo21.sistemanacional.repository.RecursoRepository;
import com.grupo21.sistemanacional.repository.UsuarioRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba de punta a punta (HTTP + seguridad + base H2) de ONGs, certificaciones y recursos.
 * Se autentica con el login real, igual que lo haría Swagger o Bonita.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CatalogoApiTests {

    private static final String PASSWORD = "secreto123";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private OngRepository ongRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private CertificacionRepository certificacionRepository;
    @Autowired private RecursoRepository recursoRepository;
    @Autowired private CompromisoRepository compromisoRepository;

    private Ong ongA;
    private Ong ongB;
    private Cookie representanteA;
    private Cookie representanteB;
    private Cookie admin;
    private Cookie bonita;

    @BeforeEach
    void prepararDatos() throws Exception {
        compromisoRepository.deleteAll();
        recursoRepository.deleteAll();
        certificacionRepository.deleteAll();
        usuarioRepository.deleteAll();
        ongRepository.deleteAll();

        ongA = crearOng("30700000001", "ONG A", EstadoHabilitacion.HABILITADA);
        ongB = crearOng("30700000002", "ONG B", EstadoHabilitacion.HABILITADA);
        crearUsuario("rep.a", Rol.ONG, ongA);
        crearUsuario("rep.b", Rol.ONG, ongB);
        crearUsuario("admin", Rol.ADMIN, null);
        crearUsuario("bonita", Rol.SERVICIO, null);

        representanteA = login("rep.a");
        representanteB = login("rep.b");
        admin = login("admin");
        bonita = login("bonita");
    }

    // ---------------------------------------------------------------- registro de ONGs

    @Test
    void registroEsPublicoCreaOngHabilitadaYPermiteLogin() throws Exception {
        mockMvc.perform(post("/api/ongs/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("cuit", "30711111111", "razonSocial", "Ayuda Sur",
                                "username", "ayuda.sur", "password", PASSWORD))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.cuit").value("30711111111"))
                .andExpect(jsonPath("$.estadoHabilitacion").value("HABILITADA"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "ayuda.sur", "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("ONG"))
                .andExpect(cookie().exists("access_token"));
    }

    @Test
    void registroConCuitRepetidoDevuelve409YNoCreaUsuario() throws Exception {
        mockMvc.perform(registro("30700000001", "otro.usuario"))
                .andExpect(status().isConflict());

        org.assertj.core.api.Assertions.assertThat(usuarioRepository.existsByUsername("otro.usuario")).isFalse();
    }

    @Test
    void registroConUsernameRepetidoDevuelve409YNoCreaOng() throws Exception {
        mockMvc.perform(registro("30799999999", "rep.a"))
                .andExpect(status().isConflict());

        org.assertj.core.api.Assertions.assertThat(ongRepository.existsByCuit("30799999999")).isFalse();
    }

    @Test
    void registroConDatosInvalidosDevuelve400ConErroresPorCampo() throws Exception {
        mockMvc.perform(post("/api/ongs/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("cuit", "123", "razonSocial", "X", "username", "u", "password", "corta"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.cuit").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    // ---------------------------------------------------------------- catálogo de ONGs

    @Test
    void catalogoRequiereAutenticacion() throws Exception {
        mockMvc.perform(get("/api/ongs")).andExpect(status().isUnauthorized());
    }

    @Test
    void catalogoListaSoloOngsHabilitadas() throws Exception {
        crearOng("30700000003", "ONG Pendiente", EstadoHabilitacion.PENDIENTE);

        mockMvc.perform(get("/api/ongs").cookie(representanteA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].razonSocial").value("ONG A"))
                .andExpect(jsonPath("$[1].razonSocial").value("ONG B"));
    }

    @Test
    void unaOngSoloVeElDetalleDeSiMisma() throws Exception {
        mockMvc.perform(get("/api/ongs/" + ongA.getId()).cookie(representanteA)).andExpect(status().isOk());
        mockMvc.perform(get("/api/ongs/" + ongB.getId()).cookie(representanteA)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/ongs/" + ongB.getId()).cookie(admin)).andExpect(status().isOk());
        mockMvc.perform(get("/api/ongs/" + ongB.getId()).cookie(bonita)).andExpect(status().isOk());
        mockMvc.perform(get("/api/ongs/999999").cookie(admin)).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- certificaciones

    @Test
    void soloElAdminCreaCertificaciones() throws Exception {
        mockMvc.perform(crearCertificacion(ongA, representanteA, "MEDICAMENTOS"))
                .andExpect(status().isForbidden());
        mockMvc.perform(crearCertificacion(ongA, bonita, "MEDICAMENTOS"))
                .andExpect(status().isForbidden());

        mockMvc.perform(crearCertificacion(ongA, admin, "MEDICAMENTOS"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoRecurso").value("MEDICAMENTOS"))
                .andExpect(jsonPath("$.nivelCompetencia").value("AVANZADO"))
                .andExpect(jsonPath("$.nivelRiesgoMax").value("ALTO"))
                .andExpect(jsonPath("$.vigente").value(true));
    }

    @Test
    void certificacionDuplicadaParaElMismoTipoDevuelve409() throws Exception {
        mockMvc.perform(crearCertificacion(ongA, admin, "ALIMENTOS")).andExpect(status().isCreated());
        mockMvc.perform(crearCertificacion(ongA, admin, "ALIMENTOS")).andExpect(status().isConflict());
        // otro tipo sí se puede
        mockMvc.perform(crearCertificacion(ongA, admin, "TRANSPORTE")).andExpect(status().isCreated());
    }

    @Test
    void certificacionConFechaPasadaDevuelve400() throws Exception {
        mockMvc.perform(post("/api/ongs/" + ongA.getId() + "/certificaciones")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("tipoRecurso", "ALIMENTOS", "nivelCompetencia", "BASICO",
                                "nivelRiesgoMax", "BAJO", "fechaVencimiento", "2020-01-01"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.fechaVencimiento").exists());
    }

    @Test
    void listadoInformaCuandoUnaCertificacionEstaVencida() throws Exception {
        certificacionRepository.save(new Certificacion(ongA, TipoRecurso.ALIMENTOS, NivelCompetencia.BASICO,
                NivelGravedad.BAJO, LocalDate.of(2020, 1, 1)));
        certificacionRepository.save(new Certificacion(ongA, TipoRecurso.MEDICAMENTOS, NivelCompetencia.AVANZADO,
                NivelGravedad.CRITICO, null));

        // ordenado por tipo: ALIMENTOS, MEDICAMENTOS
        mockMvc.perform(get("/api/ongs/" + ongA.getId() + "/certificaciones").cookie(representanteA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].tipoRecurso").value("ALIMENTOS"))
                .andExpect(jsonPath("$[0].vigente").value(false))
                .andExpect(jsonPath("$[1].tipoRecurso").value("MEDICAMENTOS"))
                .andExpect(jsonPath("$[1].vigente").value(true));
    }

    @Test
    void unaOngNoVeLasCertificacionesDeOtra() throws Exception {
        mockMvc.perform(get("/api/ongs/" + ongB.getId() + "/certificaciones").cookie(representanteA))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/ongs/" + ongB.getId() + "/certificaciones").cookie(bonita))
                .andExpect(status().isOk());
    }

    @Test
    void adminActualizaYEliminaUnaCertificacion() throws Exception {
        Certificacion cert = certificacionRepository.save(new Certificacion(ongA, TipoRecurso.ALIMENTOS,
                NivelCompetencia.BASICO, NivelGravedad.BAJO, null));
        String url = "/api/ongs/" + ongA.getId() + "/certificaciones/" + cert.getId();

        mockMvc.perform(put(url).cookie(representanteA).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nivelCompetencia", "AVANZADO", "nivelRiesgoMax", "CRITICO"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(put(url).cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nivelCompetencia", "AVANZADO", "nivelRiesgoMax", "CRITICO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nivelCompetencia").value("AVANZADO"))
                .andExpect(jsonPath("$.nivelRiesgoMax").value("CRITICO"));

        // con el id de otra ONG en la URL no la encuentra
        mockMvc.perform(delete("/api/ongs/" + ongB.getId() + "/certificaciones/" + cert.getId()).cookie(admin))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(url).cookie(admin)).andExpect(status().isNoContent());
        org.assertj.core.api.Assertions.assertThat(certificacionRepository.count()).isZero();
    }

    // ---------------------------------------------------------------- recursos

    @Test
    void ongCreaRecursoYLaUnidadSeNormaliza() throws Exception {
        mockMvc.perform(crearRecurso(ongA, representanteA, "ALIMENTOS", "  KG ", 100))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unidadMedida").value("kg"))
                .andExpect(jsonPath("$.cantidadTotal").value(100))
                .andExpect(jsonPath("$.cantidadComprometida").value(0))
                .andExpect(jsonPath("$.cantidadDisponible").value(100));

        // misma ONG, tipo y unidad (distinta escritura) => duplicado
        mockMvc.perform(crearRecurso(ongA, representanteA, "ALIMENTOS", "kg", 5))
                .andExpect(status().isConflict());
        // otra unidad sí se puede
        mockMvc.perform(crearRecurso(ongA, representanteA, "ALIMENTOS", "raciones", 5))
                .andExpect(status().isCreated());
    }

    @Test
    void soloElRepresentanteDeLaOngEscribeSuInventario() throws Exception {
        mockMvc.perform(crearRecurso(ongA, representanteB, "ALIMENTOS", "kg", 10)).andExpect(status().isForbidden());
        mockMvc.perform(crearRecurso(ongA, admin, "ALIMENTOS", "kg", 10)).andExpect(status().isForbidden());
        mockMvc.perform(crearRecurso(ongA, bonita, "ALIMENTOS", "kg", 10)).andExpect(status().isForbidden());
    }

    @Test
    void ongNoHabilitadaNoPuedeCrearRecursos() throws Exception {
        Ong pendiente = crearOng("30700000009", "Pendiente", EstadoHabilitacion.PENDIENTE);
        crearUsuario("rep.pendiente", Rol.ONG, pendiente);

        mockMvc.perform(crearRecurso(pendiente, login("rep.pendiente"), "ALIMENTOS", "kg", 10))
                .andExpect(status().isConflict());
    }

    @Test
    void recursoConCantidadInvalidaDevuelve400() throws Exception {
        mockMvc.perform(crearRecurso(ongA, representanteA, "ALIMENTOS", "kg", 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.cantidadTotal").exists());
    }

    @Test
    void inventarioLoVenElDuenioElAdminYBonitaPeroNoOtraOng() throws Exception {
        recursoRepository.save(new Recurso(ongA, TipoRecurso.AGUA_POTABLE, "litros", 500));
        String url = "/api/ongs/" + ongA.getId() + "/recursos";

        mockMvc.perform(get(url).cookie(representanteA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tipoRecurso").value("AGUA_POTABLE"));
        mockMvc.perform(get(url).cookie(admin)).andExpect(status().isOk());
        mockMvc.perform(get(url).cookie(bonita)).andExpect(status().isOk());
        mockMvc.perform(get(url).cookie(representanteB)).andExpect(status().isForbidden());
    }

    @Test
    void noSePuedeBajarElTotalPorDebajoDeLoComprometido() throws Exception {
        Recurso recurso = recursoConComprometido(ongA, 100, 30);
        String url = "/api/ongs/" + ongA.getId() + "/recursos/" + recurso.getId();

        mockMvc.perform(put(url).cookie(representanteA).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("cantidadTotal", 20))))
                .andExpect(status().isConflict());

        mockMvc.perform(put(url).cookie(representanteA).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("cantidadTotal", 30))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cantidadTotal").value(30))
                .andExpect(jsonPath("$.cantidadDisponible").value(0));
    }

    @Test
    void unaOngNoPuedeModificarNiVerElRecursoDeOtra() throws Exception {
        Recurso deB = recursoRepository.save(new Recurso(ongB, TipoRecurso.ALIMENTOS, "kg", 10));

        // por su propia URL, el recurso ajeno "no existe"
        mockMvc.perform(put("/api/ongs/" + ongA.getId() + "/recursos/" + deB.getId())
                        .cookie(representanteA).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("cantidadTotal", 99))))
                .andExpect(status().isNotFound());
        // por la URL de la otra ONG, directamente no tiene permiso
        mockMvc.perform(put("/api/ongs/" + ongB.getId() + "/recursos/" + deB.getId())
                        .cookie(representanteA).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("cantidadTotal", 99))))
                .andExpect(status().isForbidden());
    }

    @Test
    void eliminarRecursoSoloSiNoTieneComprometido() throws Exception {
        Recurso recurso = recursoConComprometido(ongA, 100, 30);
        String url = "/api/ongs/" + ongA.getId() + "/recursos/" + recurso.getId();

        mockMvc.perform(delete(url).cookie(representanteA)).andExpect(status().isConflict());

        Recurso liberado = recursoRepository.findById(recurso.getId()).orElseThrow();
        liberado.liberar(30);
        recursoRepository.save(liberado);

        mockMvc.perform(delete(url).cookie(representanteA)).andExpect(status().isNoContent());
        org.assertj.core.api.Assertions.assertThat(recursoRepository.count()).isZero();
    }

    @Test
    void disponibilidadGlobalSumaSoloOngsHabilitadas() throws Exception {
        Ong pendiente = crearOng("30700000008", "Pendiente", EstadoHabilitacion.PENDIENTE);
        recursoConComprometido(ongA, 100, 30);                                       // 70 disponibles
        recursoRepository.save(new Recurso(ongB, TipoRecurso.ALIMENTOS, "kg", 50));  // 50
        recursoRepository.save(new Recurso(pendiente, TipoRecurso.ALIMENTOS, "kg", 999)); // no cuenta
        recursoRepository.save(new Recurso(ongB, TipoRecurso.AGUA_POTABLE, "litros", 200));

        mockMvc.perform(get("/api/recursos/disponibilidad").cookie(bonita))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.tipoRecurso=='ALIMENTOS')].cantidadDisponible").value(120))
                .andExpect(jsonPath("$[?(@.tipoRecurso=='AGUA_POTABLE')].cantidadDisponible").value(200));

        mockMvc.perform(get("/api/recursos/disponibilidad").param("tipoRecurso", "AGUA_POTABLE").cookie(representanteA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].unidadMedida").value("litros"));
    }

    // ---------------------------------------------------------------- helpers

    private Ong crearOng(String cuit, String razonSocial, EstadoHabilitacion estado) {
        Ong ong = new Ong(cuit, razonSocial);
        ong.setEstadoHabilitacion(estado);
        return ongRepository.save(ong);
    }

    private void crearUsuario(String username, Rol rol, Ong ong) {
        usuarioRepository.save(new Usuario(username, passwordEncoder.encode(PASSWORD), rol, ong));
    }

    private Recurso recursoConComprometido(Ong ong, int total, int comprometido) {
        Recurso recurso = new Recurso(ong, TipoRecurso.ALIMENTOS, "kg", total);
        recurso.comprometer(comprometido);
        return recursoRepository.save(recurso);
    }

    private Cookie login(String username) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("access_token");
    }

    private org.springframework.test.web.servlet.RequestBuilder registro(String cuit, String username) throws Exception {
        return post("/api/ongs/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("cuit", cuit, "razonSocial", "Otra ONG",
                        "username", username, "password", PASSWORD)));
    }

    private org.springframework.test.web.servlet.RequestBuilder crearCertificacion(Ong ong, Cookie quien, String tipo)
            throws Exception {
        return post("/api/ongs/" + ong.getId() + "/certificaciones")
                .cookie(quien)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("tipoRecurso", tipo, "nivelCompetencia", "AVANZADO", "nivelRiesgoMax", "ALTO")));
    }

    private org.springframework.test.web.servlet.RequestBuilder crearRecurso(Ong ong, Cookie quien, String tipo,
                                                                            String unidad, int cantidad)
            throws Exception {
        return post("/api/ongs/" + ong.getId() + "/recursos")
                .cookie(quien)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("tipoRecurso", tipo, "unidadMedida", unidad, "cantidadTotal", cantidad)));
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }
}
