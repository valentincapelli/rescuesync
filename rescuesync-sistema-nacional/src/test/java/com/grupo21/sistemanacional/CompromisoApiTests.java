package com.grupo21.sistemanacional;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CompromisoApiTests {

    private static final String PASSWORD = "secreto123";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private OngRepository ongRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private CertificacionRepository certificacionRepository;
    @Autowired private RecursoRepository recursoRepository;
    @Autowired private CompromisoRepository compromisoRepository;

    private Cookie bonita;
    private Recurso recurso;

    @BeforeEach
    void prepararDatos() throws Exception {
        compromisoRepository.deleteAll();
        recursoRepository.deleteAll();
        certificacionRepository.deleteAll();
        usuarioRepository.deleteAll();
        ongRepository.deleteAll();

        Ong ong = new Ong("30700000001", "ONG A");
        ong.setEstadoHabilitacion(EstadoHabilitacion.HABILITADA);
        ong = ongRepository.save(ong);
        usuarioRepository.save(new Usuario(
                "bonita", passwordEncoder.encode(PASSWORD), Rol.SERVICIO, null
        ));
        bonita = login();
        recurso = recursoRepository.save(
                new Recurso(ong, TipoRecurso.ALIMENTOS, "kg", 10)
        );
    }

    @Test
    void creaCompromisoYLaLiberacionDevuelveElStockReservado() throws Exception {
        mockMvc.perform(comprometer(bonita, 1L, 7L, 4))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recursoId").value(recurso.getId()))
                .andExpect(jsonPath("$.emergenciaRef").value(1))
                .andExpect(jsonPath("$.ofertaRef").value(7))
                .andExpect(jsonPath("$.cantidad").value(4))
                .andExpect(jsonPath("$.estado").value("COMPROMETIDO"))
                .andExpect(jsonPath("$.fechaLiberacion").doesNotExist());

        assertThat(cantidadComprometida()).isEqualTo(4);

        mockMvc.perform(post("/api/compromisos/liberar")
                        .cookie(bonita)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("emergenciaRef", 1L, "ofertaRef", 7L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liberados").value(1))
                .andExpect(jsonPath("$.compromisos[0].estado").value("LIBERADO"));

        assertThat(cantidadComprometida()).isZero();
        assertThat(compromisoRepository.findAll()).singleElement()
                .extracting(c -> c.getEstado())
                .isEqualTo(EstadoCompromiso.LIBERADO);
    }

    @Test
    void reintentarElMismoCompromisoNoReservaStockDosVeces() throws Exception {
        mockMvc.perform(comprometer(bonita, 1L, 7L, 4))
                .andExpect(status().isCreated());
        mockMvc.perform(comprometer(bonita, 1L, 7L, 4))
                .andExpect(status().isCreated());

        assertThat(compromisoRepository.count()).isEqualTo(1);
        assertThat(cantidadComprometida()).isEqualTo(4);
    }

    @Test
    void reintentoDeMismaClaveDevuelveElExistenteSinCambiarElStock() throws Exception {
        mockMvc.perform(comprometer(bonita, 1L, 7L, 4))
                .andExpect(status().isCreated());

        mockMvc.perform(comprometer(bonita, 2L, 7L, 4))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.emergenciaRef").value(1))
                .andExpect(jsonPath("$.cantidad").value(4));
        mockMvc.perform(comprometer(bonita, 1L, 7L, 5))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.emergenciaRef").value(1))
                .andExpect(jsonPath("$.cantidad").value(4));

        assertThat(compromisoRepository.count()).isEqualTo(1);
        assertThat(cantidadComprometida()).isEqualTo(4);
    }

    @Test
    void stockInsuficienteNoCreaCompromiso() throws Exception {
        mockMvc.perform(comprometer(bonita, 1L, 7L, 11))
                .andExpect(status().isConflict());

        assertThat(compromisoRepository.count()).isZero();
        assertThat(cantidadComprometida()).isZero();
    }

    @Test
    void recursoIdDebeSerPositivo() throws Exception {
        mockMvc.perform(post("/api/compromisos")
                        .cookie(bonita)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "recursoId", 0,
                                "emergenciaRef", 1,
                                "ofertaRef", 7,
                                "cantidad", 1
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.recursoId").exists());

        assertThat(compromisoRepository.count()).isZero();
    }

    private org.springframework.test.web.servlet.RequestBuilder comprometer(
            Cookie cookie,
            long emergenciaRef,
            long ofertaRef,
            int cantidad
    ) throws Exception {
        return post("/api/compromisos")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "recursoId", recurso.getId(),
                        "emergenciaRef", emergenciaRef,
                        "ofertaRef", ofertaRef,
                        "cantidad", cantidad
                )));
    }

    private Cookie login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "bonita", "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie("access_token");
    }

    private int cantidadComprometida() {
        return recursoRepository.findById(recurso.getId()).orElseThrow().getCantidadComprometida();
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }
}
