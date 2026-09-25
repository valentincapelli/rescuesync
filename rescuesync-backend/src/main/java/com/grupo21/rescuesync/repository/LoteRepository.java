package com.grupo21.rescuesync.repository;

import com.grupo21.rescuesync.model.EstadoLote;
import com.grupo21.rescuesync.model.Lote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoteRepository extends JpaRepository<Lote, Long> {

    List<Lote> findByEmergenciaId(Long emergenciaId);

    List<Lote> findByEmergenciaIdAndEstado(Long emergenciaId, EstadoLote estado);
}
