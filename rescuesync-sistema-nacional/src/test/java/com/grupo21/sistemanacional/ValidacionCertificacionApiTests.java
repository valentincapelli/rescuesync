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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** E3-05: validación por CUIT con HTTP, JWT y la base real de pruebas (H2). */
@SpringBootTest
@AutoConfigureMockMvc
class ValidacionCertificacionApiTests {

    private static final String URL = "/api/certificaciones/validar";
    private static final String PASSWORD = "secreto123";
    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private OngRepository ongRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private CertificacionRepository certificacionRepository;
    @Autowired private RecursoRepository recursoRepository;
    @Autowired private CompromisoRepository compromisoRepository;

    private Ong ong;
    private Cookie bonita;

    @BeforeEach
    void prepararDatos() throws Exception {
        compromisoRepository.deleteAll();
        recursoRepository.deleteAll();
        certificacionRepository.deleteAll();
        usuarioRepository.deleteAll();
        ongRepository.deleteAll();

        ong = new Ong("30700000001", "ONG de prueba");
        ong.setEstadoHabilitacion(EstadoHabilitacion.HABILITADA);
        ong = ongRepository.save(ong);
        usuarioRepository.save(new Usuario("bonita", passwordEncoder.encode(PASSWORD), Rol.SERVICIO, null));
        bonita = login("bonita");
    }

    @ParameterizedTest
    @CsvSource({"BASICO, BAJO", "INTERMEDIO, MEDIO", "AVANZADO, ALTO", "BASICO, CRITICO"})
    void devuelveLosNivelesCertificadosSinConvertirlosEnUnaAprobacionBinaria(
            NivelCompetencia competencia, NivelGravedad riesgoMax) throws Exception {
        LocalDate vencimiento = hoy().plusDays(30);
        certificacionRepository.save(new Certificacion(ong, TipoRecurso.ALIMENTOS,
                competencia, riesgoMax, vencimiento));

        mockMvc.perform(validar(ong.getCuit(), TipoRecurso.ALIMENTOS).cookie(bonita))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cuit").value(ong.getCuit()))
                .andExpect(jsonPath("$.tipoRecurso").value("ALIMENTOS"))
                .andExpect(jsonPath("$.estadoHabilitacion").value("HABILITADA"))
                .andExpect(jsonPath("$.capacidadesHabilitadas.length()").value(1))
                .andExpect(jsonPath("$.capacidadesHabilitadas[0].tipoRecurso").value("ALIMENTOS"))
                .andExpect(jsonPath("$.capacidadesHabilitadas[0].nivelCompetencia").value(competencia.name()))
                .andExpect(jsonPath("$.capacidadesHabilitadas[0].nivelRiesgoMax").value(riesgoMax.name()))
                .andExpect(jsonPath("$.capacidadesHabilitadas[0].fechaVencimiento").value(vencimiento.toString()))
                .andExpect(jsonPath("$.observaciones").isNotEmpty());
    }

    @Test
    void unaCertificacionQueVenceHoySigueVigente() throws Exception {
        certificar(hoy());

        mockMvc.perform(validar(ong.getCuit(), TipoRecurso.ALIMENTOS).cookie(bonita))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacidadesHabilitadas.length()").value(1));
    }

    @Test
    void unaCertificacionSinVencimientoEsVigente() throws Exception {
        certificar(null);

        mockMvc.perform(validar(ong.getCuit(), TipoRecurso.ALIMENTOS).cookie(bonita))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacidadesHabilitadas.length()").value(1))
                .andExpect(jsonPath("$.capacidadesHabilitadas[0].fechaVencimiento").value(nullValue()));
    }

    @Test
    void unaCertificacionVencidaDevuelveCapacidadesVaciasYSeConserva() throws Exception {
        Certificacion certificacion = certificar(hoy().minusDays(1));

        mockMvc.perform(validar(ong.getCuit(), TipoRecurso.ALIMENTOS).cookie(bonita))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacidadesHabilitadas").isEmpty())
                .andExpect(jsonPath("$.observaciones").value(containsString("no tiene una certificación vigente")));

        assertThat(certificacionRepository.findById(certificacion.getId())).isPresent();
    }

    @Test
    void unaCertificacionDeOtroTipoUOtraOngNoHabilitaLaCapacidadConsultada() throws Exception {
        certificar(null);
        Ong otra = new Ong("30700000002", "Otra ONG");
        otra.setEstadoHabilitacion(EstadoHabilitacion.HABILITADA);
        otra = ongRepository.save(otra);
        certificacionRepository.save(new Certificacion(otra, TipoRecurso.MEDICAMENTOS,
                NivelCompetencia.AVANZADO, NivelGravedad.CRITICO, null));

        mockMvc.perform(validar(ong.getCuit(), TipoRecurso.MEDICAMENTOS).cookie(bonita))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoHabilitacion").value("HABILITADA"))
                .andExpect(jsonPath("$.capacidadesHabilitadas").isEmpty())
                .andExpect(jsonPath("$.observaciones").value(containsString("no tiene una certificación vigente")));
    }

