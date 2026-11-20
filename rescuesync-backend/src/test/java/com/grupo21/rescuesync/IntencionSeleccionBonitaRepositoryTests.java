package com.grupo21.rescuesync;

import com.grupo21.rescuesync.config.JpaConfig;
import com.grupo21.rescuesync.model.*;
import com.grupo21.rescuesync.repository.IntencionSeleccionBonitaRepository;
import com.grupo21.rescuesync.service.PlanificadorSeleccionBonita;
import com.grupo21.rescuesync.service.SeleccionBonitaRegistro;
import com.grupo21.rescuesync.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Set;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({JpaConfig.class, PlanificadorSeleccionBonita.class, SeleccionBonitaRegistro.class})
class IntencionSeleccionBonitaRepositoryTests {
    @Autowired TestEntityManager em;
    @Autowired IntencionSeleccionBonitaRepository repo;
    @Autowired SeleccionBonitaRegistro registro;

    @Test
    void recuperaIntencionesTrasLimpiarElContextoConTaskIdYOrdenEstable() {
        Oferta primera = oferta(1);
        Oferta segunda = ofertaEnLote(primera.getLote(), 2);
        IntencionSeleccionBonita enviada = intencion(segunda, EstadoIntencionSeleccionBonita.DESPACHANDO);
        enviada.setBonitaTaskId("task-22");
        enviada.setBonitaCaseId(700L);
        enviada.setContinuarSeleccionandoInput(false);
        repo.saveAndFlush(enviada);
        repo.saveAndFlush(intencion(primera, EstadoIntencionSeleccionBonita.LIBERADA));
        Long emergenciaId = primera.getLote().getEmergencia().getId();
        em.clear();

        var cola = repo.findByOferta_Lote_Emergencia_IdOrderByOferta_IdAsc(emergenciaId);
        assertThat(cola).extracting(i -> i.getOferta().getId())
                .containsExactly(primera.getId(), segunda.getId());
        var recuperables = repo.findByEstadoInOrderByIdAsc(List.of(
                EstadoIntencionSeleccionBonita.PENDIENTE,
                EstadoIntencionSeleccionBonita.DESPACHANDO,
                EstadoIntencionSeleccionBonita.EN_CICLO,
                EstadoIntencionSeleccionBonita.INCIDENTE));
        assertThat(recuperables).hasSize(1);
        assertThat(recuperables.getFirst().getBonitaTaskId()).isEqualTo("task-22");
        assertThat(recuperables.getFirst().getContinuarSeleccionandoInput()).isFalse();
    }

