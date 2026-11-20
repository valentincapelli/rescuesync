package com.grupo21.rescuesync.repository;

import com.grupo21.rescuesync.model.Despliegue;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DespliegueRepository extends JpaRepository<Despliegue, Long> {
    Optional<Despliegue> findByOfertaId(Long ofertaId);
    List<Despliegue> findByOferta_Lote_Emergencia_IdOrderById(Long emergenciaId);
    List<Despliegue> findByOferta_Ong_IdOrderByIdDesc(Long ongId);
}