    @Test
    void ongSinCertificacionesDevuelveUnPerfilVacio() throws Exception {
        mockMvc.perform(validar(ong.getCuit(), TipoRecurso.ALIMENTOS).cookie(bonita))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacidadesHabilitadas").isEmpty())
                .andExpect(jsonPath("$.observaciones").isNotEmpty());
    }

    @ParameterizedTest
    @EnumSource(value = EstadoHabilitacion.class, names = {"PENDIENTE", "SUSPENDIDA"})
    void ongNoHabilitadaNoInformaCapacidadesAunqueTengaCertificacionVigente(EstadoHabilitacion estado)
            throws Exception {
        certificar(null);
        ong.setEstadoHabilitacion(estado);
        ongRepository.save(ong);

        mockMvc.perform(validar(ong.getCuit(), TipoRecurso.ALIMENTOS).cookie(bonita))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoHabilitacion").value(estado.name()))
                .andExpect(jsonPath("$.capacidadesHabilitadas").isEmpty())
                .andExpect(jsonPath("$.observaciones").value(containsString("no está habilitada")));
    }

    @Test
    void cuitNoRegistradoDevuelveCapacidadesVaciasConElMotivo() throws Exception {
        mockMvc.perform(validar("30799999999", TipoRecurso.ALIMENTOS).cookie(bonita))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cuit").value("30799999999"))
                .andExpect(jsonPath("$.tipoRecurso").value("ALIMENTOS"))
                .andExpect(jsonPath("$.estadoHabilitacion").value(nullValue()))
                .andExpect(jsonPath("$.capacidadesHabilitadas").isEmpty())
                .andExpect(jsonPath("$.observaciones").value(containsString("No hay una ONG registrada")));
    }

    @Test
    void requiereAutenticacionYElRepresentanteNoPuedeValidarNiSuPropiaOng() throws Exception {
        mockMvc.perform(validar(ong.getCuit(), TipoRecurso.ALIMENTOS))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value(URL));

        usuarioRepository.save(new Usuario("representante", passwordEncoder.encode(PASSWORD), Rol.ONG, ong));
        mockMvc.perform(validar(ong.getCuit(), TipoRecurso.ALIMENTOS).cookie(login("representante")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.path").value(URL));
    }

    @Test
    void elAdminTambienPuedeValidarCertificaciones() throws Exception {
        certificar(null);
        usuarioRepository.save(new Usuario("admin", passwordEncoder.encode(PASSWORD), Rol.ADMIN, null));

        mockMvc.perform(validar(ong.getCuit(), TipoRecurso.ALIMENTOS).cookie(login("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacidadesHabilitadas.length()").value(1));
    }

    @Test
    void exigeCuitYTipoDeRecursoConElFormatoApiError() throws Exception {
        mockMvc.perform(post(URL).cookie(bonita).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value(URL))
                .andExpect(jsonPath("$.fieldErrors.cuit").exists())
                .andExpect(jsonPath("$.fieldErrors.tipoRecurso").exists());

        for (String cuit : List.of("", "123", "30-70000000-1", "3070000000a", "307000000001")) {
            mockMvc.perform(validar(cuit, TipoRecurso.ALIMENTOS).cookie(bonita))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.cuit").exists());
        }
    }

    @Test
    void rechazaTiposDesconocidosNumericosYJsonMalformado() throws Exception {
        for (String body : List.of(
                "{\"cuit\":\"30700000001\",\"tipoRecurso\":\"DESCONOCIDO\"}",
                "{\"cuit\":\"30700000001\",\"tipoRecurso\":0}",
                "{\"cuit\":")) {
            mockMvc.perform(post(URL).cookie(bonita).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.path").value(URL));
        }
    }

    @Test
    void validarEsSoloLecturaYNoComprometeRecursos() throws Exception {
        Certificacion certificacion = certificar(null);
        certificacion = certificacionRepository.findById(certificacion.getId()).orElseThrow();
        Recurso recurso = new Recurso(ong, TipoRecurso.ALIMENTOS, "kg", 100);
        recurso.comprometer(30);
        recurso = recursoRepository.save(recurso);

        mockMvc.perform(validar(ong.getCuit(), TipoRecurso.ALIMENTOS).cookie(bonita))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacidadesHabilitadas.length()").value(1));

        Recurso guardado = recursoRepository.findById(recurso.getId()).orElseThrow();
        assertThat(guardado.getCantidadTotal()).isEqualTo(100);
        assertThat(guardado.getCantidadComprometida()).isEqualTo(30);
        assertThat(compromisoRepository.count()).isZero();
        assertThat(certificacionRepository.findById(certificacion.getId()).orElseThrow().getUpdatedAt())
                .isEqualTo(certificacion.getUpdatedAt());
    }

    private Certificacion certificar(LocalDate vencimiento) {
        return certificacionRepository.save(new Certificacion(ong, TipoRecurso.ALIMENTOS,
                NivelCompetencia.INTERMEDIO, NivelGravedad.MEDIO, vencimiento));
    }

    private MockHttpServletRequestBuilder validar(String cuit, TipoRecurso tipo) throws Exception {
        return post(URL).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("cuit", cuit, "tipoRecurso", tipo)));
    }

    private Cookie login(String username) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", username, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("access_token");
    }

    private static LocalDate hoy() {
        return LocalDate.now(ZONA);
    }
}