    @Test
    void unaOfertaSoloPuedeTenerUnaIntencion() {
        Oferta oferta = oferta(1);
        repo.saveAndFlush(intencion(oferta, EstadoIntencionSeleccionBonita.PENDIENTE));
        em.clear();
        assertThatThrownBy(() -> repo.saveAndFlush(intencion(oferta, EstadoIntencionSeleccionBonita.PENDIENTE)))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void unTaskIdNoPuedeReservarseParaDosOfertas() {
        Oferta primera = oferta(1);
        Oferta segunda = ofertaEnLote(primera.getLote(), 2);
        IntencionSeleccionBonita a = intencion(primera, EstadoIntencionSeleccionBonita.DESPACHANDO);
        a.setBonitaTaskId("task-27");
        repo.saveAndFlush(a);
        IntencionSeleccionBonita b = intencion(segunda, EstadoIntencionSeleccionBonita.DESPACHANDO);
        b.setBonitaTaskId("task-27");
        assertThatThrownBy(() -> repo.saveAndFlush(b)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void finalizacionSinLiberacionNacionalNoHabilitaSiguienteOferta() {
        Oferta oferta = oferta(1);
        IntencionSeleccionBonita i = intencion(oferta, EstadoIntencionSeleccionBonita.EN_CICLO);
        repo.saveAndFlush(i);
        Despliegue d = despliegue(oferta);
        d.setFinalizadoEn(Instant.now());
        em.persistAndFlush(d);
        assertThatThrownBy(() -> registro.liberacionesConfirmadas(
                oferta.getLote().getEmergencia().getId(), 700L, Set.of(oferta.getId())))
                .isInstanceOf(BusinessException.class);
        assertThat(i.getEstado()).isEqualTo(EstadoIntencionSeleccionBonita.EN_CICLO);
    }

    @Test
    void liberacionNacionalConfirmadaMarcaLaIntencionLiberada() {
        Oferta oferta = oferta(1);
        IntencionSeleccionBonita i = intencion(oferta, EstadoIntencionSeleccionBonita.EN_CICLO);
        repo.saveAndFlush(i);
        Despliegue d = despliegue(oferta);
        d.setLiberadoEn(Instant.now());
        em.persistAndFlush(d);
        registro.liberacionesConfirmadas(oferta.getLote().getEmergencia().getId(), 700L, Set.of(oferta.getId()));
        assertThat(i.getEstado()).isEqualTo(EstadoIntencionSeleccionBonita.LIBERADA);
    }

    @Test
    void recuperacionIgnoraIncidentesPermanentesYOfertasEnCiclo() {
        Oferta a = oferta(1);
        Oferta b = oferta(2);
        repo.saveAndFlush(intencion(a, EstadoIntencionSeleccionBonita.INCIDENTE));
        repo.saveAndFlush(intencion(b, EstadoIntencionSeleccionBonita.PENDIENTE));
        assertThat(registro.recuperables(10))
                .containsExactly(b.getLote().getEmergencia().getId());
    }

    @Test
    void agotadosLosIntentosNoSeReejecutaLaTareaReservada() {
        Oferta oferta = oferta(1);
        IntencionSeleccionBonita i = intencion(oferta, EstadoIntencionSeleccionBonita.DESPACHANDO);
        i.setBonitaCaseId(700L);
        i.setBonitaTaskId("task-700");
        i.setContinuarSeleccionandoInput(false);
        i.setUltimoIntentoEn(Instant.now().minusSeconds(60));
        i.setIntentos(3);
        repo.saveAndFlush(i);
        var anterior = new SeleccionBonitaRegistro.Envio(oferta.getId(), 700L, "task-700", false,
                i.getUltimoIntentoEn());
        assertThat(registro.renovar(oferta.getLote().getEmergencia().getId(), anterior)).isNull();
        assertThat(i.getEstado()).isEqualTo(EstadoIntencionSeleccionBonita.INCIDENTE);
    }

    @Test
    void dosAdjudicadasAvanzanEnOrdenSoloTrasLiberacionYRechazadaQuedaFuera() {
        Oferta primera = oferta(1);
        Oferta segunda = ofertaEnLote(primera.getLote(), 2);
        Oferta rechazada = ofertaEnLote(primera.getLote(), 3);
        rechazada.setEstado(EstadoOferta.RECHAZADA);
        em.flush();
        repo.saveAndFlush(intencion(segunda, EstadoIntencionSeleccionBonita.PENDIENTE));
        repo.saveAndFlush(intencion(primera, EstadoIntencionSeleccionBonita.PENDIENTE));
        Long emergenciaId = primera.getLote().getEmergencia().getId();

        var envio = registro.reservar(emergenciaId, 700L, "task-1");
        assertThat(envio.ofertaId()).isEqualTo(primera.getId());
        assertThat(envio.continuar()).isTrue();
        assertThat(registro.reservar(emergenciaId, 700L, "task-2")).isNull();
        registro.confirmar(emergenciaId, envio);
        assertThat(registro.reservar(emergenciaId, 700L, "task-2")).isNull();

        Despliegue d = despliegue(primera);
        d.setLiberadoEn(Instant.now());
        em.persistAndFlush(d);
        registro.liberacionesConfirmadas(emergenciaId, 700L, Set.of(primera.getId()));
        var siguiente = registro.reservar(emergenciaId, 700L, "task-2");
        assertThat(siguiente.ofertaId()).isEqualTo(segunda.getId());
        assertThat(siguiente.continuar()).isFalse();
        assertThat(repo.findByOfertaId(rechazada.getId())).isEmpty();
    }

    private Despliegue despliegue(Oferta oferta) {
        Despliegue d = new Despliegue();
        d.setOferta(oferta);
        d.setBonitaCaseId(700L);
        d.setComprometidoEn(Instant.now());
        return d;
    }

    private IntencionSeleccionBonita intencion(Oferta oferta, EstadoIntencionSeleccionBonita estado) {
        IntencionSeleccionBonita i = new IntencionSeleccionBonita();
        i.setOferta(oferta);
        i.setEstado(estado);
        return i;
    }

    private Oferta oferta(int numero) {
        Municipio municipio = new Municipio();
        municipio.setNombre("Municipio " + numero);
        em.persist(municipio);
        Emergencia emergencia = new Emergencia();
        emergencia.setMunicipio(municipio);
        emergencia.setTipoDesastre(TipoDesastre.INUNDACION);
        emergencia.setNivelGravedad(NivelGravedad.ALTO);
        emergencia.setZonaAfectada("Zona");
        emergencia.setDescripcion("Descripción");
        emergencia.setBonitaCaseId(700L);
        em.persist(emergencia);
        Lote lote = new Lote();
        lote.setEmergencia(emergencia);
        lote.setTipoRecurso(TipoRecurso.AGUA_POTABLE);
        lote.setDescripcion("Agua");
        lote.setCantidadRequerida(10);
        lote.setUnidadMedida("litros");
        em.persist(lote);
        return ofertaEnLote(lote, numero);
    }

    private Oferta ofertaEnLote(Lote lote, int numero) {
        Ong ong = new Ong();
        ong.setNombre("ONG " + numero);
        em.persist(ong);
        Oferta oferta = new Oferta();
        oferta.setLote(lote);
        oferta.setOng(ong);
        oferta.setCantidadOfrecida(5);
        oferta.setEstado(EstadoOferta.SELECCIONADA);
        return em.persistAndFlush(oferta);
    }
}
