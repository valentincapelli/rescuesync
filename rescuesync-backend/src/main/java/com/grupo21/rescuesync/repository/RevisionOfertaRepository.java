package com.grupo21.rescuesync.repository;

import com.grupo21.rescuesync.model.RevisionOferta;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RevisionOfertaRepository extends JpaRepository<RevisionOferta, Long> {

    @EntityGraph(attributePaths = "autor")
    List<RevisionOferta> findByOfertaIdOrderByIdAsc(Long ofertaId);
}
