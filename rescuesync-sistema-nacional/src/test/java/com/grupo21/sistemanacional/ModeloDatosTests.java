package com.grupo21.sistemanacional;

import com.grupo21.sistemanacional.config.JpaConfig;
import com.grupo21.sistemanacional.exception.StockInsuficienteException;
import com.grupo21.sistemanacional.model.Certificacion;
import com.grupo21.sistemanacional.model.Compromiso;
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
import com.grupo21.sistemanacional.repository.DisponibilidadView;
import com.grupo21.sistemanacional.repository.OngRepository;
import com.grupo21.sistemanacional.repository.RecursoRepository;
import com.grupo21.sistemanacional.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifica que las restricciones del modelo de E3-01 existan de verdad en la base
 * (unique, CHECK) y que las consultas de los repositorios funcionen.
 */
@DataJpaTest
@Import(JpaConfig.class)
class ModeloDatosTests {

    @Autowired private OngRepository ongRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private CertificacionRepository certificacionRepository;
    @Autowired private RecursoRepository recursoRepository;
    @Autowired private CompromisoRepository compromisoRepository;
    @Autowired private EntityManager em;

    private Ong ong;

    @BeforeEach
    void setUp() {
        ong = new Ong("30712345678", "Cruz Solidaria");
        ong.setEstadoHabilitacion(EstadoHabilitacion.HABILITADA);
        ong = ongRepository.saveAndFlush(ong);
    }

    @Test
    void auditoriaCompletaFechas() {
        assertThat(ong.getId()).isNotNull();
        assertThat(ong.getCreatedAt()).isNotNull();
        assertThat(ong.getUpdatedAt()).isNotNull();
    }

    @Test
    void cuitEsUnico() {
        assertThatThrownBy(() -> ongRepository.saveAndFlush(new Ong("30712345678", "Otra")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void usuarioOngSinOngNoSePuedeGuardar() {
        assertThatThrownBy(() -> usuarioRepository.saveAndFlush(new Usuario("ong1", "hash", Rol.ONG, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void usuarioServicioNoNecesitaOng() {
        Usuario bonita = usuarioRepository.saveAndFlush(new Usuario("bonita", "hash", Rol.SERVICIO, null));
        assertThat(usuarioRepository.findByUsername("bonita")).contains(bonita);
    }

    @Test
    void unaCertificacionPorOngYTipo() {
        certificacionRepository.saveAndFlush(new Certificacion(ong, TipoRecurso.ALIMENTOS,
                NivelCompetencia.BASICO, NivelGravedad.MEDIO, null));
        assertThatThrownBy(() -> certificacionRepository.saveAndFlush(new Certificacion(ong, TipoRecurso.ALIMENTOS,
                NivelCompetencia.AVANZADO, NivelGravedad.ALTO, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void certificacionVencidaNoEsVigente() {
        LocalDate hoy = LocalDate.of(2026, 10, 16);
        certificacionRepository.saveAndFlush(new Certificacion(ong, TipoRecurso.MEDICAMENTOS,
                NivelCompetencia.AVANZADO, NivelGravedad.CRITICO, hoy.minusDays(1)));
        certificacionRepository.saveAndFlush(new Certificacion(ong, TipoRecurso.AGUA_POTABLE,
                NivelCompetencia.INTERMEDIO, NivelGravedad.ALTO, hoy));

        assertThat(certificacionRepository.findVigente(ong.getCuit(), TipoRecurso.MEDICAMENTOS, hoy)).isEmpty();
        assertThat(certificacionRepository.findVigente(ong.getCuit(), TipoRecurso.AGUA_POTABLE, hoy))
                .hasValueSatisfying(c -> assertThat(c.getNivelRiesgoMax().cubre(NivelGravedad.ALTO)).isTrue());
    }

    @Test
    void unRecursoPorOngTipoYUnidad() {
        recursoRepository.saveAndFlush(new Recurso(ong, TipoRecurso.ALIMENTOS, "raciones", 100));
        assertThatThrownBy(() -> recursoRepository.saveAndFlush(
                new Recurso(ong, TipoRecurso.ALIMENTOS, "raciones", 50)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void checkImpideBajarElTotalPorDebajoDeLoComprometido() {
        Recurso recurso = new Recurso(ong, TipoRecurso.ALIMENTOS, "raciones", 100);
        recurso.comprometer(80);
        recursoRepository.saveAndFlush(recurso);

        recurso.setCantidadTotal(50);
        assertThatThrownBy(() -> recursoRepository.saveAndFlush(recurso))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void comprometerMasDeLoDisponibleFalla() {
        Recurso recurso = new Recurso(ong, TipoRecurso.AGUA_POTABLE, "litros", 10);
        recurso.comprometer(7);
        assertThat(recurso.getDisponible()).isEqualTo(3);

        assertThatThrownBy(() -> recurso.comprometer(4))
                .isInstanceOf(StockInsuficienteException.class)
                .hasMessageContaining("hay 3 disponibles");

        recurso.liberar(7);
        assertThat(recurso.getDisponible()).isEqualTo(10);
    }

    @Test
    void bloqueoPesimistaDevuelveRecursosOrdenadosPorId() {
        Recurso a = recursoRepository.save(new Recurso(ong, TipoRecurso.ALIMENTOS, "raciones", 10));
        Recurso b = recursoRepository.save(new Recurso(ong, TipoRecurso.TRANSPORTE, "vehiculos", 2));
        recursoRepository.flush();

        List<Recurso> bloqueados = recursoRepository.findAllByIdForUpdate(List.of(b.getId(), a.getId()));
        assertThat(bloqueados).extracting(Recurso::getId).containsExactly(a.getId(), b.getId());
        assertThat(recursoRepository.findByIdForUpdate(a.getId())).isPresent();
    }

    @Test
    void compromisoEsUnicoPorRecursoYOferta() {
        Recurso recurso = recursoRepository.saveAndFlush(new Recurso(ong, TipoRecurso.ALIMENTOS, "raciones", 10));
        compromisoRepository.saveAndFlush(new Compromiso(recurso, 1L, 42L, 5));

        assertThat(compromisoRepository.findByRecursoIdAndOfertaRef(recurso.getId(), 42L)).isPresent();
        assertThatThrownBy(() -> compromisoRepository.saveAndFlush(new Compromiso(recurso, 1L, 42L, 5)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void compromisoConCantidadCeroNoSePuedeGuardar() {
        Recurso recurso = recursoRepository.saveAndFlush(new Recurso(ong, TipoRecurso.ALIMENTOS, "raciones", 10));
        assertThatThrownBy(() -> compromisoRepository.saveAndFlush(new Compromiso(recurso, 1L, 43L, 0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void disponibilidadGlobalSoloSumaOngsHabilitadas() {
        Ong pendiente = ongRepository.save(new Ong("30798765432", "ONG Pendiente"));

        Recurso r1 = new Recurso(ong, TipoRecurso.ALIMENTOS, "raciones", 100);
        r1.comprometer(30);
        recursoRepository.save(r1);
        recursoRepository.save(new Recurso(pendiente, TipoRecurso.ALIMENTOS, "raciones", 500));
        recursoRepository.flush();
        em.clear();

        assertThat(recursoRepository.disponibilidadGlobal())
                .containsExactly(new DisponibilidadView(TipoRecurso.ALIMENTOS, "raciones", 70L));
    }
}
