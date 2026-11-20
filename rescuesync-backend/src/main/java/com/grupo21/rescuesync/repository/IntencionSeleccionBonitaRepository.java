package com.grupo21.rescuesync.repository;

import com.grupo21.rescuesync.model.EstadoIntencionSeleccionBonita;
import com.grupo21.rescuesync.model.IntencionSeleccionBonita;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IntencionSeleccionBonitaRepository extends JpaRepository<IntencionSeleccionBonita, Long> {
    @EntityGraph(attributePaths = {"oferta", "oferta.ong", "oferta.lote", "oferta.lote.emergencia"})
    List<IntencionSeleccionBonita> findByOferta_Lote_Emergencia_IdOrderByOferta_IdAsc(Long emergenciaId);

    /** Reanudar trabajo pendiente después de reiniciar; incluye envíos de resultado incierto. */
    @EntityGraph(attributePaths = {"oferta", "oferta.lote", "oferta.lote.emergencia"})
    List<IntencionSeleccionBonita> findByEstadoInOrderByIdAsc(Collection<EstadoIntencionSeleccionBonita> estados);

    Optional<IntencionSeleccionBonita> findByOfertaId(Long ofertaId);

    @Query("select distinct e.id from IntencionSeleccionBonita i join i.oferta o join o.lote l "
            + "join l.emergencia e where ((i.estado in :estados and not exists (select k.id from IntencionSeleccionBonita k "
            + "where k.oferta.lote.emergencia = e and k.estado = 'EN_CICLO')) or (i.estado = 'EN_CICLO' and exists ("
            + "select n.id from Notificacion n where n.oferta = o and n.tipo = 'OFERTA_SELECCIONADA' "
            + "and (n.notificadaEn is null or (n.recibidaEn is null and exists (select d.id from NotificacionUsuario d "
            + "where d.notificacion = n and d.fechaLectura is not null))))) or exists ("
            + "select p.id from Notificacion p where p.oferta = o and p.bonitaPendiente = true)) and not exists ("
            + "select j.id from IntencionSeleccionBonita j where j.oferta.lote.emergencia = e "
            + "and j.estado = 'INCIDENTE') order by e.id")
    List<Long> buscarEmergenciasRecuperables(@Param("estados") Collection<EstadoIntencionSeleccionBonita> estados,
                                            Pageable pagina);
}
