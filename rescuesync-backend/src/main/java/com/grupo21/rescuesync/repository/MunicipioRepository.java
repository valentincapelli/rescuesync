package com.grupo21.rescuesync.repository;

import com.grupo21.rescuesync.model.Municipio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MunicipioRepository extends JpaRepository<Municipio, Long> {

    List<Municipio> findAllByOrderByNombreAsc();
}
