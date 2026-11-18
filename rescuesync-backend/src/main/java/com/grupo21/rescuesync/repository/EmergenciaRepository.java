package com.grupo21.rescuesync.repository;

import com.grupo21.rescuesync.dto.indicadores.CasoBonitaRef;
import com.grupo21.rescuesync.model.Emergencia;
import com.grupo21.rescuesync.model.EstadoEmergencia;
import com.grupo21.rescuesync.model.TipoDesastre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface EmergenciaRepository extends JpaRepository<Emergencia, Long> {

    List<Emergencia> findByEstado(EstadoEmergencia estado);

    List<Emergencia> findByMunicipioId(Long municipioId);

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
