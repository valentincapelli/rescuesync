package com.grupo21.rescuesync.repository;

import com.grupo21.rescuesync.model.HistorialEstado;
import com.grupo21.rescuesync.model.TipoEntidadHistorial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistorialEstadoRepository extends JpaRepository<HistorialEstado, Long> {

    List<HistorialEstado> findByTipoEntidadAndEntidadIdOrderByFechaCambioAscIdAsc(
            TipoEntidadHistorial tipoEntidad, Long entidadId);

    List<HistorialEstado> findByEmergenciaIdOrderByFechaCambioAscIdAsc(Long emergenciaId);
}
