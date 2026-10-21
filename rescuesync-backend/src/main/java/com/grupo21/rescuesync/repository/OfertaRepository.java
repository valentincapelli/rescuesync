package com.grupo21.rescuesync.repository;

import com.grupo21.rescuesync.model.EstadoOferta;
import com.grupo21.rescuesync.model.Oferta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OfertaRepository extends JpaRepository<Oferta, Long> {

    List<Oferta> findByLoteId(Long loteId);

    List<Oferta> findByLoteIdAndEstado(Long loteId, EstadoOferta estado);

    List<Oferta> findByOngId(Long OngId);

    List<Oferta> findByOngIdAndLoteId(Long OngId, Long loteId);

    @Query("select o from Oferta o join fetch o.ong join o.lote l "
            + "where l.emergencia.id = :emergenciaId order by l.id, o.id")
    List<Oferta> findConOngByEmergenciaId(@Param("emergenciaId") Long emergenciaId);
}
