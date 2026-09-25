package com.grupo21.rescuesync.repository;

import com.grupo21.rescuesync.model.EstadoOferta;
import com.grupo21.rescuesync.model.Oferta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OfertaRepository extends JpaRepository<Oferta, Long> {

    List<Oferta> findByLoteId(Long loteId);

    List<Oferta> findByLoteIdAndEstado(Long loteId, EstadoOferta estado);

    List<Oferta> findByOngNombreIgnoreCase(String ongNombre);
}
