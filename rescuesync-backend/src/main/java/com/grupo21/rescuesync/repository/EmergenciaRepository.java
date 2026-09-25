package com.grupo21.rescuesync.repository;

import com.grupo21.rescuesync.model.Emergencia;
import com.grupo21.rescuesync.model.EstadoEmergencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmergenciaRepository extends JpaRepository<Emergencia, Long> {

    List<Emergencia> findByEstado(EstadoEmergencia estado);

    List<Emergencia> findByMunicipioIgnoreCase(String municipio);
}
