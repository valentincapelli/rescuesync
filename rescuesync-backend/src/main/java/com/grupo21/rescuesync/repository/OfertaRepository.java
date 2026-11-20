package com.grupo21.rescuesync.repository;

import com.grupo21.rescuesync.model.EstadoOferta;
import com.grupo21.rescuesync.model.Oferta;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OfertaRepository extends JpaRepository<Oferta, Long> {

    List<Oferta> findByLoteId(Long loteId);

    List<Oferta> findByLoteIdAndEstado(Long loteId, EstadoOferta estado);

    List<Oferta> findByOngId(Long OngId);

    List<Oferta> findByOngIdAndLoteId(Long OngId, Long loteId);

    List<Oferta> findByLote_Emergencia_Municipio_IdAndEstado(
            Long municipioId,
            EstadoOferta estado
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT o
        FROM Oferta o
        WHERE o.lote.emergencia.id = :emergenciaId
        AND o.lote.emergencia.municipio.id = :municipioId
        AND o.estado = :estado
    """)
    List<Oferta> buscarConBloqueoPorEmergenciaYMunicipioYEstado(
            @Param("emergenciaId") Long emergenciaId,
            @Param("municipioId") Long municipioId,
            @Param("estado") EstadoOferta estado
    );
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Oferta o where o.lote.emergencia.id = :emergenciaId and o.estado = :estado order by o.id")
    List<Oferta> buscarConBloqueoPorEmergenciaYEstado(
            @Param("emergenciaId") Long emergenciaId,
            @Param("estado") EstadoOferta estado
    );

    @Query("select o from Oferta o join fetch o.ong join o.lote l "
            + "where l.emergencia.id = :emergenciaId order by l.id, o.id")
    List<Oferta> findConOngByEmergenciaId(@Param("emergenciaId") Long emergenciaId);
}
