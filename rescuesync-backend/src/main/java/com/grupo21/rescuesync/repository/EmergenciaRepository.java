package com.grupo21.rescuesync.repository;

import com.grupo21.rescuesync.dto.indicadores.CasoBonitaRef;
import com.grupo21.rescuesync.model.Emergencia;
import com.grupo21.rescuesync.model.EstadoEmergencia;
import com.grupo21.rescuesync.model.TipoDesastre;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EmergenciaRepository extends JpaRepository<Emergencia, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Emergencia e where e.id = :id")
    Optional<Emergencia> buscarConBloqueo(@Param("id") Long id);

    @Query("select e.id from Emergencia e where e.bonitaCaseId is not null and e.estado <> 'CERRADA' order by e.id")
    List<Long> buscarIdsParaSeguimiento();

    List<Emergencia> findByEstado(EstadoEmergencia estado);

    List<Emergencia> findByMunicipioId(Long municipioId);

    boolean existsByIdAndMunicipio_Id(
            Long emergenciaId,
            Long municipioId
    );

    Optional<Emergencia> findByIdAndMunicipio_Id(
            Long emergenciaId,
            Long municipioId
    );
  
    /**
     * Emergencias con instancia en Bonita registradas en [inicio, fin), para los tiempos por etapa (E4).
     * municipioId y tipoDesastre son opcionales (null = sin filtrar).
     */
    @Query("select new com.grupo21.rescuesync.dto.indicadores.CasoBonitaRef(e.id, e.bonitaCaseId) "
            + "from Emergencia e "
            + "where e.bonitaCaseId is not null "
            + "and e.createdAt >= :inicio and e.createdAt < :fin "
            + "and (:municipioId is null or e.municipio.id = :municipioId) "
            + "and (:tipoDesastre is null or e.tipoDesastre = :tipoDesastre) "
            + "order by e.id")
    List<CasoBonitaRef> buscarCasosBonita(@Param("inicio") Instant inicio,
                                          @Param("fin") Instant fin,
                                          @Param("municipioId") Long municipioId,
                                          @Param("tipoDesastre") TipoDesastre tipoDesastre);
}
