package com.grupo21.rescuesync;

import com.grupo21.rescuesync.dto.ofertas.OfertasEvaluadasEmergenciaResponse;
import com.grupo21.rescuesync.dto.ofertas.SeleccionarOfertasRequest;
import com.grupo21.rescuesync.exception.BusinessException;
import com.grupo21.rescuesync.model.Emergencia;
import com.grupo21.rescuesync.model.EstadoEmergencia;
import com.grupo21.rescuesync.model.EstadoLote;
import com.grupo21.rescuesync.model.EstadoOferta;
import com.grupo21.rescuesync.model.Lote;
import com.grupo21.rescuesync.model.Municipio;
import com.grupo21.rescuesync.model.Oferta;
import com.grupo21.rescuesync.model.Ong;
import com.grupo21.rescuesync.model.TipoDesastre;
import com.grupo21.rescuesync.model.TipoRecurso;
import com.grupo21.rescuesync.model.TipoNotificacion;
import com.grupo21.rescuesync.model.Usuario;
import com.grupo21.rescuesync.repository.EmergenciaRepository;
import com.grupo21.rescuesync.repository.LoteRepository;
import com.grupo21.rescuesync.repository.OfertaRepository;
import com.grupo21.rescuesync.repository.UsuarioRepository;
import com.grupo21.rescuesync.service.HistorialEstadoService;
import com.grupo21.rescuesync.service.NotificacionService;
import com.grupo21.rescuesync.service.OfertaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfertaServiceTests {

    private OfertaRepository ofertaRepository;
    private UsuarioRepository usuarioRepository;
    private EmergenciaRepository emergenciaRepository;
    private NotificacionService notificacionService;
    private OfertaService ofertaService;

    @BeforeEach
    void preparar() {
        ofertaRepository = mock(OfertaRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        emergenciaRepository = mock(EmergenciaRepository.class);
        notificacionService = mock(NotificacionService.class);
        ofertaService = new OfertaService(
                ofertaRepository,
                mock(LoteRepository.class),
                usuarioRepository,
                emergenciaRepository,
                notificacionService,
                mock(HistorialEstadoService.class)
        );
    }

    @Test
    void ofertasEvaluadasSeAgrupanPorEmergenciaYLoteYFiltranEstadosNoElegibles() {
        Municipio municipio = mock(Municipio.class);
        when(municipio.getId()).thenReturn(21L);
        when(municipio.getNombre()).thenReturn("Municipio de prueba");
        Usuario usuario = mock(Usuario.class);
        when(usuario.getMunicipio()).thenReturn(municipio);
        when(usuarioRepository.findByEmail("operador@test.com"))
                .thenReturn(Optional.of(usuario));

        Emergencia coberturaEvaluada = emergencia(100L, municipio, EstadoEmergencia.COBERTURA_EVALUADA);
        Emergencia coberturaParcial = emergencia(200L, municipio, EstadoEmergencia.COBERTURA_PARCIAL);
        Emergencia adjudicada = emergencia(300L, municipio, EstadoEmergencia.ADJUDICADA);
        List<Oferta> ofertas = List.of(
                oferta(1L, 10L, coberturaEvaluada),
                oferta(2L, 10L, coberturaEvaluada),
                oferta(3L, 20L, coberturaParcial),
                oferta(4L, 30L, adjudicada)
        );
        when(ofertaRepository.findByLote_Emergencia_Municipio_IdAndEstado(
                21L,
                EstadoOferta.EVALUADA
        )).thenReturn(ofertas);

        List<OfertasEvaluadasEmergenciaResponse> resultado =
                ofertaService.obtenerOfertasEvaluadasDelMunicipio("operador@test.com");

        assertThat(resultado).hasSize(2);
        assertThat(resultado).extracting(OfertasEvaluadasEmergenciaResponse::id)
                .containsExactly(100L, 200L);
        assertThat(resultado.get(0).lotes()).hasSize(1);
        assertThat(resultado.get(0).lotes().get(0).id()).isEqualTo(10L);
        assertThat(resultado.get(0).lotes().get(0).ofertas()).hasSize(2);
        assertThat(resultado.get(1).lotes()).hasSize(1);
        assertThat(resultado.get(1).lotes().get(0).ofertas()).hasSize(1);
    }

    @Test
    void seleccionRechazaEmergenciasFueraDeCoberturaEvaluadaOParcial() {
        Municipio municipio = mock(Municipio.class);
        when(municipio.getId()).thenReturn(21L);
        Usuario usuario = mock(Usuario.class);
        when(usuario.getMunicipio()).thenReturn(municipio);
        when(usuarioRepository.findByEmail("operador@test.com"))
                .thenReturn(Optional.of(usuario));
        Emergencia emergencia = mock(Emergencia.class);
        when(emergencia.getEstado()).thenReturn(EstadoEmergencia.CONVOCATORIA_PUBLICADA);
        when(emergenciaRepository.findByIdAndMunicipio_Id(100L, 21L))
                .thenReturn(Optional.of(emergencia));

        assertThatThrownBy(() -> ofertaService.seleccionarOfertas(
                "operador@test.com",
                new SeleccionarOfertasRequest(100L, Set.of(1L))
        )).isInstanceOf(BusinessException.class)
                .hasMessage("La emergencia no está disponible para seleccionar ofertas");

        verify(ofertaRepository, never())
                .buscarConBloqueoPorEmergenciaYMunicipioYEstado(
                        100L,
                        21L,
                        EstadoOferta.EVALUADA
                );
    }

    @Test
    void notificacionIncluyeMunicipioEmergenciaLoteYCantidadOfrecida() {
        Municipio municipio = mock(Municipio.class);
        when(municipio.getId()).thenReturn(21L);
        when(municipio.getNombre()).thenReturn("Municipio de prueba");
        Usuario usuario = mock(Usuario.class);
        when(usuario.getMunicipio()).thenReturn(municipio);
        when(usuarioRepository.findByEmail("operador@test.com"))
                .thenReturn(Optional.of(usuario));

        Emergencia emergencia = emergencia(
                100L,
                municipio,
                EstadoEmergencia.COBERTURA_PARCIAL
        );
        Lote lote = mock(Lote.class);
        when(lote.getId()).thenReturn(10L);
        when(lote.getEmergencia()).thenReturn(emergencia);
        when(lote.getTipoRecurso()).thenReturn(TipoRecurso.AGUA_POTABLE);
        when(lote.getDescripcion()).thenReturn("Agua para el refugio central");
        when(lote.getCantidadRequerida()).thenReturn(200);
        when(lote.getUnidadMedida()).thenReturn("litros");
        Ong ong = mock(Ong.class);
        Oferta oferta = mock(Oferta.class);
        when(oferta.getId()).thenReturn(55L);
        when(oferta.getLote()).thenReturn(lote);
        when(oferta.getOng()).thenReturn(ong);
        when(oferta.getCantidadOfrecida()).thenReturn(75);
        Ong ongRechazada = mock(Ong.class);
        Oferta ofertaRechazada = mock(Oferta.class);
        when(ofertaRechazada.getId()).thenReturn(56L);
        when(ofertaRechazada.getLote()).thenReturn(lote);
        when(ofertaRechazada.getOng()).thenReturn(ongRechazada);
        when(ofertaRechazada.getCantidadOfrecida()).thenReturn(40);
        when(ofertaRepository.buscarConBloqueoPorEmergenciaYMunicipioYEstado(
                100L,
                21L,
                EstadoOferta.EVALUADA
        )).thenReturn(List.of(oferta, ofertaRechazada));
        when(emergenciaRepository.findByIdAndMunicipio_Id(100L, 21L))
                .thenReturn(Optional.of(emergencia));

        ofertaService.seleccionarOfertas(
                "operador@test.com",
                new SeleccionarOfertasRequest(100L, Set.of(55L))
        );

        ArgumentCaptor<String> mensaje = ArgumentCaptor.forClass(String.class);
        verify(notificacionService, times(2)).crearNotificacion(
                any(Ong.class),
                anyString(),
                mensaje.capture(),
                any(TipoNotificacion.class)
        );
        assertThat(mensaje.getAllValues()).anySatisfy(texto ->
                assertThat(texto)
                        .contains("Tu oferta #55 fue seleccionada")
                        .contains("Municipio de prueba", "Emergencia #100", "Lote #10")
                        .contains("Agua para el refugio central", "200 litros", "75 litros")
        );
        assertThat(mensaje.getAllValues()).anySatisfy(texto ->
                assertThat(texto)
                        .contains("Tu oferta #56 no fue seleccionada")
                        .contains("Municipio de prueba", "Agua para el refugio central", "40 litros")
        );
    }

    private Emergencia emergencia(
            Long id,
            Municipio municipio,
            EstadoEmergencia estado
    ) {
        Emergencia emergencia = mock(Emergencia.class);
        when(emergencia.getId()).thenReturn(id);
        when(emergencia.getEstado()).thenReturn(estado);
        when(emergencia.getMunicipio()).thenReturn(municipio);
        when(emergencia.getTipoDesastre()).thenReturn(TipoDesastre.INUNDACION);
        when(emergencia.getZonaAfectada()).thenReturn("Zona afectada");
        when(emergencia.getDescripcion()).thenReturn("Descripción");
        return emergencia;
    }

    private Oferta oferta(Long id, Long loteId, Emergencia emergencia) {
        Lote lote = mock(Lote.class);
        when(lote.getId()).thenReturn(loteId);
        when(lote.getEmergencia()).thenReturn(emergencia);
        when(lote.getTipoRecurso()).thenReturn(TipoRecurso.AGUA_POTABLE);
        when(lote.getDescripcion()).thenReturn("Agua potable");
        when(lote.getCantidadRequerida()).thenReturn(100);
        when(lote.getUnidadMedida()).thenReturn("litros");
        when(lote.getEstado()).thenReturn(EstadoLote.CUBIERTO);

        Ong ong = mock(Ong.class);
        when(ong.getNombre()).thenReturn("ONG de prueba");

        Oferta oferta = mock(Oferta.class);
        when(oferta.getId()).thenReturn(id);
        when(oferta.getLote()).thenReturn(lote);
        when(oferta.getOng()).thenReturn(ong);
        when(oferta.getCantidadOfrecida()).thenReturn(50);
        when(oferta.getEstado()).thenReturn(EstadoOferta.EVALUADA);
        return oferta;
    }
}
