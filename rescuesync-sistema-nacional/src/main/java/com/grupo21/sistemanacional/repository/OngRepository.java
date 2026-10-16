package com.grupo21.sistemanacional.repository;

import com.grupo21.sistemanacional.model.EstadoHabilitacion;
import com.grupo21.sistemanacional.model.Ong;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OngRepository extends JpaRepository<Ong, Long> {

    Optional<Ong> findByCuit(String cuit);

    boolean existsByCuit(String cuit);

    List<Ong> findByEstadoHabilitacionOrderByRazonSocial(EstadoHabilitacion estado);
}
