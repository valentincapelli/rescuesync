package com.grupo21.rescuesync.repository;

import com.grupo21.rescuesync.model.NovedadDespliegue;
import com.grupo21.rescuesync.model.TipoNovedadDespliegue;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface NovedadDespliegueRepository extends JpaRepository<NovedadDespliegue, Long> {
    List<NovedadDespliegue> findByDespliegueIdOrderById(Long despliegueId);
    Optional<NovedadDespliegue> findByDespliegueIdAndTipo(Long despliegueId, TipoNovedadDespliegue tipo);
    List<NovedadDespliegue> findByDespliegue_Oferta_Lote_Emergencia_IdAndConfirmadaEnIsNull(Long emergenciaId);
}
