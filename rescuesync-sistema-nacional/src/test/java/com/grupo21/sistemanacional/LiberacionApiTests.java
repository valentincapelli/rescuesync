package com.grupo21.sistemanacional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grupo21.sistemanacional.model.Compromiso;
import com.grupo21.sistemanacional.model.EstadoCompromiso;
import com.grupo21.sistemanacional.model.EstadoHabilitacion;
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
import org.springframework.test.web.servlet.RequestBuilder;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Liberación de recursos (E3-07). Los compromisos se crean por repositorio porque el endpoint
 * de compromiso es E3-06.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LiberacionApiTests {

    private static final String PASSWORD = "secreto123";
    private static final long EMERGENCIA = 1L;
    private static final long OTRA_EMERGENCIA = 2L;

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
    private Cookie bonita;
    private Cookie admin;
    private Cookie representanteA;

    @BeforeEach
    void prepararDatos() throws Exception {
        compromisoRepository.deleteAll();
        recursoRepository.deleteAll();
        certificacionRepository.deleteAll();
        usuarioRepository.deleteAll();
        ongRepository.deleteAll();

        ongA = crearOng("30700000001", "ONG A");
        ongB = crearOng("30700000002", "ONG B");
        usuarioRepository.save(new Usuario("rep.a", passwordEncoder.encode(PASSWORD), Rol.ONG, ongA));
        usuarioRepository.save(new Usuario("admin", passwordEncoder.encode(PASSWORD), Rol.ADMIN, null));
        usuarioRepository.save(new Usuario("bonita", passwordEncoder.encode(PASSWORD), Rol.SERVICIO, null));

        representanteA = login("rep.a");
        admin = login("admin");
        bonita = login("bonita");
    }

    @Test
    void liberarUnaOfertaDeConsorcioDevuelveElStockDeTodasLasOngs() throws Exception {
        Recurso deA = recurso(ongA, 100, 40);   // 30 de la oferta 7 + 10 de la oferta 8
        Recurso deB = recurso(ongB, 50, 20);    // 20 de la oferta 7
        compromiso(deA, EMERGENCIA, 7, 30);
        compromiso(deB, EMERGENCIA, 7, 20);
        compromiso(deA, EMERGENCIA, 8, 10);

        mockMvc.perform(liberar(bonita, EMERGENCIA, 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liberados").value(2))
                .andExpect(jsonPath("$.yaLiberados").value(0))
                .andExpect(jsonPath("$.compromisos.length()").value(2))
                .andExpect(jsonPath("$.compromisos[0].estado").value("LIBERADO"))
                .andExpect(jsonPath("$.compromisos[0].fechaLiberacion").exists());

        assertThat(comprometido(deA)).isEqualTo(10);   // quedó solo la oferta 8
        assertThat(comprometido(deB)).isZero();
    }

    @Test
    void sinOfertaRefLiberaTodaLaEmergenciaYNoTocaOtras() throws Exception {
        Recurso deA = recurso(ongA, 100, 50);
        Recurso deB = recurso(ongB, 50, 20);
        compromiso(deA, EMERGENCIA, 7, 30);
        compromiso(deA, EMERGENCIA, 8, 10);
        compromiso(deA, OTRA_EMERGENCIA, 9, 10);
        compromiso(deB, EMERGENCIA, 7, 20);

        mockMvc.perform(liberar(admin, EMERGENCIA, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liberados").value(3))
                .andExpect(jsonPath("$.ofertaRef").doesNotExist());

        assertThat(comprometido(deA)).isEqualTo(10);   // solo la de la otra emergencia
        assertThat(comprometido(deB)).isZero();
    }

    @Test
    void reintentarEsIdempotenteYNoDevuelveStockDosVeces() throws Exception {
        Recurso deA = recurso(ongA, 100, 40);
        compromiso(deA, EMERGENCIA, 7, 30);
        compromiso(deA, EMERGENCIA, 8, 10);

        mockMvc.perform(liberar(bonita, EMERGENCIA, 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liberados").value(1));
        mockMvc.perform(liberar(bonita, EMERGENCIA, 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liberados").value(0))
                .andExpect(jsonPath("$.yaLiberados").value(1));

        assertThat(comprometido(deA)).isEqualTo(10);

        // liberar la emergencia entera después: solo falta la oferta 8
        mockMvc.perform(liberar(bonita, EMERGENCIA, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liberados").value(1))
                .andExpect(jsonPath("$.yaLiberados").value(1));
        assertThat(comprometido(deA)).isZero();
    }

    @Test
    void sinCompromisosDevuelve404() throws Exception {
        mockMvc.perform(liberar(bonita, 999L, null)).andExpect(status().isNotFound());
        mockMvc.perform(liberar(bonita, 999L, 5L)).andExpect(status().isNotFound());
    }

    @Test
    void siUnCompromisoNoSePuedeLiberarNoSeLiberaNinguno() throws Exception {
        Recurso deA = recurso(ongA, 100, 10);
        Recurso deB = recurso(ongB, 50, 0);             // dato inconsistente: nada comprometido
        Compromiso c1 = compromiso(deA, EMERGENCIA, 7, 10);
        Compromiso c2 = compromiso(deB, EMERGENCIA, 7, 5);

        mockMvc.perform(liberar(bonita, EMERGENCIA, 7L)).andExpect(status().isConflict());

        assertThat(comprometido(deA)).isEqualTo(10);
        assertThat(compromisoRepository.findById(c1.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoCompromiso.COMPROMETIDO);
        assertThat(compromisoRepository.findById(c2.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoCompromiso.COMPROMETIDO);
    }

    @Test
    void soloAdminYServicioPuedenLiberar() throws Exception {
        Recurso deA = recurso(ongA, 100, 10);
        compromiso(deA, EMERGENCIA, 7, 10);

        mockMvc.perform(liberar(representanteA, EMERGENCIA, 7L)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/compromisos/liberar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("emergenciaRef", EMERGENCIA, "ofertaRef", 7L))))
                .andExpect(status().isUnauthorized());

        assertThat(comprometido(deA)).isEqualTo(10);
    }

    @Test
    void emergenciaRefEsObligatoria() throws Exception {
        mockMvc.perform(post("/api/compromisos/liberar").cookie(bonita)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.emergenciaRef").exists());
    }

    // ---------------------------------------------------------------- helpers

    private Ong crearOng(String cuit, String razonSocial) {
        Ong ong = new Ong(cuit, razonSocial);
        ong.setEstadoHabilitacion(EstadoHabilitacion.HABILITADA);
        return ongRepository.save(ong);
    }

    private Recurso recurso(Ong ong, int total, int comprometido) {
        Recurso recurso = new Recurso(ong, TipoRecurso.ALIMENTOS, "kg", total);
        if (comprometido > 0) {
            recurso.comprometer(comprometido);
        }
        return recursoRepository.save(recurso);
    }

    private Compromiso compromiso(Recurso recurso, long emergencia, long oferta, int cantidad) {
        return compromisoRepository.save(new Compromiso(recurso, emergencia, oferta, cantidad));
    }

    private int comprometido(Recurso recurso) {
        return recursoRepository.findById(recurso.getId()).orElseThrow().getCantidadComprometida();
    }

    private Cookie login(String username) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("access_token");
    }

    private RequestBuilder liberar(Cookie quien, long emergenciaRef, Long ofertaRef) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("emergenciaRef", emergenciaRef);
        if (ofertaRef != null) {
            body.put("ofertaRef", ofertaRef);
        }
        return post("/api/compromisos/liberar")
                .cookie(quien)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body));
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }
}
